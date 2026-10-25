package live.hisui.calorietracker.domain

import java.time.LocalTime

object MealTagger {
    private const val MINUTES_BEFORE = 90
    private const val MINUTES_AFTER = 150

    fun mealFor(time: LocalTime, settings: Settings): Meal {
        val minute = time.hour * 60 + time.minute
        val meals = listOf(
            Meal.BREAKFAST to settings.breakfast,
            Meal.LUNCH to settings.lunch,
            Meal.DINNER to settings.dinner,
        )
        return meals
            .filter { (_, at) ->
                val offset = minute - (at.hour * 60 + at.minute)
                offset in -MINUTES_BEFORE..MINUTES_AFTER
            }
            .minByOrNull { (_, at) -> kotlin.math.abs(minute - (at.hour * 60 + at.minute)) }
            ?.first ?: Meal.SNACK
    }
}
