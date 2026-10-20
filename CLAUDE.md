# Fitness-App

Android-App (Kotlin, Jetpack Compose, Room), alle Daten lokal. Kommentare, Commits und Texte auf Deutsch.

## Nutzerdaten müssen jedes Update überleben

- Niemals `fallbackToDestructiveMigration()` o. Ä. verwenden, die Datenbank niemals neu anlegen oder löschen.
- Jede Änderung an einer `@Entity` (Spalte, Tabelle, Index, Typ): `AppDatabase.VERSION` erhöhen, eine
  `AutoMigration` oder `Migration` ergänzen, die neue `app/schemas/.../<n>.json` committen und den
  Fingerabdruck der ausgelieferten Version in `SchemaVersionTest.releasedSchemas` eintragen.
- Neue Standardübungen nur in `DefaultExercises` (MuscleGroups.kt) anhängen. Dafür keine neue DB-Version;
  `SeedCallback` ergänzt sie bei bestehenden Installationen. `SeededUntilVersion13` nicht ändern.
- Release-APKs immer mit dem bisherigen Schlüssel signieren (`keystore.properties`); der Build prüft den
  Fingerabdruck. Package-Name `com.sheuberger.fitnesstracker` nie ändern. Jede ausgelieferte APK erhöht `versionCode`.
- Vor jedem Release: `./gradlew testDebugUnitTest assembleRelease`.
