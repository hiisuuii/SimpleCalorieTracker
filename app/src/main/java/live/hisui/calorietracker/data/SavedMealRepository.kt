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
import live.hisui.calorietracker.domain.MealShare
import live.hisui.calorietracker.domain.SharedFood
import live.hisui.calorietracker.domain.SharedMeal
import timber.log.Timber

@Singleton
class SavedMealRepository @Inject constructor(
    private val dao: SavedMealDao,
    private val foods: FoodRepository,
    private val log: LogRepository,
) {
    val meals: Flow<List<SavedMeal>> = dao.meals()

    suspend fun create(name: String, items: List<Pair<Food, Double>>, totalGrams: Double? = null, totalServings: Double? = null) {
        dao.create(name, items.map { it.toItem() }, totalGrams, totalServings)
        Timber.i("Saved meal '%s' with %d items", name, items.size)
    }

    suspend fun update(id: Long, name: String, items: List<Pair<Food, Double>>, totalGrams: Double?, totalServings: Double?) {
        dao.replace(id, name, totalGrams, totalServings, items.map { it.toItem() })
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

    // eaten is in grams for a meal with a total weight and in servings for one with a total serving count.
    suspend fun log(meal: SavedMeal, slot: Meal, eaten: Double? = null) {
        val total = meal.totalGrams ?: meal.totalServings
        val scale = if (eaten != null && total != null) eaten / total else 1.0
        val items = foodsOf(meal.id).map { (food, grams) -> food to grams * scale }
        if (items.isNotEmpty()) log.logGroup(meal.name, items, slot)
    }

    suspend fun shareCode(meal: SavedMeal): String {
        val items = foodsOf(meal.id).map { (food, grams) ->
            SharedFood(food.name, food.kcalPer100g, food.proteinPer100g, food.carbsPer100g, food.fatPer100g, grams)
        }
        return MealShare.encode(SharedMeal(meal.name, meal.totalGrams, meal.totalServings, items))
    }

    suspend fun withName(name: String): SavedMeal? = dao.byName(name)

    suspend fun uniqueName(name: String): String =
        generateSequence(2) { it + 1 }.map { "$name ($it)" }.first { dao.byName(it) == null }

    suspend fun import(shared: SharedMeal, name: String, replaceId: Long?) {
        val items = shared.items.map { foods.findOrCreateShared(it) to it.grams }
        if (replaceId != null) update(replaceId, name, items, shared.totalGrams, shared.totalServings)
        else create(name, items, shared.totalGrams, shared.totalServings)
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        Timber.i("Deleted saved meal %d", id)
    }

    private fun Pair<Food, Double>.toItem() = SavedMealItem(savedMealId = 0, foodId = first.id, grams = second)
}
