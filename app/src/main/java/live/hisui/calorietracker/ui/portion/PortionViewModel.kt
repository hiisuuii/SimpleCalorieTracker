package live.hisui.calorietracker.ui.portion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import live.hisui.calorietracker.data.FoodRepository
import live.hisui.calorietracker.data.LogRepository
import live.hisui.calorietracker.data.SettingsRepository
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.db.Portion
import live.hisui.calorietracker.domain.EntrySource
import live.hisui.calorietracker.domain.GRAMS_PER_OUNCE
import live.hisui.calorietracker.domain.Meal
import live.hisui.calorietracker.domain.Nutrition

data class PortionUiState(
    val food: Food? = null,
    val portions: List<Portion> = emptyList(),
    val selected: Portion? = null,
    val amount: String = "100",
    val meal: Meal = Meal.SNACK,
    val useOunces: Boolean = false,
    val showMacros: Boolean = false,
) {
    val grams: Double?
        get() = amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { value ->
            when {
                selected != null -> value * selected.grams
                useOunces -> value * GRAMS_PER_OUNCE
                else -> value
            }
        }

    val nutrition: Nutrition?
        get() {
            val f = food ?: return null
            val g = grams ?: return null
            return Nutrition(f.kcalPer100g, f.proteinPer100g, f.carbsPer100g, f.fatPer100g).scaledTo(g)
        }
}

@HiltViewModel
class PortionViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val foods: FoodRepository,
    private val log: LogRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val foodId: Long = checkNotNull(handle["foodId"])
    private val source = handle.get<String>("source")?.let { EntrySource.valueOf(it.uppercase()) } ?: EntrySource.MANUAL
    private val _state = MutableStateFlow(PortionUiState())
    val state: StateFlow<PortionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val ounces = settings.current().useOunces
            val portions = foods.portions(foodId)
            // Packaged products are usually eaten by the serving, so start there.
            val serving = portions.firstOrNull { it.label.startsWith("1 serving") }.takeIf { source == EntrySource.BARCODE }
            _state.value = PortionUiState(
                food = foods.get(foodId),
                portions = portions,
                selected = serving,
                meal = log.mealNow(),
                useOunces = ounces,
                amount = if (serving != null) "1" else defaultWeight(ounces),
            )
        }
    }

    fun setAmount(text: String) = _state.update { it.copy(amount = text) }

    fun selectPortion(portion: Portion?) = _state.update {
        it.copy(selected = portion, amount = if (portion == null) defaultWeight(it.useOunces) else "1")
    }

    fun setMeal(meal: Meal) = _state.update { it.copy(meal = meal) }

    fun toggleMacros() = _state.update { it.copy(showMacros = !it.showMacros) }

    fun toggleFavorite() {
        val food = _state.value.food ?: return
        val favorite = !food.isFavorite
        _state.update { it.copy(food = food.copy(isFavorite = favorite)) }
        viewModelScope.launch { foods.setFavorite(food.id, favorite) }
    }

    fun log(onDone: () -> Unit) {
        val s = _state.value
        val food = s.food ?: return
        val grams = s.grams ?: return
        viewModelScope.launch {
            log.logFood(food, grams, s.meal, source)
            onDone()
        }
    }

    private fun defaultWeight(ounces: Boolean) = if (ounces) "4" else "100"
}
