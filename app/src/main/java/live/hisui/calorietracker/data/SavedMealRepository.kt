package live.hisui.calorietracker.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
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

    suspend fun log(mealId: Long, meal: Meal) {
        dao.items(mealId).forEach { item ->
            foods.get(item.foodId)?.let { log.logFood(it, item.grams, meal) }
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
