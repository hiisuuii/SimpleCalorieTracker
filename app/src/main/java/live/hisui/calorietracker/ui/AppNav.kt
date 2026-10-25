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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import live.hisui.calorietracker.data.LogTarget
import live.hisui.calorietracker.ui.add.AddScreen
import live.hisui.calorietracker.ui.barcode.BarcodeScreen
import live.hisui.calorietracker.ui.build.BuildMealScreen
import live.hisui.calorietracker.ui.custom.CustomFoodScreen
import live.hisui.calorietracker.ui.day.DayScreen
import live.hisui.calorietracker.ui.history.HistoryScreen
import live.hisui.calorietracker.ui.label.LabelScreen
import live.hisui.calorietracker.ui.portion.PortionScreen
import live.hisui.calorietracker.ui.quickadd.QuickAddScreen
import live.hisui.calorietracker.ui.settings.AboutScreen
import live.hisui.calorietracker.ui.settings.SettingsScreen

private object Route {
    const val DAY = "day"
    const val PAST_DAY = "day/{date}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val ADD = "add?pick={pick}"
    const val BUILD = "build"
    const val BARCODE = "barcode"
    const val LABEL = "label?barcode={barcode}"
    const val PORTION = "portion/{foodId}?source={source}&pick={pick}"
    const val QUICK_ADD = "quickadd"
    const val CUSTOM = "custom?foodId={foodId}&barcode={barcode}"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Route.DAY, "Today", Icons.Default.Home),
    TopLevel(Route.HISTORY, "History", Icons.Default.History),
    TopLevel(Route.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
fun AppNav(requestedRoute: String?, onRouteHandled: () -> Unit, logTarget: LogTarget) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    // Logging started from a past day returns to that day, not to Today.
    val backToDay = {
        val pastDay = logTarget.date != null && nav.popBackStack(Route.PAST_DAY, inclusive = false)
        if (!pastDay) nav.popBackStack(Route.DAY, inclusive = false)
    }

    LaunchedEffect(requestedRoute) {
        if (requestedRoute != null) {
            nav.navigate(requestedRoute) { popUpTo(Route.DAY) }
            onRouteHandled()
        }
    }

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
                DayScreen(onAdd = { nav.navigate("add") }, onBack = null)
            }
            composable(Route.PAST_DAY) {
                DayScreen(onAdd = { nav.navigate("add") }, onBack = { nav.popBackStack() })
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
            composable(Route.ADD, arguments = listOf(navArgument("pick") { type = NavType.BoolType; defaultValue = false })) { entry ->
                val picking = entry.arguments?.getBoolean("pick") ?: false
                AddScreen(
                    onBack = { nav.popBackStack() },
                    onFood = { nav.navigate("portion/$it?pick=$picking") },
                    onQuickAdd = { nav.navigate(Route.QUICK_ADD) },
                    onCustomFood = { nav.navigate("custom") },
                    onBuildMeal = { nav.navigate(Route.BUILD) },
                    onScanBarcode = { nav.navigate(Route.BARCODE) },
                    onScanLabel = { nav.navigate("label") },
                    onLogged = { backToDay() },
                    picking = picking,
                )
            }
            composable(Route.BUILD) {
                BuildMealScreen(
                    onBack = { nav.popBackStack() },
                    onAddIngredient = { nav.navigate("add?pick=true") },
                    onLogged = { backToDay() },
                )
            }
            composable(Route.BARCODE) {
                BarcodeScreen(
                    onBack = { nav.popBackStack() },
                    onFound = { id -> nav.navigate("portion/$id?source=barcode") { popUpTo(Route.ADD) } },
                    onCreateFood = { code -> nav.navigate("custom?barcode=$code") { popUpTo(Route.ADD) } },
                    onScanLabel = { code -> nav.navigate("label?barcode=$code") { popUpTo(Route.ADD) } },
                )
            }
            composable(
                Route.LABEL,
                arguments = listOf(navArgument("barcode") { type = NavType.StringType; nullable = true }),
            ) {
                LabelScreen(
                    onBack = { nav.popBackStack() },
                    onSaved = { id -> nav.navigate("portion/$id?source=label") { popUpTo(Route.ADD) } },
                )
            }
            composable(
                Route.PORTION,
                arguments = listOf(
                    navArgument("foodId") { type = NavType.LongType },
                    navArgument("source") { defaultValue = "manual" },
                    navArgument("pick") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val picking = entry.arguments?.getBoolean("pick") ?: false
                PortionScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("custom?foodId=$it") },
                    onLogged = { if (picking) nav.popBackStack(Route.BUILD, inclusive = false) else backToDay() },
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
