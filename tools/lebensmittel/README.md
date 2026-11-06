# Nährwert-Tabellen

Wenn du im Sheet "Eintragen" unter "Lebensmittel" tippst, schlägt die App passende Lebensmittel mit ihren
Nährwerten vor, auch ohne Internet. Die Werte stammen aus zwei Dateien in `app/src/main/assets/naehrwerte/`,
die dieses Skript erzeugt:

| Datei | Quelle | Inhalt |
| --- | --- | --- |
| `blv.tsv` | [Schweizer Nährwertdatenbank](https://naehrwertdaten.ch/de/downloads/) des BLV | rund 1250 allgemeine Lebensmittel mit deutschen Namen, z. B. "Hühnerei, ganz, roh" |
| `openfoodfacts_ch.tsv.gzip` | [Open Food Facts](https://world.openfoodfacts.org/data) | rund 82'000 Produkte, die in der Schweiz verkauft werden (Migros, Coop, Denner, Aldi, Lidl, Spar …), mit Barcode |

Alle Werte gelten pro 100 g. Die Portionsgrößen (ein Ei 55 g, eine Scheibe Brot 40 g) sind Schätzungen.

## Lizenzen

- **Schweizer Nährwertdatenbank**: kostenlos nutzbar, auch in Ernährungstagebuch-Apps, mit Quellenangabe.
  Die App zeigt sie unter den Treffern und beim Eintragen: "Daten: Schweizer Nährwertdatenbank, BLV".
- **Open Food Facts**: [Open Database License (ODbL) 1.0](https://opendatacommons.org/licenses/odbl/1-0/),
  einzelne Inhalte unter der Database Contents License. `openfoodfacts_ch.tsv.gzip` ist daraus abgeleitet und
  steht deshalb ebenfalls unter der ODbL. Die App nennt die Quelle: "Daten: Open Food Facts, ODbL".

Die Websites von Migros, Coop & Co. liest das Skript bewusst nicht aus: Ihre Nutzungsbedingungen verbieten das,
und die Daten dürften nicht in dieses öffentliche Repository.

## Neu erzeugen

Braucht Python 3 mit `openpyxl`.

1. Die Excel-Datei der Nährwertdatenbank herunterladen, unter "Downloads" auf naehrwertdaten.ch
   (zuletzt `Schweizer_Nahrwertdatenbank.xlsx`, Version 7.1 vom Juli 2026).
2. Den kompletten Export von Open Food Facts herunterladen (etwa 1,5 GB):
   `https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz`
3. Beides umwandeln:

   ```bash
   python3 tools/lebensmittel/naehrwerte.py \
       --blv ~/Downloads/Schweizer_Nahrwertdatenbank.xlsx \
       --off ~/Downloads/en.openfoodfacts.org.products.csv.gz
   ```

   Das Skript liest den Export Zeile für Zeile und behält nur Produkte mit Verkaufsland Schweiz. Es sortiert
   Unsinn aus (z. B. kJ ins kcal-Feld getippt), fasst doppelte Produkte zusammen und stellt Schweizer und oft
   gescannte Produkte nach vorne. Beide Teile gehen auch einzeln, nur `--blv` dauert wenige Sekunden.

Tipp zum Ausprobieren: Den Export einmal auf die Schweizer Zeilen verkleinern, dann geht jeder weitere Lauf
schnell. `grep -a` ist nötig, weil im Export ein paar kaputte Bytes stehen; ohne `-a` hält grep die Datei für
binär und gibt fast nichts aus.

```bash
zcat en.openfoodfacts.org.products.csv.gz | head -1 > schweiz.tsv
zcat en.openfoodfacts.org.products.csv.gz | grep -a 'en:switzerland' >> schweiz.tsv
python3 tools/lebensmittel/naehrwerte.py --off schweiz.tsv
```

Danach `./gradlew testDebugUnitTest` laufen lassen: `FoodCatalogTest` prüft die echten Dateien (Werte
plausibel, "ei" findet zuerst das Hühnerei, eine Suche dauert unter 200 ms), `FoodCatalogAssetsTest` prüft sie
so, wie die App sie nach dem Bauen bekommt.

## Suchwörter, Portionen und Reihenfolge

`CURATED` am Anfang von `naehrwerte.py` ergänzt häufige Lebensmittel um Suchwörter ("Nutella" findet den
Haselnuss-Brotaufstrich) und eine Portion in Gramm. Die Reihenfolge dort zählt: Passen zwei Treffer gleich gut,
steht der weiter vorne aus `CURATED` zuerst ("ei": rohes Ei, gekochtes Ei, dann Rührei). Danach kommen alle
anderen, kurze Namen zuerst.

Wie gut ein Treffer passt, entscheidet `SearchText.score` in
`app/src/main/java/com/sheuberger/fitness/nutrition/FoodCatalog.kt`: ganzes Wort vor Wortanfang vor
irgendwo im Wort, Umlaute und Groß- und Kleinschreibung egal ("muesli" findet "Müesli").

## Warum `.gzip` und nicht `.gz`

Das Android-Gradle-Plugin packt Dateien mit der Endung `.gz` in `assets/` beim Bauen aus und entfernt die
Endung. In der App hieß die Produktliste dann `openfoodfacts_ch.tsv`, die App suchte aber
`openfoodfacts_ch.tsv.gz` und zeigte keine Produkte. Mit `.gzip` bleibt die Datei, wie sie ist.
