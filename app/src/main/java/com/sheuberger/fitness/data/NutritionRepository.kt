package com.sheuberger.fitness.data

import androidx.room.withTransaction
import com.sheuberger.fitness.mensa.Meal
import com.sheuberger.fitness.nutrition.MensaEstimate
import com.sheuberger.fitness.nutrition.PortionValues
import com.sheuberger.fitness.nutrition.Totals
import com.sheuberger.fitness.nutrition.mensaPortion
import com.sheuberger.fitness.nutrition.toEntry
import com.sheuberger.fitness.nutrition.toTemplateItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** Eine Vorlage mit ihren Teilen. */
data class TemplateWithItems(val template: MealTemplate, val items: List<MealTemplateItem>) {
    val totals: Totals
        get() = items.fold(Totals()) { sum, i -> sum + Totals(i.kcal, i.proteinG ?: 0.0, i.carbsG ?: 0.0, i.fatG ?: 0.0) }
}

/**
 * Datenzugriff für den Kalorien-Tracker, wie [FitnessRepository] für die Trainingsdaten.
 * Beide teilen sich dieselbe Datenbankdatei `fitness.db`.
 */
class NutritionRepository(private val db: AppDatabase) {
    private val foodDao = db.foodDao()
    private val entryDao = db.foodEntryDao()
    private val templateDao = db.mealTemplateDao()

    val foods: Flow<List<Food>> = foodDao.observeAll()
    val recentEntries: Flow<List<FoodEntry>> = entryDao.observeRecent(RECENT_LIMIT)
    val recentMensa: Flow<List<FoodEntry>> = entryDao.observeRecentMensa(RECENT_MENSA_LIMIT)
    val templates: Flow<List<TemplateWithItems>> =
        combine(templateDao.observeAll(), templateDao.observeAllItems()) { templates, items ->
            val byTemplate = items.groupBy { it.templateId }
            templates.map { TemplateWithItems(it, byTemplate[it.id].orEmpty()) }
        }

    fun day(date: LocalDate): Flow<List<FoodEntry>> = entryDao.observeDay(date)

    fun dailyTotals(from: LocalDate, to: LocalDate): Flow<List<DayTotal>> = entryDao.observeDailyTotals(from, to)

    // --- Einträge ---

    suspend fun log(entry: FoodEntry): Long = entryDao.insert(entry)

    suspend fun updateEntry(entry: FoodEntry) = entryDao.update(entry)

    suspend fun deleteEntry(entry: FoodEntry) = entryDao.delete(entry)

    /** Kopiert alle Einträge von [from] nach [to], z. B. "gestern übernehmen". Gibt die Anzahl zurück. */
    suspend fun copyDay(from: LocalDate, to: LocalDate, now: Long = System.currentTimeMillis()): Int =
        db.withTransaction {
            val source = entryDao.getDay(from)
            entryDao.insertAll(source.mapIndexed { i, e -> e.copy(id = 0, date = to, loggedAt = now + i) })
            source.size
        }

    // --- Lebensmittel ---

    suspend fun food(id: Long): Food? = foodDao.get(id)

    suspend fun foodByBarcode(barcode: String): Food? = foodDao.findByBarcode(barcode)

    /**
     * Speichert ein Lebensmittel und gibt seine ID zurück. Jeder Barcode gehört zu höchstens einem
     * Lebensmittel: Ein neues mit bekanntem Barcode aktualisiert das vorhandene, statt ein zweites anzulegen.
     */
    suspend fun saveFood(food: Food): Long = db.withTransaction {
        val sameCode = food.barcode?.let { foodDao.findByBarcode(it) }
        when {
            food.id == 0L && sameCode != null -> {
                foodDao.update(food.copy(id = sameCode.id, favorite = food.favorite || sameCode.favorite))
                sameCode.id
            }
            food.id == 0L -> foodDao.insert(food)
            else -> {
                // Bearbeitest du ein Lebensmittel und gibst ihm einen Barcode, den ein anderes schon hat,
                // gehört der Barcode danach diesem hier.
                if (sameCode != null && sameCode.id != food.id) foodDao.update(sameCode.copy(barcode = null))
                foodDao.update(food)
                food.id
            }
        }
    }

    /** Löscht ein Lebensmittel. Einträge damit bleiben im Tagebuch, sie verlieren nur den Verweis. */
    suspend fun deleteFood(food: Food) = foodDao.delete(food)

    // --- Mensa ---

    suspend fun lastMensaEntry(name: String): FoodEntry? = entryDao.lastMensaEntry(name)

    /** Startwerte einer Portion: letzter eigener Eintrag, sonst Angaben der Mensa, sonst Schätzung. */
    suspend fun mensaPortion(meal: Meal): PortionValues =
        mensaPortion(meal, entryDao.lastMensaEntry(meal.name)) { MensaEstimate.guess(meal.name, meal.description) }

    // --- Vorlagen ---

    /** Speichert [entries] als Vorlage, z. B. das heutige Frühstück. */
    suspend fun saveTemplate(name: String, meal: MealSlot?, entries: List<FoodEntry>): Long = db.withTransaction {
        val id = templateDao.insert(MealTemplate(name = name.trim(), meal = meal))
        templateDao.insertItems(entries.mapIndexed { i, e -> e.toTemplateItem(id, i) })
        id
    }

    /** Legt alle Teile der Vorlage als Einträge an [date] in [slot] an. Gibt die Anzahl zurück. */
    suspend fun applyTemplate(
        templateId: Long,
        date: LocalDate,
        slot: MealSlot,
        now: Long = System.currentTimeMillis(),
    ): Int = db.withTransaction {
        val items = templateDao.items(templateId)
        entryDao.insertAll(items.mapIndexed { i, item -> item.toEntry(date, slot, now + i) })
        items.size
    }

    suspend fun renameTemplate(template: MealTemplate, name: String) = templateDao.update(template.copy(name = name.trim()))

    suspend fun deleteTemplate(template: MealTemplate) = templateDao.delete(template)

    private companion object {
        const val RECENT_LIMIT = 30
        const val RECENT_MENSA_LIMIT = 10
    }
}
