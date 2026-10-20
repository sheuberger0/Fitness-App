"""
Bereitet Übungsbilder (z. B. aus Gemini/Nano Banana) für die App auf.

Aufruf (aus dem Projektordner):
    python3 tools/uebungsbilder/verarbeiten.py <bild.jpg> <name> [<bild.jpg> <name> ...]

Für jedes Bild entstehen zwei Dateien mit gleichem Namen:
    app/src/main/res/drawable-nodpi/exercise_<name>.webp        hell: Original auf reinem Weiß
    app/src/main/res/drawable-night-nodpi/exercise_<name>.webp  dunkel: freigestellt, gedämpft, warm getönt
Android wählt im dunklen Modus automatisch die Datei aus dem "night"-Ordner.

Erwartet wird ein Motiv auf weißem Hintergrund, das den Bildrand nicht berührt (siehe README.md).
Benötigt Pillow, numpy und scipy (pip install pillow numpy scipy).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

# Größe der fertigen Bilder: 4:3 wie die Bild-Kachel in der Übungsansicht.
WIDTH, HEIGHT = 1000, 750
# So viel von Breite/Höhe darf die Figur höchstens einnehmen. Unten bleibt etwas mehr Platz für den Schatten.
MAX_W, MAX_H = 0.86, 0.80
CENTER_Y = 0.47
# Pixel, die höchstens so weit von Weiß entfernt sind, zählen als Hintergrund (0 = reines Weiß).
TOLERANCE = 50
# Eingeschlossene Lücken: so weiß (Weißabstand), so groß (Anteil am Bild) und mit so dunklem Rand (Mittelwert).
HOLE_WHITE, HOLE_MIN_AREA, HOLE_EDGE = 10, 0.00001, 80

# Dunkle Kachel. Der Rand hat genau die Kartenfarbe aus Color.kt (DarkCard), damit die Kachel wie eine Karte wirkt.
DARK_EDGE = (0x26, 0x26, 0x24)
DARK_TOP = (0x2D, 0x2B, 0x28)
DARK_GLOW = (0x3D, 0x3B, 0x36)
# Tonwerte der Figur im dunklen Modus: Schwarz bleibt fast schwarz, Weiß wird gedämpft, damit nichts blendet.
NIGHT_LO, NIGHT_HI = 10, 210
# Warme Tönung für Grautöne, passend zur warmen Farbpalette der App. Rot (die Muskeln) bleibt unverändert.
NIGHT_WARM = (1.0, 0.975, 0.93)

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app" / "src" / "main" / "res"


def cut_out(image: Image.Image) -> Image.Image:
    """Stellt das Motiv frei: Weiß außen herum und weiße Lücken innerhalb der Geräte werden transparent."""
    rgb = np.asarray(image.convert("RGB")).astype(np.float32)
    distance = 255 - rgb.min(axis=2)  # wie weit ein Pixel von Weiß entfernt ist
    # Zusammenhängende helle Flächen finden. Alles, was den Bildrand berührt, ist Hintergrund.
    light, _ = ndimage.label(distance <= TOLERANCE)
    border = np.unique(np.concatenate([light[0], light[-1], light[:, 0], light[:, -1]]))
    background = np.isin(light, border[border > 0])
    # Eingeschlossene Lücken (z. B. im Rahmen einer Maschine oder zwischen Arm und Körper): fast reines Weiß,
    # umgeben von dunklen Kanten. Helle Stellen auf dem Körper sind dagegen von hellem Grau umgeben und bleiben.
    pure, count = ndimage.label(distance <= HOLE_WHITE)
    min_area = HOLE_MIN_AREA * distance.size
    holes = set()
    for index, area in enumerate(ndimage.sum(np.ones_like(pure), pure, range(1, count + 1)), start=1):
        if area < min_area:
            continue
        component = pure == index
        if background[component].any():
            continue
        ring = ndimage.binary_dilation(component, iterations=4) & ~component
        if distance[ring].mean() >= HOLE_EDGE:
            holes.update(np.unique(light[component]))
    holes.discard(0)
    if holes:
        background |= np.isin(light, list(holes))
    # Kante: halbtransparent je nach Weißabstand, damit kein harter, gezackter Rand entsteht.
    edge = ndimage.binary_dilation(background) & ~background
    alpha = np.where(background, 0.0, 255.0)
    alpha[edge] = np.clip(distance[edge] / 60 * 255, 0, 255)
    # Den weißen Anteil aus den Kantenpixeln herausrechnen, sonst bleibt ein heller Saum.
    a = np.maximum(alpha / 255, 1e-3)[..., None]
    rgb = np.clip((rgb - 255 * (1 - a)) / a, 0, 255)
    return Image.fromarray(np.dstack([rgb, alpha]).astype(np.uint8), "RGBA")


def subject_box(figure: Image.Image) -> tuple[int, int, int, int]:
    """Umriss des Motivs. Einzelne Störpixel (z. B. JPEG-Rauschen) werden vorher herausgefiltert."""
    alpha = figure.getchannel("A").point(lambda v: 255 if v > 128 else 0)
    alpha = alpha.filter(ImageFilter.MinFilter(5)).filter(ImageFilter.MaxFilter(5))
    return alpha.getbbox()


def place(figure: Image.Image) -> tuple[Image.Image, tuple[int, int, int, int]]:
    """Schneidet auf das Motiv zu und setzt es mittig in eine leere 4:3-Fläche. Alle Bilder wirken so gleich groß."""
    figure = figure.crop(subject_box(figure))
    scale = min(WIDTH * MAX_W / figure.width, HEIGHT * MAX_H / figure.height)
    figure = figure.resize((round(figure.width * scale), round(figure.height * scale)), Image.LANCZOS)
    x = (WIDTH - figure.width) // 2
    y = round(HEIGHT * CENTER_Y - figure.height / 2)
    canvas = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    canvas.paste(figure, (x, y), figure)
    return canvas, (x, y, x + figure.width, y + figure.height)


def night_tones(figure: Image.Image) -> Image.Image:
    """Dunkel-Variante der Figur: Tonwerte stauchen und Grautöne warm tönen."""
    data = np.asarray(figure).astype(np.float32)
    rgb, alpha = data[..., :3], data[..., 3:]
    rgb = NIGHT_LO + rgb * (NIGHT_HI - NIGHT_LO) / 255
    saturation = (rgb.max(axis=2) - rgb.min(axis=2)) / np.maximum(rgb.max(axis=2), 1)
    neutral = np.clip(1 - saturation * 3, 0, 1)[..., None]
    rgb = rgb * (1 - neutral) + rgb * np.array(NIGHT_WARM) * neutral
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), alpha]).astype(np.uint8), "RGBA")


def dark_tile() -> Image.Image:
    """Hintergrund der dunklen Kachel: Kartenfarbe, oben minimal heller, dazu ein weicher Lichtschein in der Mitte."""
    size = (WIDTH, HEIGHT)
    tile = Image.new("RGB", size, DARK_EDGE)
    top = Image.linear_gradient("L").resize(size).point(lambda v: 255 - v)
    tile.paste(Image.new("RGB", size, DARK_TOP), (0, 0), top)
    glow = Image.new("L", size, 0)
    ImageDraw.Draw(glow).ellipse([WIDTH * 0.18, HEIGHT * 0.02, WIDTH * 0.82, HEIGHT * 0.98], fill=255)
    tile.paste(Image.new("RGB", size, DARK_GLOW), (0, 0), glow.filter(ImageFilter.GaussianBlur(WIDTH * 0.07)))
    return tile.convert("RGBA")


def contact_shadow(box: tuple[int, int, int, int]) -> Image.Image:
    """Weicher Schatten unter dem Motiv, damit es steht statt zu schweben."""
    x0, _, x1, y1 = box
    center, half = (x0 + x1) / 2, (x1 - x0) * 0.42
    mask = Image.new("L", (WIDTH, HEIGHT), 0)
    ImageDraw.Draw(mask).ellipse([center - half, y1 - 14, center + half, y1 + 8], fill=140)
    shadow = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    shadow.putalpha(mask.filter(ImageFilter.GaussianBlur(16)))
    return shadow


def process(source: Path, name: str) -> None:
    figure, box = place(cut_out(Image.open(source)))

    light = Image.new("RGBA", (WIDTH, HEIGHT), (255, 255, 255, 255))
    light.alpha_composite(figure)

    dark = dark_tile()
    dark.alpha_composite(contact_shadow(box))
    dark.alpha_composite(night_tones(figure))

    for folder, image in (("drawable-nodpi", light), ("drawable-night-nodpi", dark)):
        target = RES / folder / f"exercise_{name}.webp"
        target.parent.mkdir(parents=True, exist_ok=True)
        image.convert("RGB").save(target, "WEBP", quality=82, method=6)
        print(f"{target.relative_to(ROOT)}  ({target.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args or len(args) % 2:
        sys.exit(__doc__)
    for source, name in zip(args[::2], args[1::2]):
        process(Path(source), name)
