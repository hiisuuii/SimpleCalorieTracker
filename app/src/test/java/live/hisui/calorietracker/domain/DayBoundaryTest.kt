package live.hisui.calorietracker.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DayBoundaryTest {
    private val denver = ZoneId.of("America/Denver")

    private fun dateAt(zone: ZoneId, day: Int, hour: Int, minute: Int, rollover: Int = 4): LocalDate =
        DayBoundary.logDate(ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant(), zone, rollover)

    @Test
    fun beforeMidnightCountsForThatDay() {
        assertEquals(LocalDate.of(2026, 10, 20), dateAt(denver, 20, 23, 59))
    }

    @Test
    fun afterMidnightCountsForPreviousDay() {
        assertEquals(LocalDate.of(2026, 10, 20), dateAt(denver, 21, 0, 30))
    }

    @Test
    fun justBeforeRolloverStillPreviousDay() {
        assertEquals(LocalDate.of(2026, 10, 20), dateAt(denver, 21, 3, 59))
    }

    @Test
    fun atRolloverStartsNewDay() {
        assertEquals(LocalDate.of(2026, 10, 21), dateAt(denver, 21, 4, 0))
    }

    @Test
    fun rolloverHourZeroIsCalendarDay() {
        assertEquals(LocalDate.of(2026, 10, 21), dateAt(denver, 21, 0, 30, rollover = 0))
    }

    @Test
    fun usesTheGivenZoneNotUtc() {
        val instant = ZonedDateTime.of(2026, 10, 21, 2, 0, 0, 0, ZoneId.of("UTC")).toInstant()
        assertEquals(LocalDate.of(2026, 10, 20), DayBoundary.logDate(instant, denver, 4))
        assertEquals(LocalDate.of(2026, 10, 20), DayBoundary.logDate(instant, ZoneId.of("UTC"), 4))
        assertEquals(LocalDate.of(2026, 10, 21), DayBoundary.logDate(instant, ZoneId.of("Asia/Tokyo"), 4))
    }

    @Test
    fun differentZonesSameInstantCanLandOnDifferentDays() {
        val instant = ZonedDateTime.of(2026, 10, 21, 5, 0, 0, 0, ZoneId.of("UTC")).toInstant()
        assertEquals(LocalDate.of(2026, 10, 21), DayBoundary.logDate(instant, ZoneId.of("UTC"), 4))
        assertEquals(LocalDate.of(2026, 10, 20), DayBoundary.logDate(instant, denver, 4))
    }
}
