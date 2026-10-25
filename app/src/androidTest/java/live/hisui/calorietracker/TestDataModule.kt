package live.hisui.calorietracker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import live.hisui.calorietracker.data.db.AppDatabase
import live.hisui.calorietracker.di.DataModule

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object TestDataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

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
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + Job())) {
            context.cacheDir.resolve("ui-test-${System.nanoTime()}.preferences_pb")
        }

    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}
