package live.hisui.calorietracker.platform

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import live.hisui.calorietracker.data.SettingsRepository
import live.hisui.calorietracker.data.db.LogDao
import live.hisui.calorietracker.domain.DayBoundary
import live.hisui.calorietracker.domain.ReminderRules

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val logDao: LogDao,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        val settings = settingsRepository.current()
        val reminder = settings.reminders.firstOrNull { it.id == id && it.enabled } ?: return Result.success()

        val today = DayBoundary.logDate(clock.instant(), clock.zone, settings.rolloverHour).toString()
        ReminderRules.message(reminder, logDao.totalForDateOnce(today), settings)
            ?.let { Notifications.post(applicationContext, id.toInt(), it) }
        scheduler.schedule(reminder, settings)
        return Result.success()
    }

    companion object {
        const val KEY_ID = "reminder_id"
    }
}
