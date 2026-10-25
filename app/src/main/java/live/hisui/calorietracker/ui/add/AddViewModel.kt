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

data class AddUiState(
    val query: String = "",
    val local: List<Food> = emptyList(),
    val usda: List<FoodCandidate>? = null,
    val usdaLoading: Boolean = false,
    val message: String? = null,
    val recents: List<LogEntry> = emptyList(),
    val favorites: List<Food> = emptyList(),
    val savedMeals: List<SavedMeal> = emptyList(),
)

@HiltViewModel
class AddViewModel @Inject constructor(
    private val foods: FoodRepository,
    private val log: LogRepository,
    private val savedMeals: SavedMealRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AddUiState())
    val state: StateFlow<AddUiState> = _state.asStateFlow()
    val logDate get() = log.logTargetDate

    init {
        collect(log.recentFoodEntries) { s, v -> s.copy(recents = v) }
        collect(foods.favorites) { s, v -> s.copy(favorites = v) }
        collect(savedMeals.meals) { s, v -> s.copy(savedMeals = v) }
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

    fun open(candidate: FoodCandidate, onSaved: (Long) -> Unit) {
        viewModelScope.launch { onSaved(foods.saveUsdaCandidate(candidate).id) }
    }

    fun relog(entry: LogEntry, onDone: () -> Unit) {
        viewModelScope.launch {
            log.relog(entry, log.mealNow())
            onDone()
        }
    }

    fun logSavedMeal(meal: SavedMeal, onDone: () -> Unit) {
        viewModelScope.launch {
            savedMeals.log(meal, log.mealNow())
            onDone()
        }
    }

    fun deleteSavedMeal(meal: SavedMeal) {
        viewModelScope.launch { savedMeals.delete(meal.id) }
    }

    fun renameSavedMeal(meal: SavedMeal, name: String) {
        viewModelScope.launch { savedMeals.rename(meal.id, name.trim()) }
    }
}
