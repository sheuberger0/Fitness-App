package com.sheuberger.fitness.nutrition

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Die Nährwert-Tabellen so, wie die App sie bekommt: über den AssetManager, nach dem Bauen.
 *
 * [FoodCatalogTest] liest die Dateien direkt aus dem Ordner `src/main/assets`. Das reicht nicht: Beim Bauen kann
 * sich eine Datei ändern. So packte das Android-Gradle-Plugin die Produktliste aus, als sie noch auf ".gz" endete,
 * und benannte sie um. Die App fand sie nicht mehr, und die Liste "Produkte" blieb leer, ohne Fehlermeldung.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodCatalogAssetsTest {
    private val assets = ApplicationProvider.getApplicationContext<Context>().assets
    private val catalog = FoodCatalog(open = assets::open)

    @Test
    fun dateienSindInDerAppUnterIhremNamen() {
        val files = assets.list("naehrwerte").orEmpty().map { "naehrwerte/$it" }
        assertTrue(files.toString(), FoodCatalog.BLV_FILE in files)
        assertTrue(files.toString(), FoodCatalog.OFF_FILE in files)
    }

    @Test
    fun beideTabellenFindenEtwas() = runBlocking {
        val result = catalog.search("nutella")
        assertEquals("Haselnuss-Schokolade-Brotaufstrich", result.generic.first().name)
        assertTrue("keine Produkte gefunden", result.products.any { it.name == "Nutella" })
        assertEquals("Nutella", catalog.byBarcode("3017620425035")?.name)
    }
}
