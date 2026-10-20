# Fitness

Eine Android-App zum Tracken von Trainingsgewichten und Körpergewicht. Alles bleibt lokal auf dem Handy,
es gibt kein Konto, keine Werbung und keine Internetverbindung.

## Was die App kann

- **Workouts**: Trainingspläne wie „Push“ oder „Oberkörper A“ anlegen und Übungen hinzufügen, entfernen
  und sortieren. Mit dem „+“ neben einer Übung trägst du direkt nach dem Satz das Gewicht ein.
- **Übungen**: Eine Bibliothek mit 51 gängigen Übungen, viele davon an Maschinen und am Kabelzug,
  nach Muskelgruppe sortiert und durchsuchbar.
  Eigene Übungen kannst du jederzeit anlegen.
- **Fortschritt pro Übung**: Jeder Eintrag speichert Datum, Gewicht und optional die Wiederholungen.
  Der Graph zeigt pro Tag dein schwerstes Gewicht über 1 Monat, 3 Monate, 6 Monate, 1 Jahr oder alles.
  Fährst du mit dem Finger über den Graphen, siehst du den Wert an jedem Tag. Bestwerte werden als „PR“
  markiert.
- **Übungsbilder**: Für Übungen mit Bild zeigt die Übungsansicht oben eine Abbildung mit den trainierten Muskeln in Rot,
  passend zum hellen und dunklen Design. Wie neue Bilder dazukommen, steht in
  [`tools/uebungsbilder/README.md`](tools/uebungsbilder/README.md).
- **Körpergewicht**: Ein Wert pro Tag, mit Graph, Tiefst- und Höchstwert sowie Durchschnitt.
- Helles und dunkles Design, je nach Systemeinstellung.

## App aufs Handy bringen

Du brauchst [Android Studio](https://developer.android.com/studio) (kostenlos).

1. Android Studio öffnen, **File › Open** wählen und den Ordner dieses Repositorys auswählen.
   Beim ersten Öffnen lädt Android Studio Gradle und alle Bibliotheken herunter, das dauert ein paar Minuten.
2. Am Handy die **Entwickleroptionen** aktivieren: *Einstellungen › Über das Telefon* und siebenmal auf
   *Build-Nummer* tippen. Danach in den Entwickleroptionen **USB-Debugging** einschalten.
3. Handy per USB anschließen und am Handy „USB-Debugging zulassen“ bestätigen.
4. In Android Studio oben dein Handy auswählen und auf den grünen **Run**-Knopf (▶) drücken.
   Die App wird installiert und startet.

Ohne Handy geht es auch mit dem Emulator: *Tools › Device Manager › Create Virtual Device*.

Alternativ auf der Kommandozeile (Android SDK muss installiert sein):

```bash
./gradlew assembleDebug        # baut app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # installiert sie auf dem angeschlossenen Handy
```

### Fertige APK direkt installieren

Ohne Android Studio geht es auch mit einer fertigen APK-Datei. Die aktuelle Version liegt in
[`apk/Fitness-1.6.apk`](apk/Fitness-1.6.apk). Ist eine ältere Version (ab 1.1) schon installiert, wird sie
einfach aktualisiert, deine Einträge bleiben erhalten.

1. Die APK aufs Handy laden, z. B. per Download oder per Kabel kopieren.
2. Die Datei antippen. Android fragt beim ersten Mal, ob die App (z. B. Chrome oder „Dateien“)
   **unbekannte Apps installieren** darf. Das erlauben und zurückgehen.
3. **Installieren** tippen. Play Protect warnt eventuell, weil die App nicht aus dem Play Store kommt;
   dort „Trotzdem installieren“ wählen.

Die Release-APK baust du mit `./gradlew assembleRelease` (liegt danach in `app/build/outputs/apk/release/`).
Signiert wird sie mit dem Schlüssel aus `keystore.properties` (gehört nicht ins Git). Updates lassen sich nur
über eine App mit demselben Schlüssel installieren, deshalb den Schlüssel gut aufheben. Fehlt die Datei oder
zeigt sie auf einen anderen Schlüssel, bricht der Release-Build ab.

## Deine Daten bei Updates

Alle Einträge liegen in der Datenbankdatei `fitness.db` im privaten Ordner der App. Ein Update tauscht nur den
Code aus, die Datei bleibt liegen. Verloren gehen können die Daten nur auf zwei Wegen, und gegen beide ist
das Projekt abgesichert:

- **Anderer Signaturschlüssel**: Android installiert ein Update nur, wenn es mit demselben Schlüssel signiert ist.
  Sonst bleibt nur Deinstallieren, und das löscht die Daten. Der Release-Build prüft deshalb den Fingerabdruck
  des Schlüssels und bricht bei einem falschen oder fehlenden Schlüssel ab.
- **Geändertes Datenmodell ohne Migration**: Bekommt eine Tabelle z. B. eine neue Spalte, muss die alte Datei auf
  dem Handy umgebaut werden. Wie das geht, steht oben in `AppDatabase.kt`. Die Tests in `app/src/test` spielen ein
  Update mit einer alten Datenbank voller Einträge durch und schlagen fehl, wenn eine Version oder Migration fehlt:

  ```bash
  ./gradlew testDebugUnitTest
  ```

Neue Standardübungen in `MuscleGroups.kt` kommen bei einem Update automatisch dazu. Übungen, die du gelöscht hast,
kommen nicht zurück, und deine eigenen Einträge bleiben unberührt. Dafür muss die Datenbankversion nicht erhöht
werden.

Zusätzlich sichert Android die Datenbank im Google-Backup und überträgt sie beim Umzug auf ein neues Handy.

## Wie der Code aufgebaut ist

Die App folgt der Architektur, die Google für Android empfiehlt: **Daten → Repository → ViewModel → Oberfläche**.
Jede Schicht kennt nur die darunterliegende.

```
app/src/main/java/com/sheuberger/fitness/
├── FitnessApplication.kt      Erzeugt Datenbank und Repository einmal beim App-Start
├── MainActivity.kt            Einstiegspunkt, startet die Compose-Oberfläche
├── data/                      Datenschicht (Room = SQLite-Datenbank)
│   ├── Entities.kt            Tabellen: Übung, Workout, Workout↔Übung, Gewichtseintrag, Körpergewicht
│   ├── Daos.kt                SQL-Abfragen; liefern Flows, die sich bei Änderungen selbst aktualisieren
│   ├── AppDatabase.kt         Die Datenbank mit Versionsnummer und Migrationen, legt Standardübungen an
│   ├── Converters.kt          Speichert Datumswerte als Zahl
│   ├── MuscleGroups.kt        Muskelgruppen und Standardübungen
│   └── FitnessRepository.kt   Einzige Schnittstelle der Oberfläche zu den Daten
└── ui/
    ├── FitnessAppRoot.kt      Navigation: drei Tabs unten plus Detailseiten
    ├── AppViewModelProvider.kt Erzeugt die ViewModels und gibt ihnen das Repository
    ├── workouts/              Workout-Liste und Workout-Detail
    ├── exercises/             Übungsbibliothek und Übungsdetail mit Graph
    ├── bodyweight/            Körpergewicht mit Graph
    ├── components/            Wiederverwendbare Bausteine: Liniengraph, Eingabe-Sheet, Formatierung …
    └── theme/                 Farben, Schriften, helles und dunkles Theme
```

Ein paar Begriffe, die dir im Code begegnen:

- **Jetpack Compose**: Die Oberfläche wird in Kotlin beschrieben statt in XML. Eine `@Composable`-Funktion
  sagt, *wie* der Bildschirm für einen bestimmten Zustand aussieht; ändert sich der Zustand, zeichnet
  Compose automatisch neu.
- **Room**: Eine Bibliothek über SQLite. Man beschreibt Tabellen als Kotlin-Klassen (`@Entity`) und
  Abfragen als Funktionen (`@Query`), Room erzeugt den Rest beim Bauen.
- **Flow**: Ein Datenstrom. Die Abfragen liefern bei jeder Änderung der Tabelle neue Werte, deshalb muss
  nach dem Speichern nichts manuell neu geladen werden.
- **ViewModel**: Hält den Zustand eines Bildschirms (`UiState`) und überlebt z. B. das Drehen des Handys.
  Die Oberfläche liest nur den Zustand und ruft Funktionen wie `saveLog(...)` auf.

Der Liniengraph (`ui/components/LineChart.kt`) ist selbst gezeichnet, ohne externe Bibliothek. Die x-Achse
ist eine echte Zeitachse: Liegen zwischen zwei Einträgen zwei Wochen, ist auch der Abstand im Graphen größer.

## Technik

Kotlin 2.0, Jetpack Compose mit Material 3, Room, Navigation Compose. Mindestens Android 8.0 (API 26).
