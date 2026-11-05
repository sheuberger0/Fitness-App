package com.sheuberger.fitness.ui.nutrition

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sheuberger.fitness.data.AppDatabase
import com.sheuberger.fitness.data.Food
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.FoodSource
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.data.NutritionRepository
import com.sheuberger.fitness.mensa.MensaClient
import com.sheuberger.fitness.mensa.MensaSettings
import com.sheuberger.fitness.nutrition.FoodCatalog
import com.sheuberger.fitness.nutrition.OffLookup
import com.sheuberger.fitness.ui.mensa.MenuLoad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPOutputStream

/**
 * Prüft das ViewModel des Sheets "Eintragen" mit einer echten Datenbank im Speicher und Attrappen für
 * Open Food Facts und die Mensa-Server.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddFoodViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val nutrition = NutritionRepository(db)

    // Statt des Haupt-Threads, der im Test mit dem Test selbst beschäftigt ist: Unconfined startet jede Coroutine
    // sofort im aufrufenden Thread. Die Mensa-Attrappe wartet nie, deshalb ist ein Ladevorgang schon fertig,
    // wenn loadMensa zurückkehrt.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val monday = LocalDate.of(2026, 11, 2)
    private val ethWeek = javaClass.getResource("/mensa/eth_polymensa.json")!!.readText()

    /** Adressen, die die Mensa-Attrappe beantwortet hat. Threadsicher, falls Mensen gleichzeitig laden. */
    private val mensaRequests = CopyOnWriteArrayList<String>()

    @Volatile
    private var offline = false

    /** Nur die Seiten der UZH-Mensen (app.food2050.ch) sind nicht erreichbar. */
    @Volatile
    private var uzhDown = false

    private val mensaClient = MensaClient { url ->
        mensaRequests += url
        if (offline || (uzhDown && "food2050" in url)) throw IOException("Keine Verbindung")
        // Polymensa bekommt den echten Wochenplan. Die UZH-Wochenseiten sind leer, dann lädt der Client nichts weiter.
        if ("idapps.ethz.ch" in url) ethWeek else "<html></html>"
    }

    /** Codes, nach denen die Open-Food-Facts-Attrappe gefragt wurde, und ihre Antwort. */
    private val lookups = mutableListOf<String>()
    private var answer: suspend (String) -> OffLookup = { OffLookup.NotFound }

    private val viewModel = AddFoodViewModel(
        nutrition = nutrition,
        mensaClient = mensaClient,
        mensaSettings = MensaSettings(context),
        lookup = { code ->
            lookups += code
            answer(code)
        },
        scope = scope,
    )

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun resolve(raw: String): BarcodeOutcome = runBlocking { viewModel.resolveBarcode(raw) }

    private fun save(food: Food): Long = runBlocking { nutrition.saveFood(food) }

    /**
     * Was das Sheet für [date] anzeigt. Jeder Test liest das nur einmal, ganz am Ende: Der erste Wert des
     * uiState entsteht dann aus dem fertigen Stand.
     */
    private fun menuShownFor(date: LocalDate): MenuLoad? = runBlocking {
        withTimeout(5_000) { viewModel.uiState.first { date in it.menus }.menus[date] }
    }

    // --- Barcode ---

    @Test
    fun barcode_ungueltig_ohneDatenbankUndInternet() {
        assertEquals(BarcodeOutcome.Invalid, resolve("123"))
        assertEquals(BarcodeOutcome.Invalid, resolve("7614500010014")) // falsche Prüfziffer
        assertEquals(BarcodeOutcome.Invalid, resolve("../7614500010013"))
        assertEquals(BarcodeOutcome.Invalid, resolve(""))
        assertTrue(lookups.isEmpty())
    }

    @Test
    fun barcode_schonInMeinenLebensmitteln_auchOhneInternet() {
        answer = { throw IOException("Keine Verbindung") }
        val id = save(Food(name = "Toblerone", barcode = "7614500010013", kcal = 529.0, favorite = true))

        val outcome = resolve(" 7614500010013 ")

        val food = (outcome as BarcodeOutcome.Known).food
        assertEquals(id, food.id)
        assertEquals("Toblerone", food.name)
        assertTrue(food.favorite)
        assertTrue(lookups.isEmpty())
    }

    @Test
    fun barcode_eigenerLadencode_wirdErkannt() {
        // Einmal selbst angelegt, z. B. abgepacktes Poulet: Beim nächsten Scan kennt die App es.
        save(Food(name = "Pouletbrust", barcode = "2181113008607", kcal = 110.0))
        assertEquals("Pouletbrust", (resolve("2181113008607") as BarcodeOutcome.Known).food.name)
        assertTrue(lookups.isEmpty())
    }

    @Test
    fun barcode_ladencode_ohneAnfrage() {
        assertEquals(BarcodeOutcome.InStore("2181113008607"), resolve("2181113008607"))
        assertTrue(lookups.isEmpty())
    }

    @Test
    fun barcode_gefunden_nochNichtGespeichertMitDemGescanntenCode() {
        answer = {
            OffLookup.Found(Food(id = 42, name = "Toblerone", barcode = "4006381333931", kcal = 529.0, source = FoodSource.OFF))
        }

        val food = (resolve("7614500010013") as BarcodeOutcome.Found).food

        assertEquals(0L, food.id)
        assertEquals("7614500010013", food.barcode)
        assertEquals("Toblerone", food.name)
        assertEquals(FoodSource.OFF, food.source)
        assertEquals(listOf("7614500010013"), lookups)
        // Gespeichert wird erst beim Eintragen.
        assertTrue(runBlocking { nutrition.foods.first() }.isEmpty())
    }

    @Test
    fun barcode_nichtGefunden() {
        answer = { OffLookup.NotFound }
        assertEquals(BarcodeOutcome.NotFound("7614500010013"), resolve("7614500010013"))
    }

    @Test
    fun barcode_fehlgeschlagen_meldungDerSuche() {
        answer = { OffLookup.Failed("Open Food Facts bremst gerade.") }
        assertEquals(BarcodeOutcome.Failed("7614500010013", "Open Food Facts bremst gerade."), resolve("7614500010013"))
    }

    @Test
    fun barcode_ausnahmeWirdZurVerstaendlichenMeldung() {
        answer = { throw IllegalStateException("java.lang.IllegalStateException at x.y.z") }

        val outcome = resolve("7614500010013") as BarcodeOutcome.Failed

        assertEquals("7614500010013", outcome.code)
        assertTrue(outcome.message.isNotBlank())
        assertFalse(outcome.message.contains("Exception"))
    }

    @Test
    fun barcode_abbruchBleibtEinAbbruch() {
        // Sheet geschlossen, während die Suche läuft: Das ist kein Fehler, der angezeigt werden soll.
        answer = { throw CancellationException("Sheet geschlossen") }
        runBlocking {
            try {
                viewModel.resolveBarcode("7614500010013")
                fail("CancellationException erwartet")
            } catch (e: CancellationException) {
                assertEquals("Sheet geschlossen", e.message)
            }
        }
    }

    // --- Mensa ---

    @Test
    fun mensa_zeigtSofortWasDerMensaTabSchonGeladenHat() {
        val menu = runBlocking { mensaClient.load(monday) } // so lädt der Mensa-Tab
        val requestsBefore = mensaRequests.size

        viewModel.loadMensa(monday)

        assertEquals(MenuLoad.Loaded(menu), menuShownFor(monday))
        assertEquals(requestsBefore, mensaRequests.size)
    }

    @Test
    fun mensa_laedtDieMenuesDesTages() {
        viewModel.loadMensa(monday)

        val loaded = menuShownFor(monday) as MenuLoad.Loaded
        assertTrue(loaded.menu.meals.any { it.name == "Lasagne Emiliana" })
        assertTrue(loaded.menu.failed.isEmpty())
    }

    @Test
    fun mensa_behaeltDieAltenMenues_wennNeuladenScheitert() {
        viewModel.loadMensa(monday)
        val requestsAfterFirstLoad = mensaRequests.size

        offline = true
        viewModel.loadMensa(monday, refresh = true)

        assertNotEquals("Es wurde wirklich neu geladen", requestsAfterFirstLoad, mensaRequests.size)
        val shown = menuShownFor(monday) as MenuLoad.Loaded
        assertTrue(shown.menu.meals.any { it.name == "Lasagne Emiliana" })
    }

    @Test
    fun mensa_behaeltAuchTeilweiseGeladeneMenues_dieNichtZwischengespeichertSind() {
        // Die UZH-Mensen fallen aus: Der Client speichert so einen Tag nicht zwischen, nur das Sheet hat ihn.
        uzhDown = true
        viewModel.loadMensa(monday)
        assertNull(mensaClient.cached(monday))

        offline = true
        viewModel.loadMensa(monday, refresh = true)

        val shown = menuShownFor(monday) as MenuLoad.Loaded
        assertTrue(shown.menu.meals.any { it.name == "Lasagne Emiliana" })
        assertEquals(3, shown.menu.failed.size)
    }

    @Test
    fun mensa_ohneInternetUndOhneAlteMenues_offline_danachErneutVersuchen() {
        offline = true
        viewModel.loadMensa(monday)
        val requestsOffline = mensaRequests.size

        // Wieder online: Ein neuer Aufruf (z. B. "Erneut versuchen") lädt, weil der Tag nicht geladen ist.
        offline = false
        viewModel.loadMensa(monday)

        assertTrue(mensaRequests.size > requestsOffline)
        assertTrue(menuShownFor(monday) is MenuLoad.Loaded)
    }

    @Test
    fun mensa_ohneInternet_zeigtOffline() {
        offline = true
        viewModel.loadMensa(monday)
        assertEquals(MenuLoad.Offline, menuShownFor(monday))
    }

    // --- Eintragen und Speichern ---

    @Test
    fun eintragenLandetImTagebuch() = runBlocking {
        viewModel.log(FoodEntry(date = monday, meal = MealSlot.LUNCH, name = "Lasagne Emiliana", kcal = 584.0))

        val day = withTimeout(5_000) { nutrition.day(monday).first { it.isNotEmpty() } }
        assertEquals(listOf("Lasagne Emiliana"), day.map { it.name })
    }

    @Test
    fun speichernGibtDasLebensmittelMitIdZurueck() = runBlocking {
        val saved = CompletableDeferred<Food>()

        viewModel.saveFood(Food(name = "Skyr", kcal = 63.0, proteinG = 11.0)) { saved.complete(it) }

        val food = withTimeout(5_000) { saved.await() }
        assertNotEquals(0L, food.id)
        assertEquals("Skyr", nutrition.food(food.id)!!.name)
    }

    // --- Eingebaute Tabellen ---

    /** Ein ViewModel mit kleinen Nährwert-Tabellen: zwei Lebensmittel (BLV) und ein Produkt (Open Food Facts). */
    private fun withCatalog(): AddFoodViewModel {
        val blv = "290\tHühnerei, ganz, roh\tEi|Eier\tEier\t140\t12.6\t0.3\t9.8\t55\n" +
            "829\tRuchbrot\t\tBrote\t234\t9.5\t44.3\t1.2\t40\n"
        val products = "7613032779566\tChocapic\tNestlé\tMigros\t384\t8.5\t73.8\t4.4\t30\t375\n"
        val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(products.toByteArray()) } }
        val catalog = FoodCatalog({ name ->
            when (name) {
                FoodCatalog.BLV_FILE -> ByteArrayInputStream(blv.toByteArray())
                FoodCatalog.OFF_FILE -> ByteArrayInputStream(gz.toByteArray())
                else -> throw FileNotFoundException(name)
            }
        })
        return AddFoodViewModel(nutrition, mensaClient, MensaSettings(context), lookup = { lookups += it; answer(it) }, catalog = catalog, scope = scope)
    }

    @Test
    fun barcode_inDerProduktliste_ohneInternet() {
        answer = { throw IOException("Keine Verbindung") }
        val outcome = runBlocking { withCatalog().resolveBarcode("7613032779566") }

        val food = (outcome as BarcodeOutcome.Found).food
        assertEquals("Chocapic", food.name)
        assertEquals(FoodSource.OFF, food.source)
        assertEquals("7613032779566", food.barcode)
        assertTrue("Ohne Anfrage ins Internet", lookups.isEmpty())
    }

    @Test
    fun suche_zeigtTrefferAusDerTabelle() = runBlocking {
        val vm = withCatalog()
        vm.searchCatalog("Ruchbrot")
        val found = withTimeout(5_000) { vm.uiState.first { it.catalog.generic.isNotEmpty() } }
        assertEquals("Ruchbrot", found.catalog.generic.single().name)
        assertEquals("Ruchbrot", found.catalog.query)

        // Ein leeres Feld leert die Treffer sofort, ohne Wartezeit.
        vm.searchCatalog("")
        val cleared = withTimeout(5_000) { vm.uiState.first { it.catalog.query.isEmpty() } }
        assertTrue(cleared.catalog.isEmpty)
    }

    @Test
    fun ausDerTabelleEintragen_speichertLebensmittelUndVerweistDarauf() = runBlocking {
        val vm = withCatalog()
        val egg = Food(name = "Hühnerei, ganz, roh", source = FoodSource.BLV, kcal = 140.0, proteinG = 12.6, portionG = 55.0)
        val entry = FoodEntry(date = monday, meal = MealSlot.BREAKFAST, name = egg.name, kcal = 154.0, amount = 2.0)

        vm.logCatalogFood(egg, entry)

        val day = withTimeout(5_000) { nutrition.day(monday).first { it.isNotEmpty() } }
        val saved = nutrition.foods.first().single()
        assertEquals(FoodSource.BLV, saved.source)
        assertEquals(55.0, saved.portionG!!, 1e-9)
        assertEquals(saved.id, day.single().foodId)
        assertEquals(154.0, day.single().kcal, 1e-9)
    }
}
