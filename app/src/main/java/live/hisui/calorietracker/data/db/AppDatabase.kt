package live.hisui.calorietracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        LogEntry::class,
        Food::class,
        FoodFts::class,
        Portion::class,
        Alias::class,
        SavedMeal::class,
        SavedMealItem::class,
    ],
    version = 4,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
    abstract fun foodDao(): FoodDao
    abstract fun aliasDao(): AliasDao
    abstract fun savedMealDao(): SavedMealDao
}
