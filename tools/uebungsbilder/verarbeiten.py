"""
Bereitet Übungsbilder (z. B. aus Gemini/Nano Banana) für die App auf.

Aufruf (aus dem Projektordner):
    python3 tools/uebungsbilder/verarbeiten.py <bild.jpg> <name> [<bild.jpg> <name> ...]
    python3 tools/uebungsbilder/verarbeiten.py --raster 3x3 <sammelbild.jpg> <name1> ... <name9>

Mit --raster wird ein Sammelbild mit mehreren Übungen (Spalten x Zeilen) zuerst in Einzelbilder zerlegt.
Die Namen folgen der Leserichtung (Zeile für Zeile, links nach rechts); "-" überspringt ein Feld.

Alle Bilder auf einmal neu erzeugen (Zuordnung in quellen.txt, Quelldateien im angegebenen Ordner):
    python3 tools/uebungsbilder/verarbeiten.py --liste tools/uebungsbilder/quellen.txt <ordner>

Für jedes Bild entstehen zwei Dateien mit gleichem Namen:
    app/src/main/res/drawable-nodpi/exercise_<name>.webp        hell: Original auf reinem Weiß
    app/src/main/res/drawable-night-nodpi/exercise_<name>.webp  dunkel: freigestellt, gedämpft, warm getönt
Android wählt im dunklen Modus automatisch die Datei aus dem "night"-Ordner.

Erwartet wird ein Motiv auf weißem Hintergrund, das den Bildrand nicht berührt (siehe README.md).
Benötigt Pillow, numpy und scipy (pip install pillow numpy scipy).
"""
import re
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
# Weiche Kante: so viele Pixel in den Hintergrund und ins Motiv hinein, so weit darf die Bezugsfarbe entfernt
# sein, und so viel Weißabstand (Summe der drei Kanäle) gilt noch als JPEG-Rauschen.
EDGE_IN, EDGE_OUT, EDGE_NEAR, EDGE_NOISE = 2, 3, 5, 12

# Dunkle Kachel. Der Rand hat genau die Kartenfarbe aus Color.kt (DarkCard), damit die Kachel nahtlos in die Karte
# übergeht. Nur zur Mitte hin wird sie heller, als läge die Figur in einem weichen Lichtkegel.
DARK_EDGE = (0x26, 0x26, 0x24)
DARK_GLOW = (0x3A, 0x38, 0x33)
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
    return Image.fromarray(soft_edge(rgb, background), "RGBA")


def soft_edge(rgb: np.ndarray, background: np.ndarray) -> np.ndarray:
    """Weiche Kante: Wie viel Motiv steckt in jedem Randpixel?

    Ein Randpixel ist eine Mischung aus Motivfarbe F und Weiß. Aus seiner Helligkeit im Vergleich zu F folgt die
    Deckkraft, die Farbe selbst wird durch F ersetzt. So bleibt auf dunklem Grund kein heller Saum und keine
    gepunktete Linie zurück, auch nicht von den hellen JPEG-Schlieren, die an dunklen Kanten entstehen.
    F ist die Farbe des nächsten Pixels im Inneren des Motivs, also mit etwas Abstand zum Hintergrund.
    """
    whiteness = (255 - rgb).sum(axis=2)  # Abstand zu Weiß über alle drei Kanäle
    foreground = ~background
    zone = (ndimage.binary_dilation(background, iterations=EDGE_IN)
            & ndimage.binary_dilation(foreground, iterations=EDGE_OUT))
    core = foreground & ~zone
    gap, (iy, ix) = ndimage.distance_transform_edt(~core, return_indices=True)
    fill = rgb[iy, ix]
    near = gap <= EDGE_NEAR
    # Dünne Linien (Kabel, Stangen) haben kein Inneres in der Nähe: Bezug ist ihr dunkelstes Pixel.
    reference = np.where(near, (255 - fill).sum(axis=2), ndimage.maximum_filter(whiteness, size=7))
    coverage = np.clip((whiteness - EDGE_NOISE) / np.maximum(reference - EDGE_NOISE, 30), 0, 1)
    alpha = np.where(core, 1.0, np.where(zone, coverage, np.where(background, 0.0, 1.0)))
    # Nahe am Inneren dessen Farbe nehmen (dunkler als F darf ein Pixel bleiben, z. B. eine Umrisslinie),
    # fern davon den weißen Anteil herausrechnen.
    a = np.maximum(alpha, 1e-3)[..., None]
    unmixed = np.clip((rgb - 255 * (1 - a)) / a, 0, 255)
    color = np.where((near & zone & (coverage < 1))[..., None], fill, np.where(near[..., None], rgb, unmixed))
    color = np.where(core[..., None], rgb, color)
    return np.dstack([color, alpha * 255]).astype(np.uint8)


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
    """Hintergrund der dunklen Kachel: Kartenfarbe, zur Mitte hin ein weicher Lichtschein, am Rand exakt Kartenfarbe."""
    size = (WIDTH, HEIGHT)
    glow = Image.new("L", size, 0)
    ImageDraw.Draw(glow).ellipse([WIDTH * 0.2, HEIGHT * 0.1, WIDTH * 0.8, HEIGHT * 0.9], fill=255)
    glow = np.asarray(glow.filter(ImageFilter.GaussianBlur(WIDTH * 0.07))).astype(np.float32)
    # Zum Rand hin auf null ausblenden, sonst ist die Kachel dort heller als die Karte und hat eine sichtbare Kante.
    ys, xs = np.mgrid[0:HEIGHT, 0:WIDTH]
    border = np.minimum(np.minimum(xs, WIDTH - 1 - xs), np.minimum(ys, HEIGHT - 1 - ys))
    glow *= np.clip(border / (HEIGHT * 0.12), 0, 1) ** 2
    tile = Image.new("RGB", size, DARK_EDGE)
    tile.paste(Image.new("RGB", size, DARK_GLOW), (0, 0), Image.fromarray(glow.astype(np.uint8)))
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


def split_sheet(sheet: Image.Image, columns: int, rows: int) -> list[Image.Image]:
    """Zerlegt ein Sammelbild in Einzelbilder. Geschnitten wird nahe den Rasterlinien dort, wo am wenigsten Motiv ist."""
    rgb = np.asarray(sheet.convert("RGB")).astype(np.int16)
    content = (255 - rgb.min(axis=2)) > TOLERANCE

    def cut_positions(profile: np.ndarray, parts: int) -> list[int]:
        length = len(profile)
        positions = [0]
        for k in range(1, parts):
            center = round(length * k / parts)
            radius = round(length / parts * 0.25)
            window = profile[center - radius:center + radius]
            # Unter den leersten Stellen die nächste zur eigentlichen Rasterlinie nehmen.
            candidates = np.flatnonzero(window == window.min()) + center - radius
            positions.append(int(candidates[np.argmin(np.abs(candidates - center))]))
        positions.append(length)
        return positions

    cells = []
    ys = cut_positions(content.sum(axis=1), rows)
    for top, bottom in zip(ys, ys[1:]):
        xs = cut_positions(content[top:bottom].sum(axis=0), columns)
        for left, right in zip(xs, xs[1:]):
            cells.append(sheet.crop((left, top, right, bottom)))
    return cells


def drop_border_pieces(figure: Image.Image) -> Image.Image:
    """Entfernt Teile, die den Rand berühren (z. B. eine Hantel aus dem Nachbarfeld). Das größte Teil bleibt immer."""
    alpha = np.asarray(figure.getchannel("A"))
    pieces, count = ndimage.label(alpha > 0)
    if count <= 1:
        return figure
    sizes = ndimage.sum(np.ones_like(pieces), pieces, range(1, count + 1))
    largest = int(np.argmax(sizes)) + 1
    border = set(np.unique(np.concatenate([pieces[0], pieces[-1], pieces[:, 0], pieces[:, -1]]))) - {0, largest}
    if not border:
        return figure
    cleaned = np.where(np.isin(pieces, list(border)), 0, alpha).astype(np.uint8)
    figure = figure.copy()
    figure.putalpha(Image.fromarray(cleaned))
    return figure


def process(image: Image.Image, name: str) -> None:
    figure, box = place(drop_border_pieces(cut_out(image)))

    light = Image.new("RGBA", (WIDTH, HEIGHT), (255, 255, 255, 255))
    light.alpha_composite(figure)

    dark = dark_tile()
    dark.alpha_composite(contact_shadow(box))
    dark.alpha_composite(night_tones(figure))

    for folder, result in (("drawable-nodpi", light), ("drawable-night-nodpi", dark)):
        target = RES / folder / f"exercise_{name}.webp"
        target.parent.mkdir(parents=True, exist_ok=True)
        result.convert("RGB").save(target, "WEBP", quality=82, method=6)
        print(f"{target.relative_to(ROOT)}  ({target.stat().st_size // 1024} KB)")


def run(args: list[str], folder: Path = Path(".")) -> None:
    if len(args) >= 3 and args[0] == "--raster":
        columns, rows = (int(n) for n in args[1].lower().split("x"))
        names = args[3:]
        if len(names) != columns * rows:
            sys.exit(f"Für ein {columns}x{rows}-Raster braucht es {columns * rows} Namen ('-' überspringt ein Feld).")
        for cell, name in zip(split_sheet(Image.open(folder / args[2]), columns, rows), names):
            if name != "-":
                process(cell, name)
    elif args and len(args) % 2 == 0:
        for source, name in zip(args[::2], args[1::2]):
            process(Image.open(folder / source), name)
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    args = sys.argv[1:]
    if len(args) == 3 and args[0] == "--liste":
        for line in Path(args[1]).read_text(encoding="utf-8").splitlines():
            if line.strip() and not line.startswith("#"):
                source, *rest = line.split()
                run(["--raster", rest[0], source, *rest[1:]] if re.fullmatch(r"\d+x\d+", rest[0]) else [source, *rest],
                    Path(args[2]))
    else:
        run(args)
