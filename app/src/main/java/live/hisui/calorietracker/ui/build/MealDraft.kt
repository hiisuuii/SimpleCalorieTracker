package live.hisui.calorietracker.ui.build

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import live.hisui.calorietracker.data.db.Food

data class DraftItem(val food: Food, val grams: Double)

// The meal being built lives here, outside any screen, because picking each ingredient navigates away from the builder.
@Singleton
class MealDraft @Inject constructor() {
    private val _items = MutableStateFlow<List<DraftItem>>(emptyList())
    val items: StateFlow<List<DraftItem>> = _items.asStateFlow()

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    fun setName(value: String) {
        _name.value = value
    }

    fun add(food: Food, grams: Double) = _items.update { it + DraftItem(food, grams) }

    fun setGrams(index: Int, grams: Double) =
        _items.update { list -> list.mapIndexed { i, item -> if (i == index) item.copy(grams = grams) else item } }

    fun remove(index: Int) = _items.update { list -> list.filterIndexed { i, _ -> i != index } }

    fun clear() {
        _items.value = emptyList()
        _name.value = ""
    }
}
