package com.sheuberger.fitness.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Die Datenbank der App. Sie liegt als Datei `fitness.db` im privaten App-Ordner. Ein App-Update
 * tauscht nur den Code aus, diese Datei bleibt liegen. Damit deine Einträge ein Update überleben,
 * gilt beim Ändern des Datenmodells (neue Spalte, neue Tabelle, umbenanntes Feld …):
 *
 * 1. [VERSION] um 1 erhöhen. Room erkennt die alte Datenbank auf dem Handy an ihrer Versionsnummer.
 * 2. Eine Migration ergänzen, die die alte Datei Schritt für Schritt auf die neue Version umbaut:
 *    für einfache Fälle (Tabelle/Spalte mit Standardwert hinzufügen) eine `AutoMigration` in der
 *    Annotation unten, sonst eine [Migration] in [MIGRATIONS].
 * 3. Die neu erzeugte Datei `app/schemas/.../<VERSION>.json` mit committen und `./gradlew test`
 *    laufen lassen. Die Tests prüfen, dass jede bisher ausgelieferte Version sauber migriert.
 *
 * Niemals `fallbackToDestructiveMigration()` verwenden: Das löscht bei fehlender Migration
 * still und leise alle Einträge.
 */
@Database(
    entities = [
        Exercise::class,
        Workout::class,
        WorkoutExercise::class,
        ExerciseLog::class,
        BodyWeightEntry::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun exerciseLogDao(): ExerciseLogDao
    abstract fun bodyWeightDao(): BodyWeightDao

    companion object {
        /** Aktuelle Schema-Version. Version 1 ist mit den App-Versionen 1.0 bis 1.3 ausgeliefert. */
        const val VERSION = 1

        const val FILE_NAME = "fitness.db"

        /** Handgeschriebene Migrationen, z. B. `Migration(1, 2) { db -> db.execSQL("ALTER TABLE …") }`. */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun create(context: Context): AppDatabase = build(context, FILE_NAME, DefaultExercises)

        internal fun build(
            context: Context,
            fileName: String,
            defaults: List<Pair<String, String?>>,
        ): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, fileName)
                .addMigrations(*MIGRATIONS)
                .addCallback(SeedCallback(context.applicationContext, defaults))
                .build()
    }
}

/**
 * Füllt die Übungsbibliothek mit Standardübungen. Beim allerersten Start kommen alle hinein. Bringt ein
 * späteres Update neue Standardübungen mit, werden genau diese beim nächsten Öffnen ergänzt, ohne dass
 * die Datenbank neu angelegt werden muss. Welche Übungen schon einmal angeboten wurden, steht in den
 * SharedPreferences. So kommt eine Standardübung, die du gelöscht hast, nicht wieder zurück.
 */
private class SeedCallback(
    context: Context,
    private val defaults: List<Pair<String, String?>>,
) : RoomDatabase.Callback() {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        insert(db, defaults)
        prefs.edit().putStringSet(KEY_SEEDED, defaults.map { it.first }.toSet()).apply()
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        // Installationen von vor dieser Logik haben beim ersten Start genau die Übungen aus 1.0 bis 1.3 bekommen.
        val stored = prefs.getStringSet(KEY_SEEDED, null)
        val seeded = stored ?: SeededUntilVersion13
        val new = defaults.filter { it.first !in seeded }
        if (stored != null && new.isEmpty()) return
        insert(db, new)
        prefs.edit().putStringSet(KEY_SEEDED, seeded + new.map { it.first }).apply()
    }

    private fun insert(db: SupportSQLiteDatabase, exercises: List<Pair<String, String?>>) {
        // OR IGNORE: Hast du selbst schon eine Übung mit diesem Namen angelegt, bleibt deine bestehen.
        exercises.forEach { (name, group) ->
            db.execSQL(
                "INSERT OR IGNORE INTO exercises (name, muscleGroup) VALUES (?, ?)",
                arrayOf(name, group),
            )
        }
    }

    private companion object {
        const val PREFS = "database_seed"
        const val KEY_SEEDED = "seeded_default_exercises"
    }
}

/** Die Standardübungen, die die App-Versionen 1.0 bis 1.3 beim ersten Start angelegt haben. Nicht ändern. */
private val SeededUntilVersion13 = setOf(
    "Bankdrücken", "Schrägbankdrücken", "Kurzhantel-Bankdrücken", "Butterfly (Maschine)",
    "Kreuzheben", "Klimmzüge", "Latzug", "Langhantelrudern", "Kabelrudern",
    "Schulterdrücken", "Seitheben", "Face Pulls",
    "Kniebeugen", "Beinpresse", "Rumänisches Kreuzheben", "Ausfallschritte", "Beinstrecker",
    "Beinbeuger", "Hip Thrust", "Wadenheben",
    "Bizepscurls", "Hammercurls", "Trizepsdrücken (Kabel)", "French Press", "Dips", "Kabel-Crunches",
)
