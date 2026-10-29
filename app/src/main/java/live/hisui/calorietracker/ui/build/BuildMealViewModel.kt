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
    val totalAmount: String = "",
    val byServings: Boolean = false,
    val eatenAmount: String = "",
    val editingId: Long? = null,
) {
    val totalSize: Double? get() = totalAmount.toDoubleOrNull()?.takeIf { it > 0 }

    val eatenSize: Double? get() = eatenAmount.toDoubleOrNull()?.takeIf { it > 0 }

    val totalGrams: Double? get() = totalSize.takeIf { !byServings }

    val totalServings: Double? get() = totalSize.takeIf { byServings }

    // Ingredient amounts are for the whole dish; logging part of it scales them by eaten / total.
    val logScale: Double
        get() {
            val total = totalSize
            val eaten = eatenSize
            return if (total != null && eaten != null) eaten / total else 1.0
        }

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

    val canLog: Boolean get() = name.isNotBlank() && items.isNotEmpty() && (totalAmount.isBlank() || totalSize != null) &&
            (eatenAmount.isBlank() || eatenSize != null)
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
    private val eatenAmount = MutableStateFlow("")

    val state: StateFlow<BuildMealUiState> = combine(
        combine(draft.name, draft.items, slot, saveForReuse) { name, items, slot, save -> BuildMealUiState(name, items, slot, save) },
        combine(draft.totalAmount, draft.byServings, ::Pair),
        eatenAmount,
        draft.editingId,
    ) { base, (total, byServings), eaten, editingId ->
        base.copy(totalAmount = total, byServings = byServings, eatenAmount = eaten, editingId = editingId)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BuildMealUiState())

    init {
        viewModelScope.launch { slot.value = log.mealNow() }
    }

    fun setName(value: String) = draft.setName(value)

    fun setTotalAmount(value: String) = draft.setTotalAmount(value)

    fun setByServings(value: Boolean) {
        draft.setByServings(value)
        eatenAmount.value = ""
    }

    fun setEatenAmount(value: String) = eatenAmount.update { value }

    fun setSlot(value: Meal) = slot.update { value }

    fun setSaveForReuse(value: Boolean) = saveForReuse.update { value }

    fun setGrams(index: Int, grams: Double) = draft.setGrams(index, grams)

    fun remove(index: Int) = draft.remove(index)

    fun startOver() {
        eatenAmount.value = ""
        draft.clear()
    }

    fun logMeal(onDone: () -> Unit) {
        val s = state.value
        if (!s.canLog) return
        viewModelScope.launch {
            val items = s.items.map { it.food to it.grams }
            val name = s.name.trim()
            val editingId = s.editingId
            if (editingId != null) {
                savedMeals.update(editingId, name, items, s.totalGrams, s.totalServings)
            } else {
                log.logGroup(name, items.map { (food, grams) -> food to grams * s.logScale }, s.slot)
                if (s.saveForReuse) savedMeals.create(name, items, s.totalGrams, s.totalServings)
            }
            startOver()
            onDone()
        }
    }
}
