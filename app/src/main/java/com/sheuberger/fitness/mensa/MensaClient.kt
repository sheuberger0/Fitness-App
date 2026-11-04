package com.sheuberger.fitness.mensa

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * Lädt die Menüs aller Mensen für einen Tag, parallel und mit Zwischenspeicher.
 *
 * Fällt eine Mensa aus (keine Verbindung, Seite geändert), fehlen nur ihre Gerichte; welche das sind,
 * steht in [DayMenu.failed]. Ohne Internet schlagen alle fehl, dann zeigt der Screen einen Hinweis.
 */
class MensaClient(
    private val http: suspend (String) -> String = ::download,
) {
    private val cache = mutableMapOf<LocalDate, DayMenu>()
    private val cacheLock = Mutex()

    // Höchstens 6 Seiten gleichzeitig laden: schnell genug und fair gegenüber den Servern.
    private val parallel = Semaphore(6)

    fun cached(date: LocalDate): DayMenu? = cache[date]

    suspend fun load(date: LocalDate, refresh: Boolean = false): DayMenu {
        if (!refresh) cacheLock.withLock { cache[date] }?.let { return it }
        val result = coroutineScope {
            Mensa.entries.map { mensa -> async { mensa to attempt { loadMensa(mensa, date) } } }.awaitAll()
        }
        val menu = DayMenu(
            meals = result.flatMap { (_, r) -> r.getOrDefault(emptyList()) },
            failed = result.filter { (_, r) -> r.isFailure }.map { it.first }.toSet(),
        )
        // Nur speichern, wenn alles geklappt hat. Sonst versucht es der nächste Aufruf noch einmal.
        if (menu.failed.isEmpty()) cacheLock.withLock { cache[date] = menu }
        return menu
    }

    private suspend fun loadMensa(mensa: Mensa, date: LocalDate): List<Meal> = when (mensa.university) {
        University.ETH -> {
            // Ein Aufruf liefert die ganze Woche (Mittag und Abend) dieser Mensa.
            val monday = date.minusDays(date.dayOfWeek.value - 1L)
            val url = "https://idapps.ethz.ch/cookpit-pub-services/v1/weeklyrotas" +
                "?client-id=ethz-wcms&lang=de&rs-first=0&rs-size=50&valid-after=$monday&facility=${mensa.lunch}"
            EthMenuParser.parse(limited { http(url) }, date, mensa)
        }
        University.UZH -> MealTime.entries.flatMap { time -> loadUzh(mensa, time, date) }
    }

    private suspend fun loadUzh(mensa: Mensa, time: MealTime, date: LocalDate): List<Meal> = coroutineScope {
        val path = mensa.source(time) ?: return@coroutineScope emptyList()
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        val weekly = limited { http("https://app.food2050.ch/de/zfv/$path/menu/weekly?week=$week&year=$year") }
        val links = UzhMenuParser.dishLinks(weekly, date)
        val dishes = links
            .map { link -> async { attempt { UzhMenuParser.parseDish(limited { http(link) }, link, mensa, time) } } }
            .awaitAll()
        // Fehlt einzelnes Gericht, zeigen wir die anderen trotzdem. Klappt keines, gilt die Mensa als ausgefallen.
        if (links.isNotEmpty() && dishes.all { it.isFailure }) throw dishes.first().exceptionOrNull()!!
        dishes.mapNotNull { it.getOrNull() }
    }

    /** Wie runCatching, aber ein Abbruch (Screen geschlossen) bleibt ein Abbruch und wird nicht zum Fehler. */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun <T> limited(block: suspend () -> T): T = parallel.withPermit { block() }

    companion object {
        /** Größte erlaubte Antwort. Gemessen sind heute höchstens rund 190 KB pro Seite, 2 MB lassen viel Luft. */
        internal const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024

        /** Gesamtzeit pro Download, zusätzlich zu Verbindungs- und Lese-Timeout (die gelten je Schritt). */
        internal const val TOTAL_TIMEOUT_MS = 30_000L

        /** Einfacher GET-Aufruf. HttpURLConnection ist in Android eingebaut, dafür braucht es keine Bibliothek. */
        suspend fun download(url: String): String = withContext(Dispatchers.IO) {
            val deadline = System.nanoTime() + TOTAL_TIMEOUT_MS * 1_000_000
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept-Language", "de")
                connection.setRequestProperty("User-Agent", "FitnessApp/1.9 (Android)")
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("HTTP $code für $url")
                // Früh abbrechen, wenn der Server selbst schon eine zu große Antwort ankündigt.
                if (connection.contentLengthLong > MAX_RESPONSE_BYTES) {
                    throw IOException("Antwort zu groß (${connection.contentLengthLong} Bytes) für $url")
                }
                connection.inputStream.use { readLimited(it, MAX_RESPONSE_BYTES, deadline) }
            } finally {
                connection.disconnect()
            }
        }

        /**
         * Liest [input] vollständig als UTF-8, aber höchstens [maxBytes] Bytes und nur bis [deadlineNanos]
         * (gemessen mit [now]). Die Länge im Header kann fehlen oder falsch sein, deshalb zählen wir selbst mit.
         * Zu groß oder zu langsam → IOException, die MensaClient wie jeden anderen Ladefehler behandelt.
         */
        internal fun readLimited(
            input: InputStream,
            maxBytes: Long,
            deadlineNanos: Long,
            now: () -> Long = System::nanoTime,
        ): String {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0L
            while (true) {
                if (now() - deadlineNanos > 0) throw IOException("Download dauert zu lange, abgebrochen")
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw IOException("Antwort größer als $maxBytes Bytes, abgebrochen")
                out.write(buffer, 0, n)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }
}
