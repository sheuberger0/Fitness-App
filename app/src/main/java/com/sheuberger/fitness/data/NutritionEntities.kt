package com.sheuberger.fitness.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

// Datenmodell für den Kalorien-Tracker (Datenbankversion 2).
// Enums speichert Room als Text ("LUNCH"). Das bleibt in der Datei lesbar und funktioniert auch dann noch,
// wenn sich die Reihenfolge der Einträge im Code ändert.

/** Worauf sich die Nährwerte eines Lebensmittels beziehen. */
enum class FoodBasis(val label: String) {
    PER_100G("pro 100 g"),
    PER_PORTION("pro Portion"),
}

/** Woher die Nährwerte eines Lebensmittels stammen. */
enum class FoodSource { MANUAL, OFF }

/** Die Mahlzeiten der Tagesansicht. */
enum class MealSlot(val label: String) {
    BREAKFAST("Frühstück"),
    LUNCH("Mittag"),
    DINNER("Abend"),
    SNACK("Snacks"),
}

/** Einheit der Menge eines Eintrags. */
enum class EntryUnit(val label: String) {
    GRAM("g"),
    PORTION("Portion"),
}

/** Wie ein Eintrag entstanden ist. */
enum class EntrySource { QUICK, FOOD, MENSA }

/**
 * Ein Lebensmittel aus "Meine Lebensmittel": selbst angelegt oder per Barcode von Open Food Facts geholt.
 * Die Nährwerte gelten pro [basis], also pro 100 g oder pro Portion.
 */
@Entity(
    tableName = "foods",
    indices = [
        // SQLite erlaubt mehrere NULL-Werte in einem eindeutigen Index: Lebensmittel ohne Barcode gehen also.
        Index(value = ["barcode"], unique = true),
        Index("name"),
    ],
)
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val source: FoodSource = FoodSource.MANUAL,
    val basis: FoodBasis = FoodBasis.PER_100G,
    val kcal: Double,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    /** Gramm einer Portion, z. B. 30 g Haferflocken. Erlaubt Eingaben in Portionen und in Gramm. */
    val portionG: Double? = null,
    /** Gramm einer ganzen Packung, für "½ Packung". */
    val packageG: Double? = null,
    val favorite: Boolean = false,
    /** Von dir korrigiert: Ein Neuladen von Open Food Facts überschreibt dieses Lebensmittel nicht. */
    val edited: Boolean = false,
    /** Wann die Daten von Open Food Facts geladen wurden (Millisekunden). */
    val fetchedAt: Long? = null,
)

/**
 * Ein Eintrag im Ernährungstagebuch: etwas, das du an [date] gegessen hast.
 *
 * Die Nährwerte sind eine Kopie der Summen dieses Eintrags. Mensa-Gerichte werden nicht gespeichert,
 * und wenn du ein Lebensmittel später korrigierst, sollen vergangene Tage gleich bleiben. [foodId] ist
 * nur ein Verweis. Wird das Lebensmittel gelöscht, wird er leer (SET NULL), der Eintrag bleibt.
 */
@Entity(
    tableName = "food_entries",
    foreignKeys = [
        ForeignKey(
            entity = Food::class,
            parentColumns = ["id"],
            childColumns = ["foodId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("date"), Index("foodId"), Index("name")],
)
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    /** Zeitpunkt des Eintragens (Millisekunden), ordnet die Einträge innerhalb eines Tages. */
    val loggedAt: Long = System.currentTimeMillis(),
    val meal: MealSlot,
    val name: String,
    val kcal: Double,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val amount: Double = 1.0,
    val unit: EntryUnit = EntryUnit.PORTION,
    val source: EntrySource = EntrySource.QUICK,
    val foodId: Long? = null,
    /** Bei Mensa-Gerichten: Mensa und Ausgabe, z. B. "Polymensa" und "Street". */
    val mensa: String? = null,
    val mensaLine: String? = null,
    /** Geschätzte oder berechnete Werte, in der App mit "≈" markiert. */
    val estimated: Boolean = false,
)

/** Eine Vorlage, z. B. "Frühstück Standard", die mehrere Einträge auf einmal anlegt. */
@Entity(tableName = "meal_templates")
data class MealTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Die Mahlzeit, aus der die Vorlage gespeichert wurde. Beim Anwenden nur ein Vorschlag. */
    val meal: MealSlot? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Ein Teil einer Vorlage. Wie [FoodEntry] eine Kopie der Werte, damit die Vorlage unabhängig bleibt. */
@Entity(
    tableName = "meal_template_items",
    foreignKeys = [
        ForeignKey(
            entity = MealTemplate::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Food::class,
            parentColumns = ["id"],
            childColumns = ["foodId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("templateId"), Index("foodId")],
)
data class MealTemplateItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val position: Int,
    val name: String,
    val kcal: Double,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val amount: Double = 1.0,
    val unit: EntryUnit = EntryUnit.PORTION,
    val source: EntrySource = EntrySource.QUICK,
    val foodId: Long? = null,
    val mensa: String? = null,
    val mensaLine: String? = null,
    val estimated: Boolean = false,
)

/** Tagessumme aus der Datenbank, z. B. für Durchschnitte. */
data class DayTotal(
    val date: LocalDate,
    val kcal: Double,
    val proteinG: Double?,
)
