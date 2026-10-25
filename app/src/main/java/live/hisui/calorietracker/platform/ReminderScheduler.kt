package live.hisui.calorietracker.platform

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import live.hisui.calorietracker.domain.Reminder
import live.hisui.calorietracker.domain.ReminderTiming
import live.hisui.calorietracker.domain.Settings

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    // At app start use KEEP: a REPLACE would cancel a reminder job that is cold-starting this very process.
    fun scheduleAll(settings: Settings, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val work = WorkManager.getInstance(context)
        settings.reminders.forEach { reminder ->
            if (reminder.enabled) schedule(reminder, settings, policy) else work.cancelUniqueWork(workName(reminder.id))
        }
    }

    // REPLACE is safe when a worker calls this for itself because it is the last thing doWork does.
    fun schedule(reminder: Reminder, settings: Settings, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val delay = ReminderTiming.delayUntilNext(reminder.timeOrDefault(settings), clock.instant().atZone(clock.zone))
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(ReminderWorker.KEY_ID, reminder.id).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(reminder.id), policy, request)
    }

    fun cancel(id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    private fun workName(id: Long) = "reminder-$id"
}
