package live.hisui.calorietracker.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import live.hisui.calorietracker.data.FoodRepository
import live.hisui.calorietracker.data.LogRepository
import live.hisui.calorietracker.data.SavedMealRepository
import live.hisui.calorietracker.data.toSearchMessage
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.db.LogEntry
import live.hisui.calorietracker.data.db.SavedMeal
import live.hisui.calorietracker.data.net.FoodCandidate
import live.hisui.calorietracker.ui.build.DraftItem
import live.hisui.calorietracker.domain.Meal
import live.hisui.calorietracker.domain.MealCodeException
import live.hisui.calorietracker.domain.MealShare
import live.hisui.calorietracker.domain.SharedMeal
import live.hisui.calorietracker.ui.build.MealDraft
import timber.log.Timber

sealed interface ImportStep {
    data object Idle : ImportStep
    data class Entering(val error: String? = null) : ImportStep
    data class NameTaken(val meal: SharedMeal, val suggestedName: String, val existingId: Long) : ImportStep
}

data class AddUiState(
    val query: String = "",
    val local: List<Food> = emptyList(),
    val usda: List<FoodCandidate>? = null,
    val usdaLoading: Boolean = false,
    val message: String? = null,
    val recents: List<LogEntry> = emptyList(),
    val favorites: List<Food> = emptyList(),
    val savedMeals: List<SavedMeal> = emptyList(),
    val currentMeal: Meal = Meal.SNACK,
    val shareCode: String? = null,
    val import: ImportStep = ImportStep.Idle,
)

@HiltViewModel
class AddViewModel @Inject constructor(
    private val foods: FoodRepository,
    private val log: LogRepository,
    private val savedMeals: SavedMealRepository,
    private val draft: MealDraft,
) : ViewModel() {
    private val _state = MutableStateFlow(AddUiState())
    val state: StateFlow<AddUiState> = _state.asStateFlow()
    val logDate get() = log.logTargetDate

    init {
        collect(log.recentFoodEntries) { s, v -> s.copy(recents = v) }
        collect(foods.favorites) { s, v -> s.copy(favorites = v) }
        collect(savedMeals.meals) { s, v -> s.copy(savedMeals = v) }
        viewModelScope.launch { _state.update { it.copy(currentMeal = log.mealNow()) } }
        viewModelScope.launch { observeQuery() }
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeQuery() {
        _state
            .debounce(150)
            .mapLatest { it.query }
            .distinctUntilChanged()
            .mapLatest { foods.searchLocal(it) }
            .collect { results -> _state.update { it.copy(local = results) } }
    }

    private fun <T> collect(flow: kotlinx.coroutines.flow.Flow<T>, apply: (AddUiState, T) -> AddUiState) {
        viewModelScope.launch { flow.collect { v -> _state.update { apply(it, v) } } }
    }

    fun onQuery(text: String) = _state.update { it.copy(query = text, usda = null, message = null) }

    fun searchUsda() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        _state.update { it.copy(usdaLoading = true, message = null) }
        viewModelScope.launch {
            foods.searchUsda(query)
                .onSuccess { found ->
                    _state.update { it.copy(usda = found, usdaLoading = false, message = if (found.isEmpty()) "No USDA results" else null) }
                }
                .onFailure { e -> _state.update { it.copy(usdaLoading = false, message = e.toSearchMessage()) } }
        }
    }

    private var opening = false

    fun open(candidate: FoodCandidate, onSaved: (Long) -> Unit) {
        if (opening) return
        opening = true
        viewModelScope.launch {
            try {
                onSaved(foods.saveUsdaCandidate(candidate).id)
            } finally {
                opening = false
            }
        }
    }

    fun relog(entry: LogEntry, onDone: () -> Unit) {
        viewModelScope.launch {
            log.relog(entry, log.mealNow())
            onDone()
        }
    }

    fun logSavedMeal(meal: SavedMeal, slot: Meal, eaten: Double?, onDone: () -> Unit) {
        viewModelScope.launch {
            savedMeals.log(meal, slot, eaten)
            onDone()
        }
    }

    fun shareSavedMeal(meal: SavedMeal) {
        viewModelScope.launch { _state.update { it.copy(shareCode = savedMeals.shareCode(meal)) } }
    }

    fun dismissShare() = _state.update { it.copy(shareCode = null) }

    fun startImport() = _state.update { it.copy(import = ImportStep.Entering()) }

    fun dismissImport() = _state.update { it.copy(import = ImportStep.Idle) }

    fun submitImport(code: String) {
        val shared = try {
            MealShare.decode(code)
        } catch (e: MealCodeException) {
            Timber.w(e, "Meal import rejected")
            _state.update { it.copy(import = ImportStep.Entering(e.message)) }
            return
        }
        viewModelScope.launch {
            val existing = savedMeals.withName(shared.name)
            if (existing == null) {
                savedMeals.import(shared, shared.name, null)
                dismissImport()
            } else {
                _state.update { it.copy(import = ImportStep.NameTaken(shared, savedMeals.uniqueName(shared.name), existing.id)) }
            }
        }
    }

    fun confirmImport(step: ImportStep.NameTaken, name: String, replace: Boolean) {
        viewModelScope.launch {
            savedMeals.import(step.meal, name.trim(), step.existingId.takeIf { replace })
            dismissImport()
        }
    }

    fun deleteSavedMeal(meal: SavedMeal) {
        viewModelScope.launch { savedMeals.delete(meal.id) }
    }

    fun editSavedMeal(meal: SavedMeal, onLoaded: () -> Unit) {
        viewModelScope.launch {
            draft.load(meal.id, meal.name, meal.totalGrams, meal.totalServings, savedMeals.foodsOf(meal.id).map { (food, grams) -> DraftItem(food, grams) })
            onLoaded()
        }
    }
}
