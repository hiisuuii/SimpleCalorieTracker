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
import live.hisui.calorietracker.platform.Notifications
import live.hisui.calorietracker.ui.AppNav
import live.hisui.calorietracker.ui.theme.CalorieTrackerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var requestedRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) requestedRoute = intent.getStringExtra(Notifications.EXTRA_ROUTE)
        setContent {
            CalorieTrackerTheme {
                AppNav(requestedRoute = requestedRoute, onRouteHandled = { requestedRoute = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedRoute = intent.getStringExtra(Notifications.EXTRA_ROUTE)
    }
}
