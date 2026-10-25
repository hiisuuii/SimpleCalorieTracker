package live.hisui.calorietracker.domain

import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderRulesTest {
    private val settings = Settings(dailyTarget = 3000)
    private val midday = Reminder(1, ReminderKind.MIDDAY)
    private val evening = Reminder(2, ReminderKind.EVENING)
    private val custom = Reminder(100, ReminderKind.CUSTOM, label = "Snack check")

    @Test
    fun middayNudgesWhenBehindTheConfiguredFraction() {
        val message = ReminderRules.message(midday, 500.0, settings)!!
        assertEquals("You've logged 500 kcal. Aim for about 900 by now.", message.text)
    }

    @Test
    fun middayStaysQuietWhenAlreadyAhead() {
        assertNull(ReminderRules.message(midday, 900.0, settings))
        assertNull(ReminderRules.message(midday, 1500.0, settings))
    }

    @Test
    fun middayFractionIsConfigurable() {
        val strict = settings.copy(middayFraction = 0.5)
        assertEquals("You've logged 1000 kcal. Aim for about 1500 by now.", ReminderRules.message(midday, 1000.0, strict)!!.text)
    }

    @Test
    fun eveningReportsRemainingCalories() {
        val message = ReminderRules.message(evening, 2570.0, settings)!!
        assertEquals("430 kcal left to reach today's target.", message.text)
    }

    @Test
    fun customReminderUsesItsLabelAsTitle() {
        assertEquals("Snack check", ReminderRules.message(custom, 1000.0, settings)!!.title)
    }

    @Test
    fun onTargetPostsNothing() {
        assertNull(ReminderRules.message(evening, 2990.0, settings))
        assertNull(ReminderRules.message(evening, 3010.0, settings))
    }

    @Test
    fun overTargetIsSilentUnlessEnabled() {
        assertNull(ReminderRules.message(evening, 3400.0, settings))
        val nag = settings.copy(notifyWhenOver = true)
        assertEquals("You're over today's target by 400 kcal.", ReminderRules.message(evening, 3400.0, nag)!!.text)
    }

    @Test
    fun defaultTimesDeriveFromMealTimes() {
        assertEquals(LocalTime.of(13, 30), midday.timeOrDefault(Settings()))
        assertEquals(LocalTime.of(20, 30), evening.timeOrDefault(Settings()))
        assertEquals(LocalTime.of(9, 15), evening.copy(minuteOfDay = 9 * 60 + 15).timeOrDefault(Settings()))
    }
}

class ReminderTimingTest {
    private val denver = ZoneId.of("America/Denver")

    @Test
    fun laterTodayWaitsUntilToday() {
        val now = ZonedDateTime.of(2026, 10, 20, 12, 0, 0, 0, denver)
        assertEquals(Duration.ofHours(8).plusMinutes(30), ReminderTiming.delayUntilNext(LocalTime.of(20, 30), now))
    }

    @Test
    fun alreadyPassedWaitsUntilTomorrow() {
        val now = ZonedDateTime.of(2026, 10, 20, 21, 0, 0, 0, denver)
        assertEquals(Duration.ofHours(23).plusMinutes(30), ReminderTiming.delayUntilNext(LocalTime.of(20, 30), now))
    }

    @Test
    fun exactlyNowWaitsAFullDay() {
        val now = ZonedDateTime.of(2026, 10, 20, 20, 30, 0, 0, denver)
        assertEquals(Duration.ofHours(24), ReminderTiming.delayUntilNext(LocalTime.of(20, 30), now))
    }

    @Test
    fun clockChangeDayStillLandsOnWallClockTime() {
        val now = ZonedDateTime.of(2026, 10, 31, 21, 0, 0, 0, denver)
        val delay = ReminderTiming.delayUntilNext(LocalTime.of(20, 30), now)
        assertEquals(Duration.ofHours(24).plusMinutes(30), delay)
    }
}
