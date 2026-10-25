package live.hisui.calorietracker.domain

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MealTaggerTest {
    private val settings = Settings()

    private fun meal(hour: Int, minute: Int = 0) = MealTagger.mealFor(LocalTime.of(hour, minute), settings)

    @Test
    fun tagsMealsAroundConfiguredTimes() {
        assertEquals(Meal.BREAKFAST, meal(8))
        assertEquals(Meal.BREAKFAST, meal(9, 30))
        assertEquals(Meal.LUNCH, meal(12, 45))
        assertEquals(Meal.DINNER, meal(18, 30))
        assertEquals(Meal.DINNER, meal(20, 30))
    }

    @Test
    fun everythingElseIsSnack() {
        assertEquals(Meal.SNACK, meal(3))
        assertEquals(Meal.SNACK, meal(15, 30))
        assertEquals(Meal.SNACK, meal(23, 30))
    }

    @Test
    fun overlappingWindowsPickNearestMeal() {
        val close = Settings(lunch = LocalTime.of(11, 0), dinner = LocalTime.of(12, 0))
        assertEquals(Meal.LUNCH, MealTagger.mealFor(LocalTime.of(11, 10), close))
        assertEquals(Meal.DINNER, MealTagger.mealFor(LocalTime.of(11, 50), close))
    }
}
