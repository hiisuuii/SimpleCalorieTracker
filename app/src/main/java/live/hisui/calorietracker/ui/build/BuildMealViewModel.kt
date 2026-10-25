package live.hisui.calorietracker.ui.build

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import live.hisui.calorietracker.data.LogRepository
import live.hisui.calorietracker.data.SavedMealRepository
import live.hisui.calorietracker.domain.Meal
import live.hisui.calorietracker.domain.Nutrition

data class BuildMealUiState(
    val name: String = "",
    val items: List<DraftItem> = emptyList(),
    val slot: Meal = Meal.SNACK,
    val saveForReuse: Boolean = true,
) {
    val total: Nutrition
        get() {
            val parts = items.map { Nutrition(it.food.kcalPer100g, it.food.proteinPer100g, it.food.carbsPer100g, it.food.fatPer100g).scaledTo(it.grams) }
            return Nutrition(
                kcal = parts.sumOf { it.kcal },
                proteinG = parts.mapNotNull { it.proteinG }.takeIf { it.isNotEmpty() }?.sum(),
                carbsG = parts.mapNotNull { it.carbsG }.takeIf { it.isNotEmpty() }?.sum(),
                fatG = parts.mapNotNull { it.fatG }.takeIf { it.isNotEmpty() }?.sum(),
            )
        }

    val canLog: Boolean get() = name.isNotBlank() && items.isNotEmpty()
}

@HiltViewModel
class BuildMealViewModel @Inject constructor(
    private val draft: MealDraft,
    private val log: LogRepository,
    private val savedMeals: SavedMealRepository,
) : ViewModel() {
    val logDate get() = log.logTargetDate
    private val slot = MutableStateFlow(Meal.SNACK)
    private val saveForReuse = MutableStateFlow(true)

    val state: StateFlow<BuildMealUiState> = combine(draft.name, draft.items, slot, saveForReuse) { name, items, slot, save ->
        BuildMealUiState(name, items, slot, save)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BuildMealUiState())

    init {
        viewModelScope.launch { slot.value = log.mealNow() }
    }

    fun setName(value: String) = draft.setName(value)

    fun setSlot(value: Meal) = slot.update { value }

    fun setSaveForReuse(value: Boolean) = saveForReuse.update { value }

    fun setGrams(index: Int, grams: Double) = draft.setGrams(index, grams)

    fun remove(index: Int) = draft.remove(index)

    fun startOver() = draft.clear()

    fun logMeal(onDone: () -> Unit) {
        val s = state.value
        if (!s.canLog) return
        viewModelScope.launch {
            val items = s.items.map { it.food to it.grams }
            val name = s.name.trim()
            log.logGroup(name, items, s.slot)
            if (s.saveForReuse) savedMeals.create(name, items)
            draft.clear()
            onDone()
        }
    }
}
