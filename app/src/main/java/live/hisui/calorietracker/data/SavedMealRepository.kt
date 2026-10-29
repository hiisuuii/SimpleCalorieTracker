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
import timber.log.Timber

@Singleton
class SavedMealRepository @Inject constructor(
    private val dao: SavedMealDao,
    private val foods: FoodRepository,
    private val log: LogRepository,
) {
    val meals: Flow<List<SavedMeal>> = dao.meals()

    suspend fun create(name: String, items: List<Pair<Food, Double>>, totalGrams: Double? = null) {
        dao.create(name, items.map { it.toItem() }, totalGrams)
        Timber.i("Saved meal '%s' with %d items", name, items.size)
    }

    suspend fun update(id: Long, name: String, items: List<Pair<Food, Double>>, totalGrams: Double?) {
        dao.replace(id, name, totalGrams, items.map { it.toItem() })
        Timber.i("Updated saved meal %d '%s'", id, name)
    }

    suspend fun get(id: Long): SavedMeal? = dao.byId(id)

    suspend fun foodsOf(id: Long): List<Pair<Food, Double>> =
        dao.items(id).mapNotNull { item -> foods.get(item.foodId)?.let { it to item.grams } }

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

    suspend fun log(meal: SavedMeal, slot: Meal, eatenGrams: Double? = null) {
        val total = meal.totalGrams
        val scale = if (eatenGrams != null && total != null) eatenGrams / total else 1.0
        val items = foodsOf(meal.id).map { (food, grams) -> food to grams * scale }
        if (items.isNotEmpty()) log.logGroup(meal.name, items, slot)
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        Timber.i("Deleted saved meal %d", id)
    }

    private fun Pair<Food, Double>.toItem() = SavedMealItem(savedMealId = 0, foodId = first.id, grams = second)
}
