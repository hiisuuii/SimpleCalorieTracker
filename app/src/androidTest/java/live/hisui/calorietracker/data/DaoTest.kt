package live.hisui.calorietracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import live.hisui.calorietracker.data.db.AppDatabase
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.db.LogEntry
import live.hisui.calorietracker.domain.EntrySource
import live.hisui.calorietracker.domain.FoodSource
import live.hisui.calorietracker.domain.Meal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun entry(date: String, kcal: Double, foodId: Long? = null) = LogEntry(
        loggedAt = 0,
        tzId = "UTC",
        logDate = date,
        meal = Meal.LUNCH,
        name = "x",
        kcal = kcal,
        source = EntrySource.QUICK_ADD,
        foodId = foodId,
    )

    @Test
    fun dailyTotalsSumPerDate() = runTest {
        val dao = db.logDao()
        dao.insert(entry("2026-10-20", 300.0))
        dao.insert(entry("2026-10-20", 250.5))
        dao.insert(entry("2026-10-21", 100.0))

        assertEquals(550.5, dao.totalForDate("2026-10-20").first(), 0.001)
        assertEquals(0.0, dao.totalForDate("2026-10-22").first(), 0.001)
        assertEquals(
            listOf("2026-10-21" to 100.0, "2026-10-20" to 550.5),
            dao.dailyTotals().first().map { it.logDate to it.kcal },
        )
    }

    @Test
    fun deletingFoodKeepsLogEntryWithSnapshot() = runTest {
        val foodId = db.foodDao().insert(food("Oats"))
        val id = db.logDao().insert(entry("2026-10-20", 150.0, foodId))

        db.foodDao().delete(foodId)

        val kept = db.logDao().byId(id)!!
        assertNull(kept.foodId)
        assertEquals(150.0, kept.kcal, 0.001)
    }

    @Test
    fun ftsSearchMatchesPrefixesAndStaysInSyncWithFoodTable() = runTest {
        val dao = db.foodDao()
        val id = dao.insert(food("Chicken breast, roasted"))
        dao.insert(food("Brown rice, cooked"))

        assertEquals(listOf(id), dao.search("chick*").map { it.id })
        assertEquals(listOf(id), dao.search("breast* roast*").map { it.id })

        dao.delete(id)
        assertEquals(emptyList<Long>(), dao.search("chick*").map { it.id })
    }

    private fun food(name: String) = Food(name = name, source = FoodSource.CUSTOM, kcalPer100g = 100.0)
}
