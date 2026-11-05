package com.sheuberger.fitness.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheuberger.fitness.data.Food
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.data.MealTemplate
import com.sheuberger.fitness.data.NutritionRepository
import com.sheuberger.fitness.data.TemplateWithItems
import com.sheuberger.fitness.mensa.Meal
import com.sheuberger.fitness.mensa.Mensa
import com.sheuberger.fitness.mensa.MensaClient
import com.sheuberger.fitness.mensa.MensaSettings
import com.sheuberger.fitness.nutrition.Barcode
import com.sheuberger.fitness.nutrition.CatalogResults
import com.sheuberger.fitness.nutrition.FoodCatalog
import com.sheuberger.fitness.nutrition.OffLookup
import com.sheuberger.fitness.nutrition.PortionValues
import com.sheuberger.fitness.ui.mensa.MenuLoad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Was das Sheet "Eintragen" zum Auswählen anbietet. */
data class AddFoodUiState(
    val templates: List<TemplateWithItems> = emptyList(),
    /** Alle eigenen Lebensmittel, Favoriten zuerst (so sortiert sie schon die Datenbank). */
    val foods: List<Food> = emptyList(),
    val recent: List<FoodEntry> = emptyList(),
    val recentMensa: List<FoodEntry> = emptyList(),
    /** Die Mensen aus den Mensa-Einstellungen. */
    val mensas: Set<Mensa> = Mensa.entries.toSet(),
    val menus: Map<LocalDate, MenuLoad> = emptyMap(),
    /**
     * Treffer der Suche in den eingebauten Tabellen (Nährwertdatenbank und Produkte aus Schweizer Läden). Was davon
     * schon unter "Meine Lebensmittel" steht, blendet die Liste aus (siehe [without]).
     */
    val catalog: CatalogResults = CatalogResults(),
) {
    val favorites: List<Food> get() = foods.filter { it.favorite }
}

/** Was beim Prüfen eines Barcodes herauskam. Jeder Fall führt in der Oberfläche woandershin. */
sealed interface BarcodeOutcome {
    /** Falsche Länge, Buchstaben oder falsche Prüfziffer. */
    data object Invalid : BarcodeOutcome

    /** Schon in "Meine Lebensmittel": direkt eintragen. */
    data class Known(val food: Food) : BarcodeOutcome

    /** Ladencode (beginnt mit 2): Den kennt nur das Geschäft, also Werte von Hand eintragen. */
    data class InStore(val code: String) : BarcodeOutcome

    /** Bei Open Food Facts gefunden, noch nicht gespeichert. */
    data class Found(val food: Food) : BarcodeOutcome

    data class NotFound(val code: String) : BarcodeOutcome

    /** Keine Verbindung oder Server-Fehler. */
    data class Failed(val code: String, val message: String) : BarcodeOutcome
}

/**
 * Daten und Aktionen für das Sheet "Eintragen": Vorlagen, Lebensmittel, die letzten Einträge, die Mensa-Menüs
 * des Tages, die Suche in den eingebauten Nährwert-Tabellen ([catalog]) und die Barcode-Suche.
 * [lookup] ist die Suche bei Open Food Facts im Internet; im Test eine Attrappe.
 */
class AddFoodViewModel(
    private val nutrition: NutritionRepository,
    private val mensaClient: MensaClient,
    mensaSettings: MensaSettings,
    private val lookup: suspend (String) -> OffLookup,
    private val catalog: FoodCatalog = FoodCatalog.empty(),
    scope: CoroutineScope = defaultViewModelScope(),
) : ViewModel(scope) {

    private val menus = MutableStateFlow<Map<LocalDate, MenuLoad>>(emptyMap())
    private val loadJobs = mutableMapOf<LocalDate, Job>()

    private val mensaPart = combine(mensaSettings.preferences, menus) { prefs, loaded -> prefs.mensas to loaded }

    private val catalogQuery = MutableStateFlow("")

    /**
     * Die Suche läuft erst, wenn du kurz nicht tippst (debounce), und eine neue Eingabe bricht die alte Suche ab
     * (transformLatest). Ein leeres Feld leert die Treffer sofort. Beim allerersten Mal zeigt "loading", dass die
     * Tabellen noch geladen werden.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val catalogResults: Flow<CatalogResults> = catalogQuery
        .debounce { if (it.isBlank()) 0L else SEARCH_DELAY_MS }
        .transformLatest { query ->
            if (query.isNotBlank() && !catalog.isLoaded) emit(CatalogResults(query, loading = true))
            emit(catalog.search(query))
        }

    private val extras = combine(mensaPart, catalogResults) { mensa, results -> mensa to results }

    val uiState: StateFlow<AddFoodUiState> =
        combine(nutrition.templates, nutrition.foods, nutrition.recentEntries, nutrition.recentMensa, extras) {
                templates, foods, recent, recentMensa, (mensa, results) ->
            AddFoodUiState(templates, foods, recent, recentMensa, mensa.first, mensa.second, results)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddFoodUiState())

    // --- Mensa ---

    /**
     * Lädt die Menüs eines Tages. Der [MensaClient] ist derselbe wie im Mensa-Tab: Was dort schon geladen ist,
     * erscheint hier sofort, ohne Internet.
     */
    fun loadMensa(date: LocalDate, refresh: Boolean = false) {
        if (!refresh) {
            mensaClient.cached(date)?.let { cached ->
                menus.update { it + (date to MenuLoad.Loaded(cached)) }
                return
            }
            if (menus.value[date] is MenuLoad.Loaded || loadJobs[date]?.isActive == true) return
        }
        loadJobs[date]?.cancel()
        loadJobs[date] = viewModelScope.launch {
            if (menus.value[date] !is MenuLoad.Loaded) menus.update { it + (date to MenuLoad.Loading) }
            val menu = mensaClient.load(date, refresh)
            val allFailed = menu.meals.isEmpty() && menu.failed.isNotEmpty()
            menus.update { current ->
                when {
                    !allFailed -> current + (date to MenuLoad.Loaded(menu))
                    // Beim erneuten Laden ohne Netz lieber die alten Menüs behalten.
                    current[date] is MenuLoad.Loaded -> current
                    // Noch keine eigenen: Vielleicht hat der Mensa-Tab den Tag schon geladen (der Zwischenspeicher
                    // bleibt bei einem Fehlschlag erhalten). Erst wenn auch dort nichts ist, gilt der Tag als offline.
                    else -> current + (date to (mensaClient.cached(date)?.let { MenuLoad.Loaded(it) } ?: MenuLoad.Offline))
                }
            }
        }
    }

    /** Startwerte für ein Mensa-Gericht: letzter eigener Eintrag, Angaben der Mensa oder Schätzung. */
    suspend fun mensaPortion(meal: Meal): PortionValues = nutrition.mensaPortion(meal)

    // --- Eintragen ---

    fun log(entry: FoodEntry) {
        viewModelScope.launch { nutrition.log(entry) }
    }

    fun applyTemplate(templateId: Long, date: LocalDate, slot: MealSlot) {
        viewModelScope.launch { nutrition.applyTemplate(templateId, date, slot) }
    }

    fun deleteTemplate(template: MealTemplate) {
        viewModelScope.launch { nutrition.deleteTemplate(template) }
    }

    // --- Lebensmittel ---

    /** Beginnt die eingebauten Tabellen zu laden, bevor du tippst. Mehrfaches Aufrufen schadet nicht. */
    fun preloadCatalog() = catalog.preload()

    /** Neue Eingabe im Suchfeld unter "Lebensmittel". */
    fun searchCatalog(query: String) {
        catalogQuery.value = query
    }

    /**
     * Trägt ein Lebensmittel aus der eingebauten Tabelle ein. Zuerst wird es als eigenes gespeichert: Dann steht
     * es beim nächsten Mal oben unter "Meine Lebensmittel", lässt sich als Favorit merken und auch per Barcode
     * ohne Internet wiederfinden. Der Eintrag verweist darauf.
     */
    fun logCatalogFood(food: Food, entry: FoodEntry) {
        viewModelScope.launch {
            val id = nutrition.saveFood(food)
            nutrition.log(entry.copy(foodId = id))
        }
    }

    /**
     * Speichert ein Lebensmittel. [onSaved] bekommt es mit seiner ID zurück, z. B. um es gleich einzutragen.
     * Läuft im viewModelScope, damit das Speichern auch dann fertig wird, wenn das Sheet schon zu ist.
     */
    fun saveFood(food: Food, onSaved: (Food) -> Unit = {}) {
        viewModelScope.launch {
            val id = nutrition.saveFood(food)
            onSaved(food.copy(id = id))
        }
    }

    /** Einträge mit diesem Lebensmittel bleiben im Tagebuch (siehe [NutritionRepository.deleteFood]). */
    fun deleteFood(food: Food) {
        viewModelScope.launch { nutrition.deleteFood(food) }
    }

    /**
     * Prüft einen gescannten oder getippten Barcode, Schritt für Schritt:
     * 1. Gültig? Sonst [BarcodeOutcome.Invalid]. Erst danach geht der Code an die Datenbank oder ins Internet.
     * 2. Schon in "Meine Lebensmittel"? Das klappt auch ohne Internet und auch für Ladencodes.
     * 3. Ladencode? Den kennt Open Food Facts nicht, also gar nicht erst fragen.
     * 4. In der eingebauten Produktliste aus Schweizer Läden? Das geht ohne Internet.
     * 5. Sonst bei Open Food Facts im Internet nachsehen (dort gibt es auch neuere Produkte).
     */
    suspend fun resolveBarcode(raw: String): BarcodeOutcome {
        val code = Barcode.validate(raw) ?: return BarcodeOutcome.Invalid
        nutrition.foodByBarcode(code)?.let { return BarcodeOutcome.Known(it) }
        if (Barcode.isInStore(code)) return BarcodeOutcome.InStore(code)
        catalog.byBarcode(code)?.let { return BarcodeOutcome.Found(it.toFood().copy(barcode = code)) }
        val result = try {
            lookup(code)
        } catch (e: CancellationException) {
            // Sheet geschlossen: Abbruch bleibt Abbruch.
            throw e
        } catch (e: Exception) {
            OffLookup.Failed("Open Food Facts ist gerade nicht erreichbar.")
        }
        return when (result) {
            // Der gescannte Code gehört zum Lebensmittel: Beim nächsten Scan findet es Schritt 2.
            is OffLookup.Found -> BarcodeOutcome.Found(result.food.copy(id = 0, barcode = code))
            OffLookup.NotFound -> BarcodeOutcome.NotFound(code)
            is OffLookup.Failed -> BarcodeOutcome.Failed(code, result.message)
        }
    }

    private companion object {
        /** So lange nach dem letzten Tastendruck, bis gesucht wird. Kürzer fühlt sich nicht schneller an. */
        const val SEARCH_DELAY_MS = 150L
    }
}
