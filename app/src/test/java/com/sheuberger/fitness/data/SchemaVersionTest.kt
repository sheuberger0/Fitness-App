package com.sheuberger.fitness.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Passt auf, dass eine Änderung am Datenmodell nicht ohne neue Datenbankversion ausgeliefert wird.
 *
 * Room schreibt beim Bauen für jede Version eine Schema-Datei nach `app/schemas`. Ihr `identityHash`
 * ist ein Fingerabdruck aller Tabellen und Spalten. Ändert jemand eine Entity, ohne [AppDatabase.VERSION]
 * zu erhöhen, überschreibt Room die Datei der alten Version mit einem neuen Hash. Auf dem Handy würde
 * Room die alte Datenbank dann nicht mehr öffnen, und die App stürzt beim Start ab.
 */
class SchemaVersionTest {

    /** Fingerabdrücke aller ausgelieferten Schema-Versionen. Bei jeder neuen Version hier ergänzen. */
    private val releasedSchemas = mapOf(
        1 to "cf393a7711f74144f794b354d8c2db4f", // App 1.0 bis 1.3
    )

    private val schemaDir = File("schemas/${AppDatabase::class.java.name}")

    @Test
    fun releasedSchemasAreUnchanged() {
        releasedSchemas.forEach { (version, expectedHash) ->
            assertEquals(
                "Das Schema von Datenbankversion $version hat sich geändert. Ausgelieferte Versionen dürfen " +
                    "sich nicht mehr ändern: AppDatabase.VERSION erhöhen und eine Migration schreiben.",
                expectedHash,
                identityHash(version),
            )
        }
    }

    @Test
    fun everyVersionHasCommittedSchema() {
        for (version in 1..AppDatabase.VERSION) {
            assertTrue(
                "Schema-Datei für Version $version fehlt in app/schemas. Ohne sie lässt sich die Migration nicht testen.",
                File(schemaDir, "$version.json").exists(),
            )
        }
    }

    private fun identityHash(version: Int): String {
        val json = File(schemaDir, "$version.json").readText()
        return Regex("\"identityHash\"\\s*:\\s*\"([0-9a-f]+)\"").find(json)!!.groupValues[1]
    }
}
