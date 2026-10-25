package live.hisui.calorietracker.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import live.hisui.calorietracker.ui.add.AddScreen
import live.hisui.calorietracker.ui.barcode.BarcodeScreen
import live.hisui.calorietracker.ui.custom.CustomFoodScreen
import live.hisui.calorietracker.ui.day.DayScreen
import live.hisui.calorietracker.ui.history.HistoryScreen
import live.hisui.calorietracker.ui.portion.PortionScreen
import live.hisui.calorietracker.ui.quickadd.QuickAddScreen
import live.hisui.calorietracker.ui.sentence.SentenceScreen
import live.hisui.calorietracker.ui.settings.AboutScreen
import live.hisui.calorietracker.ui.settings.SettingsScreen

private object Route {
    const val DAY = "day"
    const val PAST_DAY = "day/{date}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val ADD = "add"
    const val BARCODE = "barcode"
    const val PORTION = "portion/{foodId}?source={source}"
    const val QUICK_ADD = "quickadd"
    const val SENTENCE = "sentence"
    const val CUSTOM = "custom?foodId={foodId}&barcode={barcode}"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Route.DAY, "Today", Icons.Default.Home),
    TopLevel(Route.HISTORY, "History", Icons.Default.History),
    TopLevel(Route.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val backToDay = { nav.popBackStack(Route.DAY, inclusive = false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (topLevel.any { it.route == route }) {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(Route.DAY)
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Route.DAY, modifier = Modifier.padding(padding)) {
            composable(Route.DAY) {
                DayScreen(onAdd = { nav.navigate(Route.ADD) }, onBack = null)
            }
            composable(Route.PAST_DAY) {
                DayScreen(onAdd = {}, onBack = { nav.popBackStack() })
            }
            composable(Route.HISTORY) {
                HistoryScreen(onDay = { nav.navigate("day/$it") })
            }
            composable(Route.SETTINGS) {
                SettingsScreen(onAbout = { nav.navigate(Route.ABOUT) })
            }
            composable(Route.ABOUT) {
                AboutScreen(onBack = { nav.popBackStack() })
            }
            composable(Route.ADD) {
                AddScreen(
                    onBack = { nav.popBackStack() },
                    onFood = { nav.navigate("portion/$it") },
                    onQuickAdd = { nav.navigate(Route.QUICK_ADD) },
                    onCustomFood = { nav.navigate("custom") },
                    onSentence = { nav.navigate(Route.SENTENCE) },
                    onScanBarcode = { nav.navigate(Route.BARCODE) },
                    onLogged = { backToDay() },
                )
            }
            composable(Route.BARCODE) {
                BarcodeScreen(
                    onBack = { nav.popBackStack() },
                    onFound = { id -> nav.navigate("portion/$id?source=barcode") { popUpTo(Route.ADD) } },
                    onCreateFood = { code -> nav.navigate("custom?barcode=$code") { popUpTo(Route.ADD) } },
                )
            }
            composable(Route.SENTENCE) {
                SentenceScreen(onBack = { nav.popBackStack() }, onLogged = { backToDay() })
            }
            composable(
                Route.PORTION,
                arguments = listOf(
                    navArgument("foodId") { type = NavType.LongType },
                    navArgument("source") { defaultValue = "manual" },
                ),
            ) {
                PortionScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("custom?foodId=$it") },
                    onLogged = { backToDay() },
                )
            }
            composable(Route.QUICK_ADD) {
                QuickAddScreen(onBack = { nav.popBackStack() }, onLogged = { backToDay() })
            }
            composable(
                Route.CUSTOM,
                arguments = listOf(
                    navArgument("foodId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("barcode") { type = NavType.StringType; nullable = true },
                ),
            ) {
                CustomFoodScreen(
                    onBack = { nav.popBackStack() },
                    onSaved = { id -> nav.navigate("portion/$id") { popUpTo(Route.ADD) } },
                    onDeleted = { nav.popBackStack(Route.ADD, inclusive = false) },
                )
            }
        }
    }
}
