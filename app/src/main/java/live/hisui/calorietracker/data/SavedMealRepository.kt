package live.hisui.calorietracker.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.db.LogEntry
import live.hisui.calorietracker.data.db.SavedMeal
import live.hisui.calorietracker.data.db.SavedMealDao
import live.hisui.calorietracker.data.db.SavedMealItem
import live.hisui.calorietracker.domain.Meal

@Singleton
class SavedMealRepository @Inject constructor(
    private val dao: SavedMealDao,
    private val foods: FoodRepository,
    private val log: LogRepository,
) {
    val meals: Flow<List<SavedMeal>> = dao.meals()

    suspend fun create(name: String, items: List<Pair<Food, Double>>) {
        dao.create(name, items.map { (food, grams) -> SavedMealItem(savedMealId = 0, foodId = food.id, grams = grams) })
    }

    // Quick-add entries have no food to point at, so they cannot be part of a saved meal.
    suspend fun saveFromEntries(name: String, entries: List<LogEntry>): Boolean {
        val items = entries.mapNotNull { e ->
            val foodId = e.foodId ?: return@mapNotNull null
            val grams = e.grams ?: return@mapNotNull null
            SavedMealItem(savedMealId = 0, foodId = foodId, grams = grams)
        }
        if (items.isEmpty()) return false
        dao.create(name, items)
        return true
    }

    suspend fun log(meal: SavedMeal, slot: Meal) {
        val items = dao.items(meal.id).mapNotNull { item -> foods.get(item.foodId)?.let { it to item.grams } }
        if (items.isNotEmpty()) log.logGroup(meal.name, items, slot)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun rename(id: Long, name: String) = dao.rename(id, name)
}
