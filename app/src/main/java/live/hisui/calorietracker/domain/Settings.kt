package live.hisui.calorietracker.domain

import java.time.LocalTime

data class Settings(
    val dailyTarget: Int = 2000,
    val useOunces: Boolean = false,
    val rolloverHour: Int = 4,
    val breakfast: LocalTime = LocalTime.of(8, 0),
    val lunch: LocalTime = LocalTime.of(12, 30),
    val dinner: LocalTime = LocalTime.of(18, 30),
    val usdaApiKey: String = "",
    val reminders: List<Reminder> = Reminder.defaults,
    val middayFraction: Double = 0.3,
    val notifyWhenOver: Boolean = false,
    val debugLogging: Boolean = false,
)
