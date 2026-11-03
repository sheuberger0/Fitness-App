package com.sheuberger.fitness.mensa

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.time.LocalDate

/**
 * Liest die Menüs der UZH-Mensen. Die betreibt die ZFV, und die Menüpläne liegen auf app.food2050.ch.
 * Eine offene Schnittstelle ohne API-Schlüssel gibt es nicht, deshalb liest die App die Webseiten selbst:
 *
 * 1. Die Wochenseite einer Mensa verlinkt jedes Gericht als eigene Seite, z. B.
 *    `…/obere-mensa/lunch,menu-2/2026-11-03`. Daraus nehmen wir die Links für den gewünschten Tag.
 * 2. Jede Gerichtsseite ist eine Next.js-Seite. Ihre Daten stecken als JSON in
 *    `self.__next_f.push([1,"…"])`-Skripten (das "RSC-Payload"). Darin steht ein Objekt `{"menuItem":…}`
 *    mit Name, Preisen und Nährwerten pro Portion.
 */
object UzhMenuParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** Alle Gerichts-Links der Wochenseite für [date], ohne Doppelte. */
    fun dishLinks(weeklyHtml: String, date: LocalDate): List<String> {
        val pattern = Regex("""https://app\.food2050\.ch/[^"'\s<>?]+/$date(?=["'])""")
        return pattern.findAll(weeklyHtml).map { it.value }.distinct().toList()
    }

    /** Liest ein Gericht aus der HTML-Seite. null, wenn die Seite kein Gericht enthält. */
    fun parseDish(html: String, link: String, mensa: Mensa, time: MealTime): Meal? {
        val payload = rscPayload(html)
        val start = payload.indexOf("{\"menuItem\":")
        if (start < 0) return null
        val objectText = balancedObject(payload, start) ?: return null
        val item = json.parseToJsonElement(objectText).obj?.get("menuItem")?.obj ?: return null
        val dish = item["dish"]?.obj ?: return null
        val name = dish.str("name")?.trim().orEmpty()
        if (name.isEmpty()) return null

        val prices = buildMap {
            for (p in item.arr("prices").mapNotNull { it.obj }) {
                // 0.00 steht da, wenn der Preis noch fehlt (z. B. "Pasta, jeden Tag eine neue Kreation").
                val amount = p.double("amount")?.takeIf { it > 0 } ?: continue
                val category = p["priceCategory"]?.obj?.str("name")?.lowercase().orEmpty()
                val group = when {
                    category.startsWith("stud") -> PriceGroup.Students
                    category.startsWith("mitarb") || category.startsWith("angest") -> PriceGroup.Staff
                    category.startsWith("ext") || category.startsWith("gäst") || category.startsWith("gast") ->
                        PriceGroup.External
                    else -> null
                } ?: continue
                put(group, amount)
            }
        }

        // Der letzte Pfadteil vor dem Datum ist die Ausgabe: "lunch,menu-2" → "Menu 2",
        // "mittagsverpflegung,hauptspeisen,butcher" → "Butcher".
        val categoryPath = link.substringBeforeLast('/').substringAfterLast('/')
        val parts = categoryPath.split(',')
        val line = parts.last().replace('-', ' ').replaceFirstChar { it.titlecase() }
        val isSide = parts.any { it.startsWith("nebe") || it.contains("dessert") || it.contains("suppe") }

        val vegan = dish.bool("isVegan") == true
        return Meal(
            mensa = mensa,
            time = time,
            line = line,
            name = name.calmCase(),
            description = dish.str("description")?.trim().orEmpty(),
            prices = prices,
            nutrition = nutrition(dish["stats"]?.obj),
            vegetarian = vegan || dish.bool("isVegetarian") == true,
            vegan = vegan,
            isSide = isSide,
        )
    }

    private fun nutrition(stats: JsonObject?): Nutrition? {
        stats ?: return null
        fun amount(key: String) = stats[key]?.obj?.double("amount")
        val kcal = amount("energy")?.takeIf { it > 0 } ?: return null
        val protein = amount("protein") ?: return null
        return Nutrition(
            kcal = kcal,
            proteinG = protein,
            carbsG = amount("carbohydrates"),
            fatG = amount("fat"),
            weightG = stats["servingWeight"]?.obj?.double("amount"),
            estimated = false,
        )
    }

    /** Hängt die Texte aller `self.__next_f.push([1,"…"])`-Aufrufe zusammen (JSON-Strings, also entschlüsseln). */
    internal fun rscPayload(html: String): String {
        // Von Hand statt mit einem regulären Ausdruck: Ein Ausdruck wie ("(?:[^"\\]|\\.)*") arbeitet in Java
        // rekursiv und läuft bei den 30 KB langen Texten in einen StackOverflowError.
        val marker = "self.__next_f.push([1,\""
        val out = StringBuilder()
        var from = 0
        while (true) {
            val start = html.indexOf(marker, from)
            if (start < 0) break
            val open = start + marker.length - 1 // Position des öffnenden Anführungszeichens
            val close = stringEnd(html, open) ?: break
            out.append((json.parseToJsonElement(html.substring(open, close + 1)) as JsonPrimitive).content)
            from = close + 1
        }
        return out.toString()
    }

    /** Index des schließenden Anführungszeichens zum JSON-String, der bei [open] beginnt. */
    private fun stringEnd(text: String, open: Int): Int? {
        var i = open + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i++ // nächstes Zeichen ist maskiert
                '"' -> return i
            }
            i++
        }
        return null
    }

    /** Schneidet ab [start] ein vollständiges JSON-Objekt heraus: zählt Klammern, außer innerhalb von Strings. */
    internal fun balancedObject(text: String, start: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> if (--depth == 0) return text.substring(start, i + 1)
            }
        }
        return null
    }
}

private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
