package live.hisui.calorietracker.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object DayBoundary {
    fun logDate(at: Instant, zone: ZoneId, rolloverHour: Int): LocalDate =
        at.atZone(zone).toLocalDateTime().minusHours(rolloverHour.toLong()).toLocalDate()
}
