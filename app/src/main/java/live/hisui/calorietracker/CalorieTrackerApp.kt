package live.hisui.calorietracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import live.hisui.calorietracker.data.SettingsRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import live.hisui.calorietracker.platform.AppLogger
import live.hisui.calorietracker.platform.Notifications
import live.hisui.calorietracker.platform.ReminderScheduler

@HiltAndroidApp
class CalorieTrackerApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var logger: AppLogger

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        logger.install()
        Notifications.createChannel(this)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            settings.settings.map { it.debugLogging }.distinctUntilChanged().collect(logger::setEnabled)
        }
        scope.launch {
            scheduler.scheduleAll(settings.current(), ExistingWorkPolicy.KEEP)
        }
    }
}
