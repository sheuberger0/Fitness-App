#!/usr/bin/env python3
"""
Baut die Nährwert-Dateien, mit denen die App beim Tippen Lebensmittel vorschlägt.

Zwei offene Quellen, beide dürfen in einer App verwendet werden:

1. Schweizer Nährwertdatenbank des BLV (Excel-Datei von https://naehrwertdaten.ch/de/downloads/)
   -> app/src/main/assets/naehrwerte/blv.tsv
   Rund 1250 allgemeine Lebensmittel mit deutschen Namen ("Hühnerei, ganz, roh", "Ruchbrot").
   Laut BLV kostenlos, auch für Ernährungstagebuch-Apps, mit Quellenangabe.

2. Open Food Facts (kompletter Export, https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz)
   -> app/src/main/assets/naehrwerte/openfoodfacts_ch.tsv.gzip (gzip, aber nicht ".gz": Android würde die Datei
      beim Bauen auspacken und umbenennen, siehe FoodCatalog.OFF_FILE)
   Alle Produkte, die in der Schweiz verkauft werden: Migros, Coop, Denner, Aldi, Lidl, Spar und mehr.
   Lizenz: Open Database License (ODbL) 1.0. Die erzeugte Datei steht unter derselben Lizenz.

Die Seiten von Migros, Coop & Co. selbst liest das Skript bewusst nicht aus: Ihre Nutzungsbedingungen erlauben
das nicht, und die Daten dürften nicht in ein öffentliches Repository.

Aufruf (Python 3 mit openpyxl; der Open-Food-Facts-Export ist 1,5 GB groß und wird gestreamt):

    python3 tools/lebensmittel/naehrwerte.py \\
        --blv ~/Downloads/Schweizer_Nahrwertdatenbank.xlsx \\
        --off ~/Downloads/en.openfoodfacts.org.products.csv.gz
"""

import argparse
import gzip
import io
import re
import sys
import unicodedata
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "naehrwerte"

# Die gleichen Grenzen wie in OpenFoodFacts.kt: Was außerhalb liegt, ist ein Tippfehler in den Daten.
KCAL_MAX = 900.0          # reines Öl hat knapp 900 kcal pro 100 g
GRAMS_MAX = 100.0         # mehr als 100 g Protein in 100 g gibt es nicht
PORTION_RANGE = (1.0, 2000.0)
PACKAGE_RANGE = (1.0, 20000.0)
NAME_MAX = 80
BRAND_MAX = 40
KJ_PER_KCAL = 4.184

# --- Eigene Ergänzungen zur BLV-Tabelle ---
#
# Suchwörter, die im offiziellen Namen fehlen ("Ei" soll "Hühnerei" finden, "Nutella" den Brotaufstrich),
# und eine übliche Portion in Gramm (essbarer Anteil). Die Portionen sind Richtwerte: In der App lässt sich
# jede Menge auch in Gramm eintragen, und "1 Portion = 55 g" steht dort sichtbar dabei.
# Schlüssel ist die BLV-ID (erste Spalte der Excel-Datei).
CURATED = {
    # Eier: 1 Ei Größe M ohne Schale wiegt etwa 55 g.
    "290": (["Ei", "Eier", "Frühstücksei"], 55),
    "1070": (["Ei", "Eier", "gekochtes Ei", "hartes Ei"], 55),
    "1602": (["Ei", "Eier"], 120),  # Rührei: nach dem rohen und dem gekochten Ei, siehe blv_order
    "410": (["Eigelb", "Eidotter"], 18),
    "411": (["Eiweiss", "Eiklar"], 33),
    # Brot und Aufstriche: eine Scheibe Brot etwa 40 g, Toast 25 g, ein Gipfeli 45 g.
    "10446": (["Brot", "Scheibe Brot"], 40),
    "823": (["Weissbrot"], 40),
    "833": ([], 40),
    "829": ([], 40),
    "825": (["Toast"], 25),
    "826": (["Toast"], 25),
    "827": (["Toast"], 25),
    "14109": (["Toast"], 25),
    "820": (["Zopf"], 40),
    "10406": (["Croissant"], 45),
    "836": (["Croissant"], 45),
    "703": (["Nutella", "Schokoaufstrich", "Nussnougatcreme", "Nuss-Nougat-Creme"], 15),
    "751": (["Peanut Butter"], 15),
    "49": (["Butter"], 10),
    "14297": ([], 10),
    "696": (["Marmelade", "Konfi"], 20),
    "472": (["Honig"], 20),
    # Frühstück
    "198": ([], 50),
    "942": (["Müsli", "Muesli"], 60),
    "943": (["Müsli", "Muesli"], 60),
    "1198": (["Bircher"], 200),
    "940": ([], 40),
    # Milchprodukte: Joghurtbecher in der Schweiz meist 180 g, 1 Glas Milch 2 dl.
    "1194": (["Milch"], 200),
    "62": (["Milch"], 200),
    "14126": (["Milch"], 200),
    "64": (["Milch"], 200),
    "52": ([], 180),
    "587": ([], 180),
    "53": ([], 180),
    "54": ([], 180),
    "55": ([], 180),
    "56": ([], 180),
    "57": ([], 180),
    "1192": ([], 180),
    "71": (["Magerquark"], 125),
    "571": ([], 125),
    "505": (["Cottage Cheese"], 100),
    "555": (["Käse"], 30),
    "530": (["Käse", "Gruyère"], 30),
    "82": ([], 60),
    "72": (["Sojamilch", "Sojadrink"], 200),
    # Früchte: ein mittelgroßes Stück ohne Schale.
    "378": ([], 150),
    "381": ([], 120),
    "405": ([], 150),
    "382": ([], 150),
    "395": ([], 75),
    "380": ([], 150),
    # Fleisch, Fisch, Tofu: eine Portion etwa 125 bis 150 g.
    "22": (["Pouletbrust", "Hähnchenbrust", "Hühnerbrust", "Chicken"], 150),
    "13276": (["Pouletbrust", "Hähnchenbrust", "Hühnerbrust", "Chicken"], 150),
    "6": (["Hackfleisch", "Hack"], 125),
    "14237": (["Hackfleisch", "Hack"], 125),
    "13297": (["Hackfleisch", "Hack"], 125),
    "193": (["Räucherlachs"], 50),
    "194": ([], 125),
    "756": (["Thon"], 100),
    "13437": ([], 125),
    "1081": (["Cervelat"], 100),
    "713": (["Trutenbrust"], 150),
    # Beilagen gekocht: Reis etwa 180 g, Teigwaren etwa 250 g, Kartoffeln 200 g.
    "1066": (["Reis"], 180),
    "1065": (["Reis"], 180),
    "1067": (["Reis", "Vollkornreis"], 180),
    "1063": (["Pasta", "Nudeln", "Spaghetti", "Penne", "Hörnli"], 250),
    "1062": (["Pasta", "Nudeln", "Eiernudeln"], 250),
    "1064": (["Pasta", "Nudeln", "Vollkornpasta"], 250),
    "800": (["Pasta", "Nudeln", "Spaghetti", "Penne", "Hörnli"], 100),
    "1057": (["Kartoffeln"], 200),
    "14259": (["Kartoffeln"], 200),
    "13396": ([], 180),
    "1059": ([], 150),
    # Snacks und Süßes
    "195": (["Schokolade", "Schoggi"], 25),
    "196": (["Schokolade", "Schoggi", "Zartbitter"], 25),
    "273": ([], 25),
    "271": (["Walnuss"], 25),
    "275": ([], 25),
    "282": ([], 25),
    "12982": (["Chips"], 30),
    # Getränke
    "994": (["Kaffee"], 200),
    "1187": (["Milchkaffee", "Café crème"], 200),
    "498": (["Cola"], 330),
    "816": (["Bier"], 330),
    "576": ([], 200),
    "568": ([], 200),
    # Gerichte
    "1535": (["Pizza"], 350),
    "1478": ([], 350),
    "14055": ([], 30),
}

# Position jeder ID in CURATED (Python-Dicts behalten die Reihenfolge, in der sie geschrieben sind).
CURATED_ORDER = {blv_id: position for position, blv_id in enumerate(CURATED)}


# --- Hilfsfunktionen ---

def clean_text(raw, max_length):
    """Wie cleanText in OpenFoodFacts.kt: Leerzeichen zusammenfassen, Steuer- und Formatzeichen weg, kürzen."""
    if raw is None:
        return None
    parts = []
    for ch in str(raw):
        if ch.isspace():
            parts.append(" ")
        elif unicodedata.category(ch) in ("Cc", "Cf"):
            continue
        else:
            parts.append(ch)
    text = re.sub(r" +", " ", "".join(parts)).strip()
    return text[:max_length].rstrip() or None


def fold(text):
    """Für Duplikate: klein, ohne Akzente, nur Buchstaben und Ziffern."""
    text = unicodedata.normalize("NFKD", text.lower().replace("ß", "ss"))
    text = "".join(c for c in text if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", " ", text).strip()


def fmt(value):
    """Zahl mit höchstens einer Nachkommastelle, ohne ".0". Fehlend = leeres Feld."""
    if value is None:
        return ""
    value = round(value, 1)
    return str(int(value)) if value == int(value) else f"{value:.1f}"


def check_digit_valid(code):
    """Wie Barcode.checkDigitValid in der App: Gewichte 3 und 1 von rechts."""
    digits = [ord(c) - 48 for c in code]
    total = sum(d * 3 if i % 2 == 0 else d for i, d in enumerate(reversed(digits[:-1])))
    return (10 - total % 10) % 10 == digits[-1]


def valid_code(code):
    """Nur Codes, die die App auch scannen kann: EAN-8, UPC-A, EAN-13. Ladencodes (2…) gelten nur im Laden."""
    if not code.isdigit() or len(code) not in (8, 12, 13) or not check_digit_valid(code):
        return False
    return not (len(code) == 13 and code[0] == "2")


# --- 1. Schweizer Nährwertdatenbank ---

def blv_number(value):
    """Zahl aus der BLV-Tabelle. "Sp." (Spuren) und "<0.5" zählen als 0, "k.A." (keine Angabe) als fehlend."""
    if value is None:
        return None
    text = str(value).strip().replace(",", ".")
    if text == "Sp." or text.startswith("<"):
        return 0.0
    try:
        number = float(text)
    except ValueError:
        return None
    return number if number >= 0 else None


def read_blv(path):
    import openpyxl  # nur hier nötig

    workbook = openpyxl.load_workbook(path, read_only=True, data_only=True)
    version = None
    items = []
    for sheet in ("Generische Lebensmittel", "Markenprodukte"):
        rows = workbook[sheet].iter_rows(values_only=True)
        title = str(next(rows)[0] or "")
        found = re.search(r"V\s*([\d.]+)\s*\(([\d.]+)\)", title)
        if found:
            version = f"V {found.group(1)} ({found.group(2)})"
        next(rows)  # leere Zeile
        header = [str(h or "").strip() for h in next(rows)]
        col = {key: header.index(key) for key in (
            "ID", "Name", "Synonyme", "Kategorie", "Energie, Kilokalorien (kcal)", "Protein (g)",
            "Kohlenhydrate, verfügbar (g)", "Fett, total (g)",
        )}
        for row in rows:
            if row[col["ID"]] is None:
                continue
            blv_id = str(row[col["ID"]]).strip()
            name = clean_text(row[col["Name"]], NAME_MAX)
            kcal = blv_number(row[col["Energie, Kilokalorien (kcal)"]])
            if not name or kcal is None or kcal > KCAL_MAX:
                continue
            synonyms = [clean_text(s, NAME_MAX) for s in str(row[col["Synonyme"]] or "").split(";")]
            aliases, portion = CURATED.get(blv_id, ([], None))
            items.append({
                "id": blv_id,
                "name": name,
                "aliases": [a for a in synonyms + aliases if a],
                "category": clean_text(row[col["Kategorie"]], 200) or "",
                "kcal": kcal,
                "protein": blv_number(row[col["Protein (g)"]]),
                "carbs": blv_number(row[col["Kohlenhydrate, verfügbar (g)"]]),
                "fat": blv_number(row[col["Fett, total (g)"]]),
                "portion": portion,
            })
    known = {item["id"] for item in items}
    for missing in sorted(set(CURATED) - known):
        print(f"Warnung: BLV-ID {missing} aus CURATED gibt es nicht (mehr).", file=sys.stderr)
    return version, items


def blv_order(item):
    """
    Die Reihenfolge in der Datei ist der Rang in der App, wenn zwei Treffer gleich gut passen. Zuerst die häufigen
    Lebensmittel aus CURATED, in der Reihenfolge dort ("ei": rohes Ei, gekochtes Ei, dann Rührei), danach alle
    anderen mit kurzen Namen zuerst: "Brot (Durchschnitt)" ist allgemeiner als "Brot, Vollkorn, mit Kernen".
    """
    position = CURATED_ORDER.get(item["id"])
    if position is not None:
        return (0, position, 0, "")
    return (1, 0, len(item["name"]), item["name"])


def write_blv(version, items, out):
    items = sorted(items, key=blv_order)
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(f"# Quelle: Schweizer Nährwertdatenbank {version}, Bundesamt für Lebensmittelsicherheit und "
                "Veterinärwesen BLV, naehrwertdaten.ch\n")
        f.write("# Suchwörter und Portionen ergänzt in tools/lebensmittel/naehrwerte.py. Werte pro 100 g.\n")
        f.write("# id\tname\tsuchwörter\tkategorie\tkcal\tprotein\tkohlenhydrate\tfett\tportion_g\n")
        for item in items:
            f.write("\t".join([
                item["id"], item["name"], "|".join(item["aliases"]), item["category"],
                fmt(item["kcal"]), fmt(item["protein"]), fmt(item["carbs"]), fmt(item["fat"]), fmt(item["portion"]),
            ]) + "\n")
    print(f"{out}: {len(items)} Lebensmittel")


# --- 2. Open Food Facts ---

# Eigenmarken der Läden: So findet "migros joghurt" auch Produkte von "M-Classic", wenn der Laden fehlt.
SHOPS = {
    "Migros": ["migros", "m-classic", "m classic", "m-budget", "m budget", "anna's best", "annas best",
               "sélection", "selection", "léger", "aproz", "elsa", "micarna", "jowa", "chocolat frey", "frey",
               "heidi", "v-love", "delizio", "bischofszell"],
    "Coop": ["coop", "naturaplan", "prix garantie", "qualité & prix", "qualite & prix", "qualité&prix",
             "fine food", "karma", "betty bossi", "halba", "sapori d'italia", "plant hero", "free from"],
    "Denner": ["denner"],
    "Aldi": ["aldi", "milsani", "lyttos", "choceur", "moser roth", "cucina nobile", "golden bridge"],
    "Lidl": ["lidl", "milbona", "pilos", "favorina", "fin carré", "crownfield", "sondey", "vitasia",
             "freeway", "solevita", "snack day", "italiamo", "chef select", "gelatelli"],
    "Spar": ["spar"],
    "Volg": ["volg"],
    "Manor": ["manor"],
    "Globus": ["globus"],
    "Landi": ["landi"],
}


def shops_of(stores, brands):
    stores = stores.lower()
    brand_list = [b.strip().lower() for b in brands.split(",") if b.strip()]
    found = []
    for shop, keys in SHOPS.items():
        if shop.lower() in stores or any(b == k or b.startswith(k + " ") for b in brand_list for k in keys):
            found.append(shop)
    return found


def off_number(text):
    try:
        value = float(text)
    except ValueError:
        return None
    return value if value == value and abs(value) != float("inf") else None


def in_range(value, low, high):
    return value if value is not None and low <= value <= high else None


def read_off(path):
    """Liest den Export Zeile für Zeile. Nur Zeilen mit "en:switzerland" werden überhaupt zerlegt."""
    raw = gzip.open(path, "rb") if str(path).endswith(".gz") else open(path, "rb")
    with raw:
        header = raw.readline().rstrip(b"\r\n").decode("utf-8").split("\t")
        col = {name: i for i, name in enumerate(header)}
        need = ["code", "product_name", "brands", "stores", "countries_tags", "categories_tags",
                "serving_quantity", "product_quantity", "unique_scans_n", "energy-kcal_100g", "energy-kj_100g",
                "energy_100g", "proteins_100g", "carbohydrates_100g", "fat_100g"]
        missing = [n for n in need if n not in col]
        if missing:
            sys.exit(f"Spalten fehlen im Export: {missing}")
        stats = {"schweiz": 0, "ohne_name": 0, "ohne_kcal": 0, "ohne_protein": 0, "unplausibel": 0}
        for line in raw:
            if b"en:switzerland" not in line:
                continue
            p = line.rstrip(b"\r\n").decode("utf-8", errors="replace").split("\t")
            if len(p) != len(header) or "en:switzerland" not in p[col["countries_tags"]].split(","):
                continue
            stats["schweiz"] += 1
            name = clean_text(p[col["product_name"]], NAME_MAX)
            if not name or len(name) < 2 or not any(c.isalpha() for c in name):
                stats["ohne_name"] += 1
                continue
            kcal = kcal_of(p, col)
            if kcal is None:
                stats["ohne_kcal"] += 1
                continue
            protein = in_range(off_number(p[col["proteins_100g"]]), 0, GRAMS_MAX)
            if protein is None:
                stats["ohne_protein"] += 1
                continue
            carbs = in_range(off_number(p[col["carbohydrates_100g"]]), 0, GRAMS_MAX)
            fat = in_range(off_number(p[col["fat_100g"]]), 0, GRAMS_MAX)
            if not plausible(kcal, protein, carbs, fat, p[col["categories_tags"]]):
                stats["unplausibel"] += 1
                continue
            code = p[col["code"]].strip()
            brands = p[col["brands"]]
            yield {
                "codes": [code] if valid_code(code) else [],
                "name": name,
                "brand": next((b for b in (clean_text(x, BRAND_MAX) for x in brands.split(",")) if b), ""),
                "shops": shops_of(p[col["stores"]], brands),
                "kcal": kcal,
                "protein": protein,
                "carbs": carbs,
                "fat": fat,
                "portion": in_range(off_number(p[col["serving_quantity"]]), *PORTION_RANGE),
                "package": in_range(off_number(p[col["product_quantity"]]), *PACKAGE_RANGE),
                "scans": int(off_number(p[col["unique_scans_n"]]) or 0),
            }
        print(f"Open Food Facts: {stats}", file=sys.stderr)


def kcal_of(p, col):
    """Kalorien wie in OpenFoodFacts.kt: kcal, sonst kJ umgerechnet, sonst das alte Feld "energy" (immer kJ).
    0 ist ein echter Wert (Wasser, Tee), deshalb wird mit "is not None" geprüft und nicht mit "or"."""
    kcal = in_range(off_number(p[col["energy-kcal_100g"]]), 0, KCAL_MAX)
    if kcal is not None:
        return kcal
    for key in ("energy-kj_100g", "energy_100g"):
        kj = off_number(p[col[key]])
        if kj is not None and in_range(kj / KJ_PER_KCAL, 0, KCAL_MAX) is not None:
            return kj / KJ_PER_KCAL
    return None


def plausible(kcal, protein, carbs, fat, categories):
    """Grobe Fehler aussortieren, z. B. kJ ins kcal-Feld getippt. Alkohol liefert Energie ohne Makros."""
    if carbs is not None and fat is not None:
        if protein + carbs + fat > 105:
            return False
        if "en:alcoholic-beverages" in categories:
            return True
        estimate = 4 * protein + 4 * carbs + 9 * fat
        return estimate * 0.5 - 40 <= kcal <= estimate * 1.5 + 40
    return kcal >= 4 * protein * 0.6 - 20


def merge_duplicates(products):
    """Dasselbe Produkt steht oft mehrfach drin (mehrere Barcodes). Gleiche Werte = ein Eintrag, alle Barcodes."""
    merged = {}
    for item in products:
        key = (fold(item["name"]), fold(item["brand"]), round(item["kcal"]), round(item["protein"], 1))
        known = merged.get(key)
        if known is None:
            merged[key] = item
            continue
        if item["scans"] > known["scans"]:
            item, known = known, item
            merged[key] = known
        known["codes"] += [c for c in item["codes"] if c not in known["codes"]]
        known["shops"] += [s for s in item["shops"] if s not in known["shops"]]
        known["scans"] += item["scans"]
        for field in ("carbs", "fat", "portion", "package"):
            if known[field] is None:
                known[field] = item[field]
    # Reihenfolge = Rang bei gleich guten Treffern in der App. Zuerst Schweizer Produkte (bekannter Laden oder
    # Barcode mit 76…, der Schweizer GS1-Vorwahl), darin die oft gescannten zuerst.
    def swiss(i):
        return bool(i["shops"]) or any(c.startswith("76") for c in i["codes"])
    return sorted(merged.values(), key=lambda i: (not swiss(i), -i["scans"], i["name"]))


def write_off(products, out):
    out.parent.mkdir(parents=True, exist_ok=True)
    text = io.StringIO()
    text.write("# Quelle: Open Food Facts (openfoodfacts.org), Produkte mit Verkaufsland Schweiz.\n")
    text.write("# Lizenz: Open Database License (ODbL) 1.0, einzelne Inhalte: Database Contents License (DbCL) 1.0.\n")
    text.write("# Werte pro 100 g. Bereinigt mit tools/lebensmittel/naehrwerte.py.\n")
    text.write("# barcodes\tname\tmarke\tläden\tkcal\tprotein\tkohlenhydrate\tfett\tportion_g\tpackung_g\n")
    for p in products:
        text.write("\t".join([
            ",".join(p["codes"]), p["name"], p["brand"], ",".join(p["shops"]),
            fmt(p["kcal"]), fmt(p["protein"]), fmt(p["carbs"]), fmt(p["fat"]), fmt(p["portion"]), fmt(p["package"]),
        ]) + "\n")
    # mtime=0: Gleiche Daten ergeben dieselbe Datei, Git sieht dann keine Änderung.
    with open(out, "wb") as f, gzip.GzipFile(filename="", mode="wb", fileobj=f, mtime=0, compresslevel=9) as gz:
        gz.write(text.getvalue().encode("utf-8"))
    print(f"{out}: {len(products)} Produkte, {out.stat().st_size // 1024} KB")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--blv", type=Path, help="Schweizer_Nahrwertdatenbank.xlsx")
    parser.add_argument("--off", type=Path, help="en.openfoodfacts.org.products.csv.gz (oder ein Ausschnitt mit Kopfzeile)")
    parser.add_argument("--out", type=Path, default=ASSETS, help=f"Zielordner (Standard: {ASSETS})")
    args = parser.parse_args()
    if not args.blv and not args.off:
        parser.error("--blv und/oder --off angeben")
    if args.blv:
        version, items = read_blv(args.blv)
        write_blv(version, items, args.out / "blv.tsv")
    if args.off:
        write_off(merge_duplicates(read_off(args.off)), args.out / "openfoodfacts_ch.tsv.gzip")


if __name__ == "__main__":
    main()
