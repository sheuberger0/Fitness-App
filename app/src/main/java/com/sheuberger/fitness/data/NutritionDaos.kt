package com.sheuberger.fitness.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

// Datenbankzugriffe für den Kalorien-Tracker. Alle Abfragen haben Parameter (":date"), nie zusammengesetzte
// Strings. Room setzt die Werte sicher ein, so kann ein Name wie "'; DROP TABLE" nichts anrichten.

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods ORDER BY favorite DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Food>>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun get(id: Long): Food?

    @Query("SELECT * FROM foods WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): Food?

    @Insert
    suspend fun insert(food: Food): Long

    @Update
    suspend fun update(food: Food)

    @Delete
    suspend fun delete(food: Food)
}

@Dao
interface FoodEntryDao {
    @Query("SELECT * FROM food_entries WHERE date = :date ORDER BY loggedAt, id")
    fun observeDay(date: LocalDate): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food_entries WHERE date = :date ORDER BY loggedAt, id")
    suspend fun getDay(date: LocalDate): List<FoodEntry>

    /** Tagessummen für einen Zeitraum. Tage ohne Einträge fehlen in der Liste. */
    @Query(
        """
        SELECT date, SUM(kcal) AS kcal, SUM(proteinG) AS proteinG FROM food_entries
        WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date
        """
    )
    fun observeDailyTotals(from: LocalDate, to: LocalDate): Flow<List<DayTotal>>

    /** Der jeweils letzte Eintrag pro Name, neueste zuerst: die Liste "Zuletzt". */
    @Query(
        """
        SELECT * FROM food_entries WHERE id IN (
            SELECT MAX(id) FROM food_entries GROUP BY name COLLATE NOCASE
        ) ORDER BY loggedAt DESC, id DESC LIMIT :limit
        """
    )
    fun observeRecent(limit: Int): Flow<List<FoodEntry>>

    /** Wie [observeRecent], nur Mensa-Gerichte: "Zuletzt in der Mensa", funktioniert auch offline. */
    @Query(
        """
        SELECT * FROM food_entries WHERE id IN (
            SELECT MAX(id) FROM food_entries WHERE source = 'MENSA' GROUP BY name COLLATE NOCASE
        ) ORDER BY loggedAt DESC, id DESC LIMIT :limit
        """
    )
    fun observeRecentMensa(limit: Int): Flow<List<FoodEntry>>

    /** Dein letzter Eintrag dieses Mensa-Gerichts. Damit merkt sich die App z. B. eine größere Portion. */
    @Query(
        """
        SELECT * FROM food_entries WHERE source = 'MENSA' AND name = :name COLLATE NOCASE
        ORDER BY loggedAt DESC, id DESC LIMIT 1
        """
    )
    suspend fun lastMensaEntry(name: String): FoodEntry?

    @Insert
    suspend fun insert(entry: FoodEntry): Long

    @Insert
    suspend fun insertAll(entries: List<FoodEntry>)

    @Update
    suspend fun update(entry: FoodEntry)

    @Delete
    suspend fun delete(entry: FoodEntry)
}

@Dao
interface MealTemplateDao {
    @Query("SELECT * FROM meal_templates ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<MealTemplate>>

    @Query("SELECT * FROM meal_template_items ORDER BY templateId, position")
    fun observeAllItems(): Flow<List<MealTemplateItem>>

    @Query("SELECT * FROM meal_template_items WHERE templateId = :templateId ORDER BY position")
    suspend fun items(templateId: Long): List<MealTemplateItem>

    @Insert
    suspend fun insert(template: MealTemplate): Long

    @Insert
    suspend fun insertItems(items: List<MealTemplateItem>)

    @Update
    suspend fun update(template: MealTemplate)

    @Delete
    suspend fun delete(template: MealTemplate)
}
