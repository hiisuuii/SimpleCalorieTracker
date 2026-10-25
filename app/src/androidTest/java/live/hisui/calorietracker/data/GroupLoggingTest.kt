package live.hisui.calorietracker.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import live.hisui.calorietracker.data.db.AppDatabase
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.domain.FoodSource
import live.hisui.calorietracker.domain.Meal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupLoggingTest {
    private lateinit var db: AppDatabase
    private lateinit var log: LogRepository
    private lateinit var savedMeals: SavedMealRepository
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { context.cacheDir.resolve("group-test-${System.nanoTime()}.preferences_pb") },
            Json,
        )
        log = LogRepository(db.logDao(), db.foodDao(), settings, Clock.systemDefaultZone())
        savedMeals = SavedMealRepository(db.savedMealDao(), FoodRepository(db.foodDao(), OfflineUsda, settings), log)
    }

    @After
    fun tearDown() {
        db.close()
        scope.coroutineContext[Job]?.cancel()
    }

    private suspend fun food(name: String, kcal: Double): Food {
        val id = db.foodDao().insert(Food(name = name, source = FoodSource.CUSTOM, kcalPer100g = kcal))
        return db.foodDao().byId(id)!!
    }

    @Test
    fun aLoggedGroupCountsEachFoodOnceAndDeletesTogether() = runTest {
        val chicken = food("Chicken", 165.0)
        val bun = food("Bun", 250.0)
        val today = log.currentLogDate.first()

        log.logGroup("Sandwich", listOf(chicken to 100.0, bun to 50.0), Meal.LUNCH)

        val entries = log.entriesForDate(today).first()
        assertEquals(listOf("Chicken", "Bun"), entries.map { it.name })
        assertEquals(1, entries.map { it.groupId }.toSet().size)
        assertEquals("Sandwich", entries.first().groupName)
        assertEquals(290.0, log.totalForDate(today).first(), 0.001)

        log.deleteGroup(entries.first().groupId!!)
        assertEquals(0, log.entriesForDate(today).first().size)
        assertEquals(0.0, log.totalForDate(today).first(), 0.001)
    }

    @Test
    fun loggingASavedMealCreatesAGroupNamedAfterIt() = runTest {
        val chicken = food("Chicken", 165.0)
        val bun = food("Bun", 250.0)
        val today = log.currentLogDate.first()
        savedMeals.create("Chicken sandwich", listOf(chicken to 100.0, bun to 50.0))

        savedMeals.log(savedMeals.meals.first().single(), Meal.DINNER)
        savedMeals.log(savedMeals.meals.first().single(), Meal.DINNER)

        val entries = log.entriesForDate(today).first()
        assertEquals(4, entries.size)
        assertEquals(setOf("Chicken sandwich"), entries.map { it.groupName }.toSet())
        assertEquals(2, entries.map { it.groupId }.toSet().size)
        assertEquals(580.0, log.totalForDate(today).first(), 0.001)
    }
}
