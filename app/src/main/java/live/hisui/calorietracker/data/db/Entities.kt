package live.hisui.calorietracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class Meal { BREAKFAST, LUNCH, DINNER, SNACK }

enum class EntrySource { MANUAL, TEXT, BARCODE, LABEL, QUICK_ADD }

enum class FoodSource { USDA, OFF, CUSTOM }

@Serializable
@Entity(
    tableName = "food",
    indices = [
        Index("barcode"),
        Index(value = ["source", "external_id"], unique = true),
    ],
)
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val source: FoodSource,
    @ColumnInfo(name = "external_id") val externalId: String? = null,
    @ColumnInfo(name = "data_type") val dataType: String? = null,
    val barcode: String? = null,
    @ColumnInfo(name = "kcal_per_100g") val kcalPer100g: Double,
    @ColumnInfo(name = "protein_per_100g") val proteinPer100g: Double? = null,
    @ColumnInfo(name = "carbs_per_100g") val carbsPer100g: Double? = null,
    @ColumnInfo(name = "fat_per_100g") val fatPer100g: Double? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long? = null,
)

@Fts4(contentEntity = Food::class)
@Entity(tableName = "food_fts")
data class FoodFts(
    val name: String,
    val brand: String?,
)

@Serializable
@Entity(
    tableName = "portion",
    foreignKeys = [
        ForeignKey(
            entity = Food::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("food_id")],
)
data class Portion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "food_id") val foodId: Long,
    val label: String,
    val grams: Double,
)

@Serializable
@Entity(
    tableName = "alias",
    foreignKeys = [
        ForeignKey(
            entity = Food::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["phrase"], unique = true), Index("food_id")],
)
data class Alias(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phrase: String,
    @ColumnInfo(name = "food_id") val foodId: Long,
    @ColumnInfo(name = "default_grams") val defaultGrams: Double? = null,
    @ColumnInfo(name = "default_portion_id") val defaultPortionId: Long? = null,
)

@Serializable
@Entity(
    tableName = "log_entry",
    foreignKeys = [
        ForeignKey(
            entity = Food::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("log_date"), Index("food_id")],
)
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "logged_at") val loggedAt: Long,
    @ColumnInfo(name = "tz_id") val tzId: String,
    @ColumnInfo(name = "log_date") val logDate: String,
    val meal: Meal,
    val name: String,
    val grams: Double? = null,
    val kcal: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double? = null,
    @ColumnInfo(name = "carbs_g") val carbsG: Double? = null,
    @ColumnInfo(name = "fat_g") val fatG: Double? = null,
    val source: EntrySource,
    @ColumnInfo(name = "food_id") val foodId: Long? = null,
)

@Serializable
@Entity(tableName = "saved_meal")
data class SavedMeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Serializable
@Entity(
    tableName = "saved_meal_item",
    foreignKeys = [
        ForeignKey(
            entity = SavedMeal::class,
            parentColumns = ["id"],
            childColumns = ["saved_meal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Food::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("saved_meal_id"), Index("food_id")],
)
data class SavedMealItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "saved_meal_id") val savedMealId: Long,
    @ColumnInfo(name = "food_id") val foodId: Long,
    val grams: Double,
)

data class DayTotal(
    @ColumnInfo(name = "log_date") val logDate: String,
    val kcal: Double,
)
