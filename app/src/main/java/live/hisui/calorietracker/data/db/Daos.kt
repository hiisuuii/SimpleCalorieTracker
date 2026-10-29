package live.hisui.calorietracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import live.hisui.calorietracker.domain.FoodSource

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: LogEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<LogEntry>)

    @Update
    suspend fun update(entry: LogEntry)

    @Delete
    suspend fun delete(entry: LogEntry)

    @Query("SELECT * FROM log_entry WHERE id = :id")
    suspend fun byId(id: Long): LogEntry?

    @Query("SELECT * FROM log_entry WHERE log_date = :date ORDER BY logged_at, id")
    fun entriesForDate(date: String): Flow<List<LogEntry>>

    @Query("DELETE FROM log_entry WHERE group_id = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("UPDATE log_entry SET group_name = :name WHERE group_id = :groupId")
    suspend fun renameGroup(groupId: String, name: String)

    @Query("UPDATE log_entry SET log_date = :date WHERE group_id = :groupId")
    suspend fun moveGroup(groupId: String, date: String)

    @Query("SELECT COALESCE(SUM(kcal), 0) FROM log_entry WHERE log_date = :date")
    fun totalForDate(date: String): Flow<Double>

    @Query("SELECT COALESCE(SUM(kcal), 0) FROM log_entry WHERE log_date = :date")
    suspend fun totalForDateOnce(date: String): Double

    @Query("SELECT log_date, SUM(kcal) AS kcal FROM log_entry GROUP BY log_date ORDER BY log_date DESC")
    fun dailyTotals(): Flow<List<DayTotal>>

    @Query("SELECT * FROM log_entry ORDER BY logged_at")
    suspend fun all(): List<LogEntry>

    @Query("DELETE FROM log_entry")
    suspend fun deleteAll()

    @Query(
        "SELECT * FROM log_entry WHERE id IN " +
            "(SELECT MAX(id) FROM log_entry WHERE food_id IS NOT NULL GROUP BY food_id) " +
            "ORDER BY logged_at DESC LIMIT :limit",
    )
    fun recentFoodEntries(limit: Int = 20): Flow<List<LogEntry>>
}

@Dao
interface FoodDao {
    @Insert
    suspend fun insert(food: Food): Long

    @Update
    suspend fun update(food: Food)

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun byId(id: Long): Food?

    @Query("SELECT * FROM food WHERE id = :id")
    fun observe(id: Long): Flow<Food?>

    @Query("SELECT * FROM food WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): Food?

    @Query(
        "SELECT * FROM food WHERE name = :name AND kcal_per_100g = :kcal AND protein_per_100g IS :protein " +
            "AND carbs_per_100g IS :carbs AND fat_per_100g IS :fat LIMIT 1",
    )
    suspend fun matching(name: String, kcal: Double, protein: Double?, carbs: Double?, fat: Double?): Food?

    @Query("SELECT * FROM food WHERE source = :source AND external_id = :externalId")
    suspend fun byExternalId(source: FoodSource, externalId: String): Food?

    @Query("SELECT * FROM food WHERE source = 'CUSTOM' ORDER BY name COLLATE NOCASE")
    fun customFoods(): Flow<List<Food>>

    @Query(
        "SELECT food.* FROM food JOIN food_fts ON food.rowid = food_fts.rowid " +
            "WHERE food_fts MATCH :ftsQuery LIMIT :limit",
    )
    suspend fun search(ftsQuery: String, limit: Int = 60): List<Food>

    @Query("SELECT * FROM food WHERE last_used_at IS NOT NULL ORDER BY last_used_at DESC LIMIT :limit")
    fun recents(limit: Int = 30): Flow<List<Food>>

    @Query("SELECT * FROM food WHERE is_favorite = 1 ORDER BY name COLLATE NOCASE")
    fun favorites(): Flow<List<Food>>

    @Query("UPDATE food SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE food SET last_used_at = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)

    @Query("DELETE FROM food WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM food")
    suspend fun all(): List<Food>

    @Insert
    suspend fun insertAll(foods: List<Food>)

    @Query("DELETE FROM food")
    suspend fun deleteAll()

    @Insert
    suspend fun insertPortions(portions: List<Portion>)

    @Query("DELETE FROM portion WHERE food_id = :foodId")
    suspend fun deletePortions(foodId: Long)

    @Query("SELECT * FROM portion WHERE food_id = :foodId ORDER BY grams")
    suspend fun portionsFor(foodId: Long): List<Portion>

    @Query("SELECT * FROM portion")
    suspend fun allPortions(): List<Portion>

    @Transaction
    suspend fun replacePortions(foodId: Long, portions: List<Portion>) {
        deletePortions(foodId)
        insertPortions(portions.map { it.copy(id = 0, foodId = foodId) })
    }

    @Transaction
    suspend fun insertWithPortions(food: Food, portions: List<Portion>): Long {
        val id = insert(food)
        insertPortions(portions.map { it.copy(id = 0, foodId = id) })
        return id
    }
}

@Dao
interface AliasDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alias: Alias)

    @Query("SELECT * FROM alias WHERE phrase = :phrase")
    suspend fun byPhrase(phrase: String): Alias?

    @Query("SELECT * FROM alias")
    suspend fun all(): List<Alias>

    @Insert
    suspend fun insertAll(aliases: List<Alias>)

    @Query("DELETE FROM alias")
    suspend fun deleteAll()
}

@Dao
interface SavedMealDao {
    @Insert
    suspend fun insert(meal: SavedMeal): Long

    @Insert
    suspend fun insertItems(items: List<SavedMealItem>)

    @Query("SELECT * FROM saved_meal ORDER BY name COLLATE NOCASE")
    fun meals(): Flow<List<SavedMeal>>

    @Query("SELECT * FROM saved_meal_item WHERE saved_meal_id = :mealId")
    suspend fun items(mealId: Long): List<SavedMealItem>

    @Query("DELETE FROM saved_meal WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM saved_meal WHERE id = :id")
    suspend fun byId(id: Long): SavedMeal?

    @Query("SELECT * FROM saved_meal WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): SavedMeal?

    @Query("UPDATE saved_meal SET name = :name, total_grams = :totalGrams, total_servings = :totalServings WHERE id = :id")
    suspend fun updateDetails(id: Long, name: String, totalGrams: Double?, totalServings: Double?)

    @Query("DELETE FROM saved_meal_item WHERE saved_meal_id = :mealId")
    suspend fun deleteItems(mealId: Long)

    @Query("SELECT * FROM saved_meal")
    suspend fun allMeals(): List<SavedMeal>

    @Query("SELECT * FROM saved_meal_item")
    suspend fun allItems(): List<SavedMealItem>

    @Insert
    suspend fun insertMeals(meals: List<SavedMeal>)

    @Query("DELETE FROM saved_meal")
    suspend fun deleteAll()

    @Transaction
    suspend fun create(name: String, items: List<SavedMealItem>, totalGrams: Double? = null, totalServings: Double? = null): Long {
        val id = insert(SavedMeal(name = name, totalGrams = totalGrams, totalServings = totalServings))
        insertItems(items.map { it.copy(id = 0, savedMealId = id) })
        return id
    }

    @Transaction
    suspend fun replace(id: Long, name: String, totalGrams: Double?, totalServings: Double?, items: List<SavedMealItem>) {
        updateDetails(id, name, totalGrams, totalServings)
        deleteItems(id)
        insertItems(items.map { it.copy(id = 0, savedMealId = id) })
    }
}
