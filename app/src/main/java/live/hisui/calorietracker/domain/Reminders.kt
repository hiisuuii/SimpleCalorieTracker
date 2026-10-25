package live.hisui.calorietracker.domain

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

enum class ReminderKind { MIDDAY, EVENING, CUSTOM }

@Serializable
data class Reminder(
    val id: Long,
    val kind: ReminderKind,
    val enabled: Boolean = false,
    val minuteOfDay: Int? = null,
    val label: String = "",
) {
    fun timeOrDefault(settings: Settings): LocalTime {
        minuteOfDay?.let { return LocalTime.of(it / 60, it % 60) }
        return when (kind) {
            ReminderKind.MIDDAY -> settings.lunch.plusMinutes(MIDDAY_AFTER_LUNCH_MIN)
            ReminderKind.EVENING -> settings.dinner.plusMinutes(EVENING_AFTER_DINNER_MIN)
            ReminderKind.CUSTOM -> LocalTime.NOON
        }
    }

    companion object {
        const val MIDDAY_ID = 1L
        const val EVENING_ID = 2L
        private const val MIDDAY_AFTER_LUNCH_MIN = 60L
        private const val EVENING_AFTER_DINNER_MIN = 120L

        val defaults = listOf(
            Reminder(MIDDAY_ID, ReminderKind.MIDDAY, label = "Midday check"),
            Reminder(EVENING_ID, ReminderKind.EVENING, label = "Evening check"),
        )
    }
}

data class ReminderMessage(val title: String, val text: String)

object ReminderRules {
    private const val ON_TARGET_TOLERANCE_KCAL = 25

    fun message(reminder: Reminder, loggedKcal: Double, settings: Settings): ReminderMessage? {
        val target = settings.dailyTarget
        val logged = loggedKcal.roundToInt()
        return when (reminder.kind) {
            ReminderKind.MIDDAY -> {
                val expected = (target * settings.middayFraction).roundToInt()
                if (logged < expected) {
                    ReminderMessage("Midday check", "You've logged $logged kcal. Aim for about $expected by now.")
                } else {
                    null
                }
            }
            ReminderKind.EVENING, ReminderKind.CUSTOM -> {
                val title = reminder.label.ifBlank { "Calorie check" }
                val remaining = target - logged
                when {
                    remaining >= ON_TARGET_TOLERANCE_KCAL -> ReminderMessage(title, "$remaining kcal left to reach today's target.")
                    remaining < -ON_TARGET_TOLERANCE_KCAL && settings.notifyWhenOver ->
                        ReminderMessage(title, "You're over today's target by ${-remaining} kcal.")
                    else -> null
                }
            }
        }
    }
}

object ReminderTiming {
    fun delayUntilNext(time: LocalTime, now: ZonedDateTime): Duration {
        val zone = now.zone
        var next = now.toLocalDate().atTime(time).atZone(zone)
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        return Duration.between(now, next)
    }
}
