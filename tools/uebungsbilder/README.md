# Übungsbilder

In der Übungsansicht steht über dem Graphen ein Bild der Übung: eine graue Anatomie-Figur, die trainierten
Muskeln rot. Im hellen Design wird das Original auf Weiß gezeigt, im dunklen eine freigestellte, gedämpfte
Version auf der Kartenfarbe.

## Neues Bild hinzufügen

1. Bild mit Gemini (Nano Banana) erstellen, mit der Vorlage unten. Ab dem zweiten Bild ein bereits fertiges
   Bild als Stil-Vorlage anhängen, damit alle gleich aussehen.
2. Bild aufbereiten (braucht Python mit `pillow`, `numpy` und `scipy`):

   ```bash
   python3 tools/uebungsbilder/verarbeiten.py ~/Downloads/gemini_bild.jpg kniebeugen
   ```

   Das legt `exercise_kniebeugen.webp` in `drawable-nodpi` (hell) und `drawable-night-nodpi` (dunkel) an.
3. In `app/src/main/java/com/sheuberger/fitness/ui/exercises/ExerciseImages.kt` eine Zeile ergänzen:

   ```kotlin
   "kniebeugen" to R.drawable.exercise_kniebeugen,
   ```

   Der Name links ist der Übungsname in Kleinbuchstaben.

## Mehrere Übungen in einem Bild

Nano Banana Pro kann neun Übungen auf einmal erzeugen, als 3x3-Raster (Vorlage unten). Das spart Versuche,
die Figuren werden aber kleiner: Bei 2048 Pixeln ist jede etwa 680 Pixel groß, das reicht gerade, 4K ist besser.
Das Skript zerlegt so ein Sammelbild selbst:

```bash
python3 tools/uebungsbilder/verarbeiten.py --raster 3x3 sammelbild.jpg \
  bankdruecken butterfly brustpresse kabel_crossover bankdruecken_multipresse \
  schulterdruecken schulterpresse reverse_butterfly seitheben_maschine
```

Die Namen folgen der Leserichtung, Zeile für Zeile. Ein `-` überspringt ein misslungenes Feld; diese Übung
lieber einzeln neu erzeugen. Geschnitten wird nahe jeder Rasterlinie an der Stelle mit dem wenigsten Motiv,
was trotzdem vom Nachbarfeld hereinragt, fällt weg.

## Alle Bilder neu erzeugen

`quellen.txt` hält fest, welche Quelldatei zu welchem Bild wird. Nach einer Änderung am Skript (z. B. an der
Freistellung) entstehen so alle Bilder in einem Rutsch neu:

```bash
python3 tools/uebungsbilder/verarbeiten.py --liste tools/uebungsbilder/quellen.txt "<Ordner mit den Quellen>"
```

Kommt ein neues Bild dazu, auch dort eine Zeile ergänzen.

## Prompt-Vorlage

`{ÜBUNG}`, `{POSE}` und `{MUSKELN}` auf Englisch ersetzen, z. B. „a barbell back squat“, „standing with the
barbell on the upper back, side view“ und „quadriceps, glutes, hamstrings“.

```
Anatomical 3D fitness illustration of a male athlete performing {ÜBUNG}: {POSE}.
Style: clean medical-anatomy render, the body is light-to-mid grey (never pure
white, especially at the edges) with visible muscle definition, smooth soft
studio lighting, subtle shading, no skin texture, short dark hair, barefoot,
grey shorts.
Highlight ONLY these working muscles in red, semi-transparent with fine muscle
fibre detail: {MUSKELN}. All other muscles stay grey.
Equipment (barbell, plates, dumbbells, bench, machine, cable) is dark graphite
grey with visible highlights. Never white, never pure black.
Camera: three-quarter front view from slightly above, whole body and equipment
fully visible and centered, nothing touches the image border, generous margin.
Background: pure solid white (#FFFFFF) everywhere. No floor, no ground line,
no cast shadow, no reflection, no horizontal lines, no text, no logo,
no watermark, no labels.
Format: square 1:1, 1024x1024.
Match the style of the attached image exactly.
```

## Vorlage für ein Sammelbild

Für jedes Feld eine Zeile mit Übung, Haltung, Ansicht und roten Muskeln einsetzen, z. B. „Pec deck fly machine:
seated upright, bringing both arms together in front of the chest, three-quarter front view. Red: chest.“
Zwei fertige Bilder als Stil-Vorlage anhängen.

```
Create ONE square 1:1 image at the highest resolution available: a 3x3 grid of
nine separate anatomical 3D fitness illustrations, like a sprite sheet.
Match the style of the attached images exactly.

Layout:
- Three rows of three. Each illustration is centered in its own invisible square
  cell and fills about 80% of it.
- Wide empty white gaps between all illustrations. No figure or equipment crosses
  into a neighbouring cell, nothing touches the outer image border.
- Exactly one athlete per cell. No grid lines, frames, borders, numbers, titles,
  captions, labels or any other text.

Style of every illustration:
- The same male athlete as a clean medical-anatomy render: body light-to-mid grey
  (never pure white, especially at the edges), visible muscle definition, smooth
  soft studio lighting, subtle shading, no skin texture, short dark hair,
  barefoot, grey shorts.
- ONLY the listed working muscles are red, semi-transparent with fine muscle
  fibre detail. All other muscles stay grey.
- Equipment (barbell, plates, dumbbells, bench, machine, cable) is dark graphite
  grey with visible highlights. Never white, never pure black.
- Camera slightly above, whole body and all equipment fully visible.
- Background pure solid white (#FFFFFF) everywhere. No floor, no ground line,
  no cast shadow, no reflection.

Top row, left to right:
- {FELD 1}
- {FELD 2}
- {FELD 3}

Middle row, left to right:
- {FELD 4}
- {FELD 5}
- {FELD 6}

Bottom row, left to right:
- {FELD 7}
- {FELD 8}
- {FELD 9}
```

## Warum diese Regeln

- **Weißer Hintergrund statt transparent:** Gemini kann keine echte Transparenz und malt sonst oft ein
  Schachbrettmuster ins Bild. Weiß entfernt das Skript zuverlässig, auch in Lücken zwischen Geräteteilen.
- **Weiche Kanten:** Ein Randpixel ist eine Mischung aus Motiv und Weiß. Das Skript rechnet aus, wie viel Motiv
  darin steckt, und nimmt als Farbe die des Motivs dahinter. Sonst bliebe im dunklen Design ein heller Saum um die
  Figur, und dünne Kabel wären gepunktet.
- **Kein Schatten, keine Linien:** Sie würden nach dem Freistellen als helle Flecken übrig bleiben.
- **Geräte nie weiß:** Weiße Teile würden mit dem Hintergrund verschwinden.
- **Nichts berührt den Rand:** Sonst wird die Figur abgeschnitten.
- **Keine Linien und keine Schrift im Sammelbild:** Rasterlinien oder Beschriftungen würden als Teil des Motivs
  mit ausgeschnitten.
