package com.sheuberger.fitness.nutrition

import com.sheuberger.fitness.data.Food
import com.sheuberger.fitness.data.FoodBasis
import com.sheuberger.fitness.data.FoodSource
import com.sheuberger.fitness.mensa.requireShallowJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Ergebnis einer Barcode-Suche bei Open Food Facts. */
sealed interface OffLookup {
    data class Found(val food: Food) : OffLookup
    data object NotFound : OffLookup
    data class Failed(val message: String) : OffLookup
}

/**
 * Sucht Produkte per Barcode bei Open Food Facts (offene Datenbank, Lesen ohne Konto und ohne Schlüssel).
 *
 * Alles, was aus dem Internet kommt, gilt als unsicher: Der Barcode wird vor der Anfrage geprüft, die
 * Antwort darf höchstens 2 MB groß sein, unsinnige Zahlen (negativ, 5000 kcal) fallen weg, und Namen
 * werden bereinigt. Kein Produkt, kein Fehler: Ohne brauchbare Kalorien gilt es als [OffLookup.NotFound],
 * dann öffnet die App das Formular zum Selbst-Eintragen.
 *
 * Die drei Parameter lassen sich in Tests ersetzen. So prüfen die Tests 404, 429 oder 503 ohne Internet
 * und ohne echte Wartezeiten. Die App nutzt einfach `OpenFoodFactsClient()`.
 */
class OpenFoodFactsClient(
    private val http: suspend (url: String) -> Response = ::download,
    private val pause: suspend (millis: Long) -> Unit = { delay(it) },
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Antwort des Servers: HTTP-Status und Inhalt. [download] liest den Inhalt nur bei Erfolg (2xx). */
    class Response(val status: Int, val body: String)

    suspend fun lookup(barcode: String): OffLookup {
        // Die UI prüft den Code schon, aber hier wird daraus eine Internetadresse. Deshalb noch einmal:
        // Nur 8, 12 oder 13 Ziffern mit richtiger Prüfziffer kommen in die URL, nie Text wie "../" oder "%2F".
        val code = Barcode.validate(barcode) ?: return OffLookup.Failed(INVALID_CODE)
        if (Barcode.isInStore(code)) return OffLookup.Failed(IN_STORE_CODE)
        val url = "$PRODUCT_URL$code.json?fields=$FIELDS"

        var retries = 0
        while (true) {
            val response = try {
                http(url)
            } catch (e: TooLargeException) {
                return OffLookup.Failed(TOO_LARGE)
            } catch (e: IOException) {
                // Kein Internet, Zeitüberschreitung, Server nicht erreichbar. Die Meldung der Exception zeigen
                // wir nicht: Sie kann die Adresse mit dem Barcode enthalten und hilft dir ohnehin nicht weiter.
                return OffLookup.Failed(NO_CONNECTION)
            }
            when (response.status) {
                in 200..299 -> return parse(response.body, code, now())
                404 -> return OffLookup.NotFound
                // 429 heißt "zu viele Anfragen". Sofort nochmal fragen würde es nur schlimmer machen.
                429 -> return OffLookup.Failed(RATE_LIMITED)
                in 500..599 -> {
                    // Serverfehler sind oft nach Sekunden vorbei: zweimal nachfragen, mit wachsender Pause.
                    if (retries == RETRY_WAITS_MS.size) return OffLookup.Failed(SERVER_ERROR)
                    pause(RETRY_WAITS_MS[retries])
                    retries++
                }
                else -> return OffLookup.Failed("Open Food Facts meldet einen Fehler (HTTP ${response.status}).")
            }
        }
    }

    companion object {
        /** Pflicht laut Lizenz (ODbL): Die Quelle wird überall genannt, wo die Daten erscheinen. */
        const val ATTRIBUTION = "Daten: Open Food Facts, ODbL"

        /**
         * So stellt sich die App vor. Open Food Facts bittet um App-Name, Version und eine Kontaktmöglichkeit.
         * Bewusst ohne E-Mail-Adresse: Der User-Agent landet in fremden Server-Logs.
         */
        internal const val USER_AGENT = "FitnessApp/2.0 (Android; github.com/sheuberger0/Fitness-App)"

        internal const val PRODUCT_URL = "https://world.openfoodfacts.org/api/v2/product/"

        /**
         * Nur diese Felder anfordern. Ohne `fields` schickt der Server das ganze Produkt mit Zutaten,
         * Bildern und Übersetzungen; so sind es ein paar KB (Toblerone: 5 KB, fast alles Nährwerte).
         */
        internal const val FIELDS = "product_name,product_name_de,generic_name,brands,nutriments," +
            "serving_quantity,serving_quantity_unit,product_quantity,product_quantity_unit"

        /** Größere Antworten bricht die App ab. Eine echte ist ein paar KB groß; so reicht der Speicher immer. */
        internal const val MAX_BYTES = 2 * 1024 * 1024

        /** Pausen vor dem 2. und 3. Versuch nach einem Serverfehler (5xx). */
        internal val RETRY_WAITS_MS = listOf(1_000L, 3_000L)

        internal const val INVALID_CODE = "Ungültiger Barcode. Bitte die Ziffern prüfen."
        internal const val IN_STORE_CODE =
            "Ladencodes (13 Ziffern, vorne eine 2) vergibt jedes Geschäft selbst. Bitte die Werte von Hand eintragen."
        internal const val RATE_LIMITED = "Open Food Facts bremst gerade. Bitte in einer Minute nochmal versuchen."
        internal const val SERVER_ERROR = "Open Food Facts ist gerade nicht erreichbar. Bitte später nochmal versuchen."
        internal const val NO_CONNECTION = "Keine Verbindung zu Open Food Facts."
        internal const val TOO_LARGE = "Die Antwort von Open Food Facts ist zu groß."
        internal const val UNEXPECTED = "Open Food Facts hat eine unerwartete Antwort geschickt."

        /** 1 kcal = 4,184 kJ. */
        internal const val KJ_PER_KCAL = 4.184

        // Was außerhalb liegt, ist ein Tippfehler in der Datenbank (z. B. kJ ins kcal-Feld) und gilt als fehlend.
        // Fett hat am meisten Energie: Reines Öl hat knapp 900 kcal pro 100 g.
        private val KCAL_RANGE = 0.0..900.0
        private val GRAMS_RANGE = 0.0..100.0
        private val PORTION_RANGE = 1.0..2000.0
        private val PACKAGE_RANGE = 1.0..20_000.0

        private const val NAME_MAX = 80
        private const val BRAND_MAX = 40

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Einfacher GET-Aufruf mit HttpURLConnection (in Android eingebaut), wie [com.sheuberger.fitness.mensa.MensaClient].
         * Es wird nichts geloggt: weder der Barcode (verrät, was du isst) noch die Antwort.
         */
        internal suspend fun download(url: String): Response = withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", USER_AGENT)
                val status = connection.responseCode
                // Bei 404, 429 oder 503 braucht die App nur den Status, den Inhalt liest sie gar nicht erst.
                val body = if (status in 200..299) connection.inputStream.use { readLimited(it) } else ""
                Response(status, body)
            } finally {
                connection.disconnect()
            }
        }

        /**
         * Liest höchstens [limit] Bytes und macht daraus Text (UTF-8). Ist der Inhalt größer, bricht es mit
         * [TooLargeException] ab, statt alles in den Speicher zu laden. `readText()` würde auch 500 MB lesen.
         */
        internal fun readLimited(input: InputStream, limit: Int = MAX_BYTES): String {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > limit) throw TooLargeException()
                out.write(buffer, 0, n)
            }
            // Erst am Ende in Text umwandeln: Ein "ü" besteht aus zwei Bytes und könnte sonst genau an der
            // Grenze zwischen zwei Puffern zerschnitten werden.
            return String(out.toByteArray(), Charsets.UTF_8)
        }

        /** Liest die Antwort der Produkt-Abfrage. [code] ist der geprüfte Barcode, [fetchedAt] die Uhrzeit. */
        internal fun parse(body: String, code: String, fetchedAt: Long): OffLookup {
            val element = try {
                // Erst die Verschachtelung prüfen, wie bei den Mensa-Menüs: Eine kaputte Antwort mit Tausenden
                // Klammern ("[[[[…") würde den Parser sonst viel Speicher kosten. Eine echte ist 3 Ebenen tief.
                json.parseToJsonElement(requireShallowJson(body))
            } catch (e: IllegalArgumentException) {
                // Kein JSON: z. B. eine HTML-Fehlerseite, ein WLAN-Anmeldeportal oder eine abgebrochene Antwort.
                // SerializationException (Lesefehler) ist eine IllegalArgumentException, die Tiefenprüfung auch.
                return OffLookup.Failed(UNEXPECTED)
            }
            // Gültiges JSON, aber kein Objekt (z. B. [] oder "Fehler"): ebenfalls nicht die erwartete Antwort.
            val root = element as? JsonObject ?: return OffLookup.Failed(UNEXPECTED)

            // Unbekannte Codes beantwortet die API mit HTTP 404 und "status": 0. Ältere Versionen schickten
            // dabei HTTP 200, deshalb zählt auch der Status im JSON.
            if (root.number("status") == 0.0) return OffLookup.NotFound
            val product = root["product"] as? JsonObject ?: return OffLookup.Failed(UNEXPECTED)
            return product.toFood(code, fetchedAt)?.let { OffLookup.Found(it) } ?: OffLookup.NotFound
        }

        /** null, wenn sich keine Kalorien pro 100 g ermitteln lassen: Ohne die nützt der Eintrag nichts. */
        private fun JsonObject.toFood(code: String, fetchedAt: Long): Food? {
            val nutriments = this["nutriments"] as? JsonObject ?: JsonObject(emptyMap())
            val portionG = quantity("serving_quantity", "serving_quantity_unit", PORTION_RANGE)
            val kcal = nutriments.kcalPer100g(portionG) ?: return null
            return Food(
                // Deutscher Name zuerst. Schweizer Produkte haben oft einen französischen Hauptnamen.
                name = listOf("product_name_de", "product_name", "generic_name")
                    .firstNotNullOfOrNull { cleanText(text(it), NAME_MAX) }
                    // Ohne Namen: Die Nummer macht den Eintrag eindeutig, und du findest sie auf der Packung.
                    ?: "Produkt $code",
                brand = firstBrand(),
                barcode = code,
                source = FoodSource.OFF,
                basis = FoodBasis.PER_100G,
                kcal = kcal.round1(),
                proteinG = nutriments.per100g("proteins", GRAMS_RANGE, portionG)?.round1(),
                carbsG = nutriments.per100g("carbohydrates", GRAMS_RANGE, portionG)?.round1(),
                fatG = nutriments.per100g("fat", GRAMS_RANGE, portionG)?.round1(),
                portionG = portionG,
                packageG = quantity("product_quantity", "product_quantity_unit", PACKAGE_RANGE),
                fetchedAt = fetchedAt,
            )
        }

        /**
         * Kalorien pro 100 g, in dieser Reihenfolge: kcal, sonst kJ umgerechnet, sonst das alte Feld "energy"
         * (immer kJ), sonst kcal pro Portion hochgerechnet. Jeder Wert muss zwischen 0 und 900 liegen.
         */
        private fun JsonObject.kcalPer100g(portionG: Double?): Double? =
            number("energy-kcal_100g").within(KCAL_RANGE)
                ?: number("energy-kj_100g")?.div(KJ_PER_KCAL).within(KCAL_RANGE)
                ?: number("energy_100g")?.div(KJ_PER_KCAL).within(KCAL_RANGE)
                ?: perPortionTo100g(number("energy-kcal_serving"), portionG).within(KCAL_RANGE)

        /** Gramm pro 100 g, z. B. "proteins". Fehlt der Wert, wird er wie die Kalorien aus der Portion berechnet. */
        private fun JsonObject.per100g(key: String, range: ClosedFloatingPointRange<Double>, portionG: Double?): Double? =
            number("${key}_100g").within(range)
                ?: perPortionTo100g(number("${key}_serving"), portionG).within(range)

        /** 180 kcal in einer Portion von 40 g sind 450 kcal pro 100 g. */
        private fun perPortionTo100g(value: Double?, portionG: Double?): Double? =
            if (value == null || portionG == null) null else value * 100 / portionG

        /**
         * Portions- oder Packungsgröße. Open Food Facts rechnet sie in g oder ml um; ml zählen wie g, so wie die
         * Nährwerte von Getränken auch pro 100 ml angegeben sind. Eine andere Einheit gilt als fehlend.
         */
        private fun JsonObject.quantity(key: String, unitKey: String, range: ClosedFloatingPointRange<Double>): Double? {
            val unit = text(unitKey)?.trim()?.lowercase()
            if (!unit.isNullOrEmpty() && unit != "g" && unit != "ml") return null
            return number(key).within(range)
        }

        /** "Elsa, M-Classic, Migros" → "Elsa". Manchmal kommt die Liste als JSON-Array. */
        private fun JsonObject.firstBrand(): String? {
            val names = when (val brands = this["brands"]) {
                is JsonArray -> brands.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                is JsonPrimitive -> if (brands.isString) brands.content.split(',') else emptyList()
                else -> emptyList()
            }
            return names.firstNotNullOfOrNull { cleanText(it, BRAND_MAX) }
        }

        /**
         * Macht aus einem Text aus dem Internet einen sauberen Namen: Leerzeichen am Rand weg, Zeilenumbrüche
         * und mehrere Leerzeichen werden zu einem, unsichtbare Zeichen fallen weg, höchstens [maxLength] Zeichen.
         * null, wenn nichts übrig bleibt.
         */
        internal fun cleanText(raw: String?, maxLength: Int): String? {
            if (raw == null) return null
            val text = StringBuilder()
            var spaceBefore = false
            for (c in raw) {
                when {
                    // Leerzeichen, Tabs, Zeilenumbrüche, geschützte Leerzeichen: merken, später höchstens eins einfügen.
                    c.isWhitespace() -> spaceBefore = text.isNotEmpty()
                    // Steuerzeichen (z. B. \u0000) und unsichtbare Formatzeichen (z. B. eines, das die Schreibrichtung
                    // umdreht und den Namen in der Liste rückwärts zeigt).
                    c.category == CharCategory.CONTROL || c.category == CharCategory.FORMAT -> Unit
                    else -> {
                        if (spaceBefore) text.append(' ')
                        spaceBefore = false
                        text.append(c)
                    }
                }
                if (text.length > maxLength) break // Der Rest würde ohnehin abgeschnitten.
            }
            var end = minOf(text.length, maxLength)
            // Emojis bestehen aus zwei Zeichen (Surrogat-Paar). Nicht zwischen den beiden abschneiden.
            if (end in 1 until text.length && text[end - 1].isHighSurrogate()) end--
            return text.substring(0, end).trimEnd().ifEmpty { null }
        }

        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        /**
         * Eine Zahl aus dem JSON. Meist echte Zahlen (529), manchmal Text ("529" oder "12,5" mit Komma).
         * Text mit Einheit ("12 g"), NaN und unendlich gelten als fehlend.
         */
        private fun JsonObject.number(key: String): Double? {
            val value = this[key] as? JsonPrimitive ?: return null
            val number = if (value.isString) {
                value.content.trim().replace(',', '.').takeIf { DECIMAL.matches(it) }?.toDouble()
            } else {
                value.content.toDoubleOrNull() // null bei JSON-null, true oder false
            }
            return number?.takeIf { it.isFinite() }
        }

        private val DECIMAL = Regex("-?\\d+(\\.\\d+)?")

        private fun Double?.within(range: ClosedFloatingPointRange<Double>): Double? = this?.takeIf { it in range }

        /** Eine Nachkommastelle reicht. Aus kJ umgerechnet stünde sonst 473,23135755 kcal im Formular. */
        private fun Double.round1(): Double = Math.round(this * 10) / 10.0
    }
}

/** Die Antwort ist größer als [OpenFoodFactsClient.MAX_BYTES]. Kein Verbindungsfehler, deshalb keine IOException. */
internal class TooLargeException : Exception("Antwort zu groß")
