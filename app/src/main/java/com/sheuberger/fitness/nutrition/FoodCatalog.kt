package com.sheuberger.fitness.nutrition

import com.sheuberger.fitness.data.Food
import com.sheuberger.fitness.data.FoodBasis
import com.sheuberger.fitness.data.FoodSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Ein Lebensmittel aus den eingebauten Tabellen, Werte pro 100 g. Nur zum Lesen: Erst wenn du es einträgst,
 * bearbeitest oder als Favorit markierst, entsteht daraus ein eigenes Lebensmittel in der Datenbank ([toFood]).
 *
 * Bewusst keine data class: Gleichheit über alle Felder braucht niemand, und es gibt über 80'000 davon.
 */
class CatalogFood internal constructor(
    val source: FoodSource,
    val name: String,
    val brand: String?,
    /** Alle Barcodes mit Kommas am Rand (",7610…,7613…,"), damit [hasBarcode] keine Teilnummer findet. Leer bei BLV. */
    private val codes: String,
    val kcal: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val portionG: Double?,
    val packageG: Double?,
    /** Die Suchtexte, gefaltet mit [SearchText.key]: Name, Suchwörter (BLV) und Marke, Läden oder Kategorie. */
    internal val nameKey: String,
    internal val aliasKey: String,
    internal val extraKey: String,
    /** Rang bei gleich guten Treffern, kleiner = weiter oben: die Reihenfolge in der Datei (siehe naehrwerte.py). */
    internal val rank: Int,
    /** Zeile in der eigenen Tabelle, eindeutig je [source]. Die Liste braucht das als Schlüssel für jeden Eintrag. */
    internal val position: Int,
) {
    /** Der erste Barcode: Den bekommt das Lebensmittel, wenn es gespeichert wird. */
    val barcode: String? get() = if (codes.isEmpty()) null else codes.substring(1, codes.indexOf(',', 1))

    val barcodes: List<String> get() = codes.split(',').filter { it.isNotEmpty() }

    fun hasBarcode(code: String): Boolean = codes.contains(",$code,")

    /** Ein noch nicht gespeichertes Lebensmittel (ID 0) mit diesen Werten. */
    fun toFood(): Food = Food(
        name = name,
        brand = brand,
        barcode = barcode,
        source = source,
        basis = FoodBasis.PER_100G,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        portionG = portionG,
        packageG = packageG,
    )
}

/** Was die Suche in den eingebauten Tabellen gefunden hat, getrennt nach allgemeinen Lebensmitteln und Produkten. */
data class CatalogResults(
    val query: String = "",
    /** Aus der Schweizer Nährwertdatenbank, z. B. "Hühnerei, ganz, roh". */
    val generic: List<CatalogFood> = emptyList(),
    /** Produkte aus Schweizer Läden (Open Food Facts), z. B. "Vollmilch UHT" von M-Classic. */
    val products: List<CatalogFood> = emptyList(),
    /** Die Tabellen werden gerade geladen (nur beim ersten Mal, etwa eine halbe Sekunde). */
    val loading: Boolean = false,
) {
    val isEmpty: Boolean get() = generic.isEmpty() && products.isEmpty()
}

/**
 * Die eingebauten Nährwert-Tabellen: rund 1250 allgemeine Lebensmittel aus der Schweizer Nährwertdatenbank (BLV)
 * und über 80'000 Produkte aus Schweizer Läden von Open Food Facts. Beide liegen als Dateien in der App
 * (`assets/naehrwerte/`, erzeugt mit `tools/lebensmittel/naehrwerte.py`), die Suche braucht also kein Internet.
 *
 * Geladen wird beim ersten Gebrauch, im Hintergrund, und danach bleibt alles im Speicher (etwa 20 MB). Die Suche
 * geht dann jedes Mal durch alle Einträge: Bei 80'000 kurzen Texten dauert das nur wenige Millisekunden, und sie
 * findet auch Wortteile, z. B. "ei" in "Hühnerei". Eine Datenbank mit Suchindex könnte das nicht.
 *
 * @param open öffnet eine Datei aus `assets/` (in der App über den AssetManager, im Test aus einem Text).
 * @param scope in dem geladen wird. Ein eigener, nicht der der Suche: Tippst du weiter, bricht die alte Suche ab,
 *   das Laden aber nicht. Sonst finge es bei jedem Buchstaben von vorne an.
 */
class FoodCatalog(
    private val open: (String) -> InputStream,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    /**
     * @param words alle Wörter der Nährwerttabelle (Namen, Suchwörter, Kategorien), gefaltet: "milch", "brot",
     *   "joghurt". Nur bei diesen zählt das Wortende als guter Treffer, siehe [SearchText.Token.wordEnd].
     */
    private class Tables(val generic: List<CatalogFood>, val products: List<CatalogFood>, val words: Set<String>)

    // LAZY: Gelesen wird erst beim ersten Gebrauch, dann nie wieder. async liefert allen Wartenden dasselbe Ergebnis.
    private val tables: Deferred<Tables> = scope.async(start = CoroutineStart.LAZY) { readTables() }

    val isLoaded: Boolean get() = tables.isCompleted

    /** Beginnt schon einmal zu laden, z. B. wenn das Sheet "Eintragen" aufgeht. So ist die erste Suche sofort da. */
    fun preload() {
        tables.start()
    }

    private suspend fun load(): Tables = tables.await()

    private fun readTables(): Tables {
        // Viele Marken und Läden kommen tausendfach vor ("M-Classic", "Migros"). Jeder Text nur einmal im Speicher.
        val pool = HashMap<String, String>()
        fun shared(text: String): String = pool.getOrPut(text) { text }
        // Fehlt eine Datei (sollte nie passieren, sie ist Teil der App), bleibt die Tabelle leer statt abzustürzen.
        val generic = runCatching { readLines(BLV_FILE, gzip = false) { cols, index -> blv(cols, index, ::shared) } }
            .getOrDefault(emptyList())
        val products = runCatching { readLines(OFF_FILE, gzip = true) { cols, index -> product(cols, index, ::shared) } }
            .getOrDefault(emptyList())
        val words = HashSet<String>()
        for (food in generic) {
            for (key in arrayOf(food.nameKey, food.aliasKey, food.extraKey)) {
                key.split(' ').filterTo(words) { it.isNotEmpty() }
            }
        }
        return Tables(generic, products, words)
    }

    private fun <T : Any> readLines(file: String, gzip: Boolean, parse: (List<String>, Int) -> T?): List<T> {
        val raw = open(file)
        val stream = if (gzip) GZIPInputStream(raw, 64 * 1024) else raw
        val result = ArrayList<T>()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (line.isEmpty() || line.startsWith('#')) continue // Kommentare: Quelle, Lizenz, Spalten
                parse(line.split('\t'), result.size)?.let(result::add)
            }
        }
        return result
    }

    /**
     * Spalten: id, Name, Suchwörter (mit | getrennt), Kategorie, kcal, Protein, Kohlenhydrate, Fett, Portion.
     * Die Portion darf fehlen: Manche Editoren schneiden das Tab am Zeilenende ab, wenn die letzte Spalte leer ist.
     */
    private fun blv(c: List<String>, index: Int, shared: (String) -> String): CatalogFood? {
        if (c.size < 8) return null
        val name = c[1].ifEmpty { return null }
        return CatalogFood(
            source = FoodSource.BLV,
            name = name,
            brand = null,
            codes = "",
            kcal = c[4].toDoubleOrNull() ?: return null,
            proteinG = c[5].toDoubleOrNull(),
            carbsG = c[6].toDoubleOrNull(),
            fatG = c[7].toDoubleOrNull(),
            portionG = c.getOrNull(8)?.toDoubleOrNull(),
            packageG = null,
            nameKey = SearchText.key(name),
            aliasKey = shared(SearchText.key(c[2].replace('|', ' '))),
            extraKey = shared(SearchText.key(c[3])),
            // Die Datei ist schon sortiert: häufige Lebensmittel zuerst (rohes Ei vor Rührei), dann kurze Namen.
            rank = index,
            position = index,
        )
    }

    /** Spalten: Barcodes (mit Kommas), Name, Marke, Läden, kcal, Protein, Kohlenhydrate, Fett, Portion, Packung. */
    private fun product(c: List<String>, index: Int, shared: (String) -> String): CatalogFood? {
        if (c.size < 8) return null // Portion und Packung dürfen fehlen, wie bei der BLV-Tabelle
        val name = c[1].ifEmpty { return null }
        val brand = c[2].ifEmpty { null }?.let(shared)
        return CatalogFood(
            source = FoodSource.OFF,
            name = name,
            brand = brand,
            codes = if (c[0].isEmpty()) "" else ",${c[0]},",
            kcal = c[4].toDoubleOrNull() ?: return null,
            proteinG = c[5].toDoubleOrNull(),
            carbsG = c[6].toDoubleOrNull(),
            fatG = c[7].toDoubleOrNull(),
            portionG = c.getOrNull(8)?.toDoubleOrNull(),
            packageG = c.getOrNull(9)?.toDoubleOrNull(),
            nameKey = SearchText.key(name),
            aliasKey = SearchText.EMPTY_KEY,
            // Marke und Läden: "migros joghurt" findet so auch Joghurts von "M-Classic".
            extraKey = shared(SearchText.key("${brand.orEmpty()} ${c[3].replace(',', ' ')}")),
            // Die Datei ist schon sortiert: Schweizer Produkte zuerst, darin die oft gescannten.
            rank = index,
            position = index,
        )
    }

    /**
     * Sucht in beiden Tabellen. Jedes Wort der Suche muss vorkommen, Groß- und Kleinschreibung und Umlaute egal
     * ("muesli" findet "Müesli"). Die besten Treffer zuerst, siehe [SearchText.score].
     */
    suspend fun search(
        query: String,
        genericLimit: Int = GENERIC_LIMIT,
        productLimit: Int = PRODUCT_LIMIT,
    ): CatalogResults {
        if (SearchText.tokens(query).sumOf { it.plain.length } < MIN_QUERY_LENGTH) return CatalogResults(query)
        val tables = load()
        val tokens = SearchText.tokens(query, tables.words)
        return withContext(Dispatchers.Default) {
            CatalogResults(
                query = query,
                generic = best(tables.generic, tokens, genericLimit),
                products = best(tables.products, tokens, productLimit),
            )
        }
    }

    /**
     * Die [limit] besten Treffer. Punkte, Rang und Position stecken zusammen in einer Zahl (Long): Ein Array solcher
     * Zahlen lässt sich sehr schnell sortieren, ohne für jeden Treffer ein eigenes Objekt anzulegen.
     */
    private fun best(items: List<CatalogFood>, tokens: List<SearchText.Token>, limit: Int): List<CatalogFood> {
        val hits = LongArray(items.size)
        var count = 0
        for (i in items.indices) {
            val item = items[i]
            val score = SearchText.score(item, tokens)
            if (score < 0) continue
            hits[count++] = (score.toLong() shl 48) or (item.rank.toLong().coerceAtMost(0xFFFFFF) shl 24) or i.toLong()
        }
        hits.sort(0, count)
        return (0 until minOf(count, limit)).map { items[(hits[it] and 0xFFFFFF).toInt()] }
    }

    /**
     * Das Produkt mit diesem Barcode, ohne Internet. UPC-A (12 Ziffern) und EAN-13 mit führender 0 sind dieselbe
     * Nummer; Open Food Facts speichert mal die eine, mal die andere Schreibweise.
     */
    suspend fun byBarcode(code: String): CatalogFood? {
        val variants = buildList {
            add(code)
            if (code.length == 12) add("0$code")
            if (code.length == 13 && code.startsWith('0')) add(code.substring(1))
        }
        val tables = load()
        return withContext(Dispatchers.Default) {
            tables.products.firstOrNull { product -> variants.any(product::hasBarcode) }
        }
    }

    companion object {
        const val BLV_FILE = "naehrwerte/blv.tsv"
        // Bewusst nicht ".gz": Das Android-Gradle-Plugin packt .gz-Dateien in assets/ beim Bauen aus und entfernt
        // die Endung. In der App hieße die Datei dann "openfoodfacts_ch.tsv", und die Produktliste bliebe leer
        // (so passiert, deshalb prüft FoodCatalogAssetsTest die Dateien so, wie die App sie bekommt).
        const val OFF_FILE = "naehrwerte/openfoodfacts_ch.tsv.gzip"

        /** Quellenangabe der Schweizer Nährwertdatenbank (Pflicht laut BLV). */
        const val BLV_ATTRIBUTION = "Daten: Schweizer Nährwertdatenbank, BLV"

        /** Ab zwei Buchstaben: Bei einem einzelnen passt fast alles, und die Liste wäre nutzlos. */
        const val MIN_QUERY_LENGTH = 2
        const val GENERIC_LIMIT = 15
        const val PRODUCT_LIMIT = 40

        /** Ohne Dateien, z. B. für Tests, die die Tabellen nicht brauchen: Jede Suche findet nichts. */
        fun empty(): FoodCatalog = FoodCatalog({ name -> throw java.io.FileNotFoundException(name) })
    }
}

/** Suchtexte vergleichbar machen und Treffer bewerten. Ein eigenes Objekt, damit es sich einzeln testen lässt. */
object SearchText {
    /** Leerer Suchtext (nur die Ränder), geteilt von allen Produkten ohne Suchwörter. */
    const val EMPTY_KEY = "  "

    /**
     * Faltet einen Text für die Suche: klein, ohne Akzente ("é" → "e"), "ß" → "ss", alles außer Buchstaben und
     * Ziffern wird zu einem Leerzeichen. Dazu "ae", "oe", "ue" → "a", "o", "u": So findet "muesli" das Schweizer
     * "Müesli" und "müsli" auch. Ein schneller Weg über eine Tabelle, ohne java.text.Normalizer, denn beim Laden
     * läuft das über 80'000-mal.
     */
    fun fold(text: String): String {
        val out = StringBuilder(text.length)
        var space = true // am Anfang kein Leerzeichen
        for (raw in text) {
            val c = raw.lowercaseChar()
            val mapped: String? = when {
                c in 'a'..'z' || c in '0'..'9' -> null
                else -> ACCENTS[c] ?: " "
            }
            if (mapped == null) {
                out.append(c)
                space = false
            } else if (mapped == " ") {
                if (!space) out.append(' ')
                space = true
            } else {
                out.append(mapped)
                space = false
            }
        }
        while (out.isNotEmpty() && out[out.length - 1] == ' ') out.setLength(out.length - 1)
        // Erst nach dem Falten: "ü" ist hier schon "u", also wird "Müesli" zu "muesli" und dann zu "musli".
        return out.toString().replace("ae", "a").replace("oe", "o").replace("ue", "u")
    }

    /** Gefalteter Text mit je einem Leerzeichen am Rand: " huhnerei ganz roh ". So zeigt " ei " ein ganzes Wort. */
    fun key(text: String): String = " ${fold(text)} "

    /**
     * Ein Suchwort, mit den vorbereiteten Varianten für die Wortgrenzen (einmal pro Suche, nicht pro Eintrag).
     *
     * @param wordEnd ob ein Treffer am Wortende gut zählt: "milch" bei "Vollmilch". Nur ab drei Buchstaben, sonst
     *   wäre "ei" in "Molkerei" ein guter Treffer. Und nur, wenn das Suchwort selbst ein Lebensmittel-Wort ist:
     *   Wer "nute" tippt, will gleich "Nutella" und keine Plätzli "à la minute".
     */
    class Token(val plain: String, val wordEnd: Boolean = plain.length >= 3) {
        val word = " $plain "
        val prefix = " $plain"
        val suffix = "$plain "
        val negated = " ohne $plain"
    }

    /**
     * Die Wörter der Suche. Mit [words] (die Wörter der Nährwerttabelle) zählt das Wortende nur bei Wörtern, die es
     * dort als ganzes Wort gibt, siehe [Token.wordEnd]. Ohne (für deine eigenen Lebensmittel) ab drei Buchstaben.
     */
    fun tokens(query: String, words: Set<String>? = null): List<Token> =
        fold(query).split(' ').filter { it.isNotEmpty() }.map { plain ->
            Token(plain, wordEnd = plain.length >= 3 && (words == null || plain in words))
        }

    /**
     * Punkte für einen Eintrag, weniger ist besser, -1 = passt nicht. Jedes Suchwort muss irgendwo vorkommen;
     * die Punkte der Wörter werden addiert:
     * - 0: eigenes Suchwort ("Ei" bei "Hühnerei") oder erstes Wort des Namens ("Apfel" bei "Apfel, roh")
     * - 1: ganzes Wort im Namen ("Teigwaren mit Ei") oder Wortende: "milch" bei "Vollmilch". Im Deutschen steht
     *   das Wichtige am Ende eines zusammengesetzten Worts: "Vollmilch" ist eine Milch, "Milchschokolade" eine
     *   Schokolade. Wann das Wortende zählt, steht bei [Token.wordEnd].
     * - 2: Anfang des ersten Worts, z. B. beim Tippen: "apf" bei "Apfel, roh"
     * - 3: Wortanfang weiter hinten ("Milchschokolade" bei "milch"), bei einem Suchwort ("Eierfrucht" bei "ei")
     *   oder bei Marke, Laden, Kategorie ("migros")
     * - 4 und 5: irgendwo mitten im Wort, oder nach "ohne" ("Teigwaren ohne Ei")
     */
    internal fun score(item: CatalogFood, tokens: List<Token>): Int =
        score(item.nameKey, item.aliasKey, item.extraKey, tokens)

    /** Dasselbe für beliebige Texte, die schon mit [key] gefaltet sind, z. B. für deine eigenen Lebensmittel. */
    fun score(name: String, alias: String, extra: String, tokens: List<Token>): Int {
        var total = 0
        for (token in tokens) {
            val points = tokenScore(name, alias, extra, token)
            if (points < 0) return -1
            total += points
        }
        return total
    }

    private fun tokenScore(name: String, alias: String, extra: String, t: Token): Int {
        // Schneller Ausschluss zuerst: Bei den meisten Einträgen kommt das Wort gar nicht vor.
        val inName = name.contains(t.plain)
        if (!inName && !alias.contains(t.plain) && !extra.contains(t.plain)) return -1
        return when {
            alias.contains(t.word) -> 0
            // "Teigwaren ohne Ei" ist kein guter Treffer für "ei".
            inName && name.contains(t.negated) -> 4
            inName && name.startsWith(t.word) -> 0
            inName && name.contains(t.word) -> 1
            inName && t.wordEnd && name.contains(t.suffix) -> 1
            inName && name.startsWith(t.prefix) -> 2
            inName && name.contains(t.prefix) -> 3
            // Suchwörter sieht man in der Liste nicht. Passt nur ihr Anfang ("ei" bei "Eierfrucht"), stünde sonst
            // "Aubergine" weit oben, und niemand wüsste, warum.
            alias.contains(t.prefix) -> 3
            extra.contains(t.prefix) -> 3
            inName -> 4
            else -> 5
        }
    }

    /** Buchstaben mit Akzent und ihre Grundform. Was fehlt (z. B. "–" oder "&"), wird zum Leerzeichen. */
    private val ACCENTS: Map<Char, String> = buildMap {
        fun put(chars: String, base: String) = chars.forEach { put(it, base) }
        put("àáâãäåāăą", "a")
        put("çćĉċč", "c")
        put("ďđ", "d")
        put("èéêëēĕėęě", "e")
        put("ĝğġģ", "g")
        put("ìíîïĩīĭįı", "i")
        put("ĵ", "j")
        put("ķ", "k")
        put("ĺļľŀł", "l")
        put("ñńņňŉ", "n")
        put("òóôõöøōŏő", "o")
        put("ŕŗř", "r")
        put("śŝşš", "s")
        put("ţťŧ", "t")
        put("ùúûüũūŭůűų", "u")
        put("ŵ", "w")
        put("ýÿŷ", "y")
        put("źżž", "z")
        put("ß", "ss")
        put("æ", "ae")
        put("œ", "oe")
    }
}
