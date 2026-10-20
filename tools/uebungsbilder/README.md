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

## Warum diese Regeln

- **Weißer Hintergrund statt transparent:** Gemini kann keine echte Transparenz und malt sonst oft ein
  Schachbrettmuster ins Bild. Weiß entfernt das Skript zuverlässig, auch in Lücken zwischen Geräteteilen.
- **Kein Schatten, keine Linien:** Sie würden nach dem Freistellen als helle Flecken übrig bleiben.
- **Geräte nie weiß:** Weiße Teile würden mit dem Hintergrund verschwinden.
- **Nichts berührt den Rand:** Sonst wird die Figur abgeschnitten.
