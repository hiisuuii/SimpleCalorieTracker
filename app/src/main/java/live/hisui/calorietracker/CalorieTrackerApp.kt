package live.hisui.calorietracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import live.hisui.calorietracker.data.SettingsRepository
import live.hisui.calorietracker.platform.Notifications
import live.hisui.calorietracker.platform.ReminderScheduler

@HiltAndroidApp
class CalorieTrackerApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var scheduler: ReminderScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { scheduler.scheduleAll(settings.current()) }
    }
}
