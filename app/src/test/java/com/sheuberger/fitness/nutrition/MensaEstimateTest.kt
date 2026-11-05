package com.sheuberger.fitness.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prüft die Schätzung für Mensa-Gerichte ohne Nährwertangaben (Stichworte im Namen). */
class MensaEstimateTest {

    private fun kcal(name: String, description: String = "") = MensaEstimate.guess(name, description).kcal

    @Test
    fun stichworteBestimmenDieKalorien() {
        assertEquals(1000.0, kcal("Wienerschnitzel"), 0.0)
        assertEquals(1000.0, kcal("Cheeseburger mit Coleslaw"), 0.0)
        assertEquals(1000.0, kcal("Cordon bleu"), 0.0)
        assertEquals(1000.0, kcal("Schnipo"), 0.0)
        assertEquals(800.0, kcal("Spaghetti Bolognese"), 0.0)
        assertEquals(800.0, kcal("Eiernudeln mit Rahmsauce"), 0.0)
        assertEquals(800.0, kcal("Älplermagronen"), 0.0)
        assertEquals(800.0, kcal("Älpler Magronen mit Apfelmus"), 0.0)
        assertEquals(750.0, kcal("Thai Curry mit Jasminreis"), 0.0)
        assertEquals(750.0, kcal("Pilzrisotto"), 0.0)
        assertEquals(650.0, kcal("Vegane Buddha Bowl"), 0.0)
        assertEquals(400.0, kcal("Grüner Salat"), 0.0)
        assertEquals(400.0, kcal("Salätli"), 0.0)
        assertEquals(250.0, kcal("Tomatensuppe"), 0.0)
        assertEquals(250.0, kcal("Süppli des Tages"), 0.0)
    }

    @Test
    fun ersterTrefferZaehlt_schwereGerichteZuerst() {
        // "Burger" kommt vor "vegan", "Pommes" vor "Curry", "Pasta" vor "Salat".
        assertEquals(1000.0, kcal("Vegan Burger"), 0.0)
        assertEquals(1000.0, kcal("Currywurst mit Pommes"), 0.0)
        assertEquals(800.0, kcal("Pastasalat"), 0.0)
    }

    @Test
    fun ohneStichwort_einDurchschnittlichesHauptgericht() {
        assertEquals(MensaEstimate.DEFAULT_KCAL, kcal("Poulet an Zitronensauce mit Reis"), 0.0)
        assertEquals(700.0, kcal(""), 0.0)
    }

    @Test
    fun dessertKommtDazu_imNamenOderInDerBeschreibung() {
        assertEquals(800.0 + MensaEstimate.DESSERT_EXTRA, kcal("Lasagne", "Rindshackfleisch, Bechamel, Dessert"), 0.0)
        assertEquals(700.0 + 250.0, kcal("Menü mit Nachspeise"), 0.0)
        assertEquals(250.0 + 250.0, kcal("Tagessuppe", "mit Nachtisch"), 0.0)
        // Die Beschreibung allein bestimmt nicht die Art des Gerichts: "Pommes" in den Zutaten zählt nicht.
        assertEquals(700.0, kcal("Poulet", "Pommes frites"), 0.0)
    }

    @Test
    fun grossUndKleinschreibungSpieltKeineRolle() {
        // Die UZH schreibt Gerichte manchmal ganz in Großbuchstaben.
        assertEquals(800.0, kcal("SPAGHETTI CARBONARA"), 0.0)
        assertEquals(800.0, kcal("ÄLPLERMAGRONEN"), 0.0)
        assertEquals(1000.0, kcal("PANIERTES SCHNITZEL"), 0.0)
        assertEquals(400.0, kcal("SALÄTLI"), 0.0)
        assertEquals(950.0, kcal("POULET", "MIT DESSERT"), 0.0)
    }

    @Test
    fun schaetzungIstAlsSolcheMarkiert() {
        val guess = MensaEstimate.guess("Poulet", "mit Reis")
        assertTrue(guess.estimated)
        assertEquals(PortionOrigin.GUESS, guess.origin)
        // Aus dem Namen lassen sich keine Makros ablesen.
        assertNull(guess.proteinG)
        assertNull(guess.carbsG)
        assertNull(guess.fatG)
    }
}
