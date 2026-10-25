package live.hisui.calorietracker.ui.custom

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
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.db.Portion
import live.hisui.calorietracker.domain.FoodSource
import live.hisui.calorietracker.domain.Nutrition

enum class EntryMode(val label: String) {
    PER_100G("Per 100 g"),
    PER_SERVING("Per serving"),
    RECIPE("Recipe totals"),
}

data class PortionRow(val label: String = "", val grams: String = "")

data class CustomFoodUiState(
    val name: String = "",
    val brand: String = "",
    val mode: EntryMode = EntryMode.PER_100G,
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val weight: String = "",
    val servings: String = "",
    val portions: List<PortionRow> = emptyList(),
    val existing: Food? = null,
) {
    private val weightG get() = weight.toDoubleOrNull()?.takeIf { it > 0 }
    private val servingCount get() = servings.toDoubleOrNull()?.takeIf { it > 0 }
    private val entered
        get() = kcal.toDoubleOrNull()?.takeIf { it >= 0 }?.let {
            Nutrition(it, protein.toDoubleOrNull(), carbs.toDoubleOrNull(), fat.toDoubleOrNull())
        }

    val per100g: Nutrition?
        get() = when (mode) {
            EntryMode.PER_100G -> entered
            EntryMode.PER_SERVING, EntryMode.RECIPE -> weightG?.let { entered?.per100g(it) }
        }

    val autoPortion: Portion?
        get() = when (mode) {
            EntryMode.PER_100G -> null
            EntryMode.PER_SERVING -> weightG?.let { Portion(foodId = 0, label = "1 serving", grams = it) }
            EntryMode.RECIPE -> weightG?.let { w -> servingCount?.let { Portion(foodId = 0, label = "1 serving", grams = w / it) } }
        }

    val canSave: Boolean
        get() = name.isNotBlank() && per100g != null && (mode != EntryMode.RECIPE || servingCount != null)
}

@HiltViewModel
class CustomFoodViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val foods: FoodRepository,
) : ViewModel() {
    private val foodId: Long = handle["foodId"] ?: -1L
    private val _state = MutableStateFlow(CustomFoodUiState())
    val state: StateFlow<CustomFoodUiState> = _state.asStateFlow()

    init {
        if (foodId > 0) {
            viewModelScope.launch {
                val food = foods.get(foodId) ?: return@launch
                _state.value = CustomFoodUiState(
                    name = food.name,
                    brand = food.brand.orEmpty(),
                    kcal = food.kcalPer100g.clean(),
                    protein = food.proteinPer100g?.clean().orEmpty(),
                    carbs = food.carbsPer100g?.clean().orEmpty(),
                    fat = food.fatPer100g?.clean().orEmpty(),
                    portions = foods.portions(foodId).map { PortionRow(it.label.removePrefix("1 "), it.grams.clean()) },
                    existing = food,
                )
            }
        }
    }

    fun edit(change: CustomFoodUiState.() -> CustomFoodUiState) = _state.update { it.change() }

    fun save(onSaved: (Long) -> Unit) {
        val s = _state.value
        val per100 = s.per100g ?: return
        val extra = s.portions.mapNotNull { row ->
            val grams = row.grams.toDoubleOrNull()?.takeIf { it > 0 }
            if (row.label.isBlank() || grams == null) null else Portion(foodId = 0, label = "1 ${row.label.trim()}", grams = grams)
        }
        val food = (s.existing ?: Food(name = "", source = FoodSource.CUSTOM, kcalPer100g = 0.0)).copy(
            name = s.name.trim(),
            brand = s.brand.trim().ifBlank { null },
            kcalPer100g = per100.kcal,
            proteinPer100g = per100.proteinG,
            carbsPer100g = per100.carbsG,
            fatPer100g = per100.fatG,
        )
        viewModelScope.launch { onSaved(foods.saveCustom(food, listOfNotNull(s.autoPortion) + extra)) }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _state.value.existing?.id ?: return
        viewModelScope.launch {
            foods.delete(id)
            onDeleted()
        }
    }
}

private fun Double.clean(): String = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(this)
