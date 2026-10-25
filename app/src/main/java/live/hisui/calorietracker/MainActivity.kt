package live.hisui.calorietracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import live.hisui.calorietracker.data.LogTarget
import live.hisui.calorietracker.platform.Notifications
import live.hisui.calorietracker.ui.AppNav
import live.hisui.calorietracker.ui.theme.CalorieTrackerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var logTarget: LogTarget

    private var requestedRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openRoute(intent)
        setContent {
            CalorieTrackerTheme {
                AppNav(requestedRoute = requestedRoute, onRouteHandled = { requestedRoute = null }, logTarget = logTarget)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRoute(intent)
    }

    // A notification starts logging "now", so any past-day target left from earlier must not apply.
    private fun openRoute(intent: Intent) {
        requestedRoute = intent.getStringExtra(Notifications.EXTRA_ROUTE)
        if (requestedRoute != null) logTarget.date = null
    }
}
