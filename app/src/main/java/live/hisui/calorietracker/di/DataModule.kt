package live.hisui.calorietracker.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import live.hisui.calorietracker.data.db.AppDatabase
import live.hisui.calorietracker.data.db.MIGRATION_1_2
import live.hisui.calorietracker.data.db.MIGRATION_2_3
import live.hisui.calorietracker.data.db.MIGRATION_3_4

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "calorie_tracker.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides
    fun logDao(db: AppDatabase) = db.logDao()

    @Provides
    fun foodDao(db: AppDatabase) = db.foodDao()

    @Provides
    fun aliasDao(db: AppDatabase) = db.aliasDao()

    @Provides
    fun savedMealDao(db: AppDatabase) = db.savedMealDao()

    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsStore

    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}
