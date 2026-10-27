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
    private lateinit var target: LogTarget
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
        target = LogTarget()
        log = LogRepository(db.logDao(), db.foodDao(), settings, Clock.systemDefaultZone(), target)
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
    fun renamingAGroupRenamesEveryFoodInItAndNothingElse() = runTest {
        val chicken = food("Chicken", 165.0)
        val bun = food("Bun", 250.0)
        val today = log.currentLogDate.first()
        log.logGroup("Sandwich", listOf(chicken to 100.0, bun to 50.0), Meal.LUNCH)
        log.logGroup("Salad", listOf(chicken to 80.0), Meal.DINNER)
        val sandwichId = log.entriesForDate(today).first().first { it.groupName == "Sandwich" }.groupId!!

        log.renameGroup(sandwichId, "Chicken sandwich")

        val byName = log.entriesForDate(today).first().groupBy { it.groupName }
        assertEquals(2, byName.getValue("Chicken sandwich").size)
        assertEquals(1, byName.getValue("Salad").size)
        assertEquals(290.0 + 132.0, log.totalForDate(today).first(), 0.001)
    }

    @Test
    fun loggingFromAPastDayLandsOnThatDayAndClearingTheTargetReturnsToNow() = runTest {
        val bun = food("Bun", 250.0)
        val today = log.currentLogDate.first()
        val past = today.minusDays(3)

        target.date = past
        log.quickAdd("Forgotten dinner", 600.0, Meal.DINNER)
        log.logFood(bun, 100.0, Meal.DINNER)
        log.logGroup("Sandwich", listOf(bun to 50.0), Meal.DINNER)
        target.date = null
        log.quickAdd("Snack now", 50.0, Meal.SNACK)

        assertEquals(3, log.entriesForDate(past).first().size)
        assertEquals(listOf("Snack now"), log.entriesForDate(today).first().map { it.name })
        assertEquals(975.0, log.totalForDate(past).first(), 0.001)
    }

    @Test
    fun movingAGroupMovesEveryFoodInItToTheOtherDay() = runTest {
        val chicken = food("Chicken", 165.0)
        val bun = food("Bun", 250.0)
        val today = log.currentLogDate.first()
        log.logGroup("Sandwich", listOf(chicken to 100.0, bun to 50.0), Meal.LUNCH)
        val groupId = log.entriesForDate(today).first().first().groupId!!

        log.moveGroup(groupId, today.minusDays(1))

        assertEquals(0, log.entriesForDate(today).first().size)
        assertEquals(2, log.entriesForDate(today.minusDays(1)).first().size)
        assertEquals(290.0, log.totalForDate(today.minusDays(1)).first(), 0.001)
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

    @Test
    fun loggingPartOfAMealWithATotalWeightScalesEveryFood() = runTest {
        val chicken = food("Chicken", 165.0)
        val bun = food("Bun", 250.0)
        val today = log.currentLogDate.first()
        savedMeals.create("Stew", listOf(chicken to 400.0, bun to 200.0), totalGrams = 1000.0)

        savedMeals.log(savedMeals.meals.first().single(), Meal.DINNER, eatenGrams = 250.0)

        val entries = log.entriesForDate(today).first()
        assertEquals(listOf(100.0, 50.0), entries.map { it.grams })
        assertEquals(290.0, log.totalForDate(today).first(), 0.001)
    }

    @Test
    fun editingASavedMealReplacesItsFoodsNameAndTotalWeight() = runTest {
        val chicken = food("Chicken", 165.0)
        val bun = food("Bun", 250.0)
        savedMeals.create("Sandwich", listOf(chicken to 100.0), totalGrams = null)
        val id = savedMeals.meals.first().single().id

        savedMeals.update(id, "Chicken sandwich", listOf(chicken to 120.0, bun to 50.0), totalGrams = 180.0)

        val meal = savedMeals.get(id)!!
        assertEquals("Chicken sandwich", meal.name)
        assertEquals(180.0, meal.totalGrams!!, 0.001)
        assertEquals(listOf(120.0, 50.0), savedMeals.foodsOf(id).map { it.second })
    }
}
