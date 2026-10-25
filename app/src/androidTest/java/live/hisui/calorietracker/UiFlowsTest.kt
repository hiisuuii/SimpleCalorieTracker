package live.hisui.calorietracker

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.db.FoodDao
import live.hisui.calorietracker.data.db.LogDao
import live.hisui.calorietracker.data.db.LogEntry
import live.hisui.calorietracker.domain.DayBoundary
import live.hisui.calorietracker.domain.EntrySource
import live.hisui.calorietracker.domain.FoodSource
import live.hisui.calorietracker.domain.Meal
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class UiFlowsTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var foodDao: FoodDao
    @Inject lateinit var logDao: LogDao

    @Before
    fun setUp() {
        hilt.inject()
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext())
    }

    private fun waitForText(text: String) =
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun logAMealFromSearch() {
        runBlocking { foodDao.insert(Food(name = "Rolled oats", source = FoodSource.CUSTOM, kcalPer100g = 380.0)) }

        compose.onNodeWithContentDescription("Log food").performClick()
        compose.onNodeWithText("Search foods").performTextInput("oat")
        waitForText("Rolled oats")
        compose.onNodeWithText("Rolled oats").performClick()
        waitForText("Amount")
        compose.onNodeWithText("Amount").performTextReplacement("50")
        compose.onNodeWithText("Log").performClick()

        waitForText("190 / 2000 kcal")
        compose.onNodeWithText("Rolled oats").assertExists()
    }

    private fun addIngredient(query: String, food: String, grams: String) {
        compose.onNodeWithText("Add food").performClick()
        compose.onNodeWithText("Search foods").performTextInput(query)
        waitForText(food)
        compose.onNodeWithText(food).performClick()
        waitForText("Amount")
        compose.onNodeWithText("Amount").performTextReplacement(grams)
        compose.onNodeWithText("Add to meal").performClick()
        waitForText("Add food")
    }

    @Test
    fun buildAMealAndExpandItInTheLog() {
        runBlocking {
            foodDao.insert(Food(name = "Shredded chicken", source = FoodSource.CUSTOM, kcalPer100g = 165.0))
            foodDao.insert(Food(name = "Hamburger bun", source = FoodSource.CUSTOM, kcalPer100g = 250.0))
        }

        compose.onNodeWithContentDescription("Log food").performClick()
        compose.onNodeWithText("Build a meal").performClick()
        compose.onNodeWithText("Meal name").performTextInput("Chicken sandwich")
        addIngredient("chick", "Shredded chicken", "100")
        addIngredient("bun", "Hamburger bun", "50")
        compose.onNodeWithText("Log meal").performClick()

        waitForText("290 / 2000 kcal")
        compose.onNodeWithText("Shredded chicken").assertDoesNotExist()
        compose.onNodeWithText("Chicken sandwich").performClick()
        waitForText("Shredded chicken")
        compose.onNodeWithText("Hamburger bun").assertExists()
    }

    @Test
    fun editAnEntry() {
        val today = DayBoundary.logDate(Instant.now(), ZoneId.systemDefault(), rolloverHour = 4).toString()
        runBlocking {
            logDao.insert(
                LogEntry(
                    loggedAt = System.currentTimeMillis(), tzId = ZoneId.systemDefault().id, logDate = today,
                    meal = Meal.BREAKFAST, name = "Toast", kcal = 100.0, source = EntrySource.QUICK_ADD,
                ),
            )
        }

        waitForText("Toast")
        compose.onNodeWithText("Toast").performClick()
        compose.onNodeWithText("Calories").performTextReplacement("250")
        compose.onNodeWithText("Save").performClick()

        waitForText("250 / 2000 kcal")
    }

    @Test
    fun changeTheDailyTarget() {
        compose.onNodeWithText("Settings").performClick()
        waitForText("Daily calorie target (kcal)")
        compose.onNodeWithText("Daily calorie target (kcal)").performTextReplacement("2500")
        compose.onNodeWithText("Today").performClick()

        waitForText("0 / 2500 kcal")
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
