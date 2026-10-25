package live.hisui.calorietracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import live.hisui.calorietracker.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Calorie Tracker ${BuildConfig.VERSION_NAME}")
            Text("Everything you log stays on this phone. The app has no accounts, analytics or tracking.")
            Text("This app is a tracker, not medical advice. Choose a calorie target that suits you.")
            Text("Nutrition data comes from USDA FoodData Central (public domain) and Open Food Facts.")
            Text("Open Food Facts data is available under the Open Database License. Thanks to its contributors.")
            TextButton(onClick = { uriHandler.openUri("https://fdc.nal.usda.gov") }) { Text("USDA FoodData Central") }
            TextButton(onClick = { uriHandler.openUri("https://world.openfoodfacts.org") }) { Text("Open Food Facts") }
            TextButton(onClick = { uriHandler.openUri("https://opendatacommons.org/licenses/odbl/1-0/") }) { Text("Open Database License") }
            Text("Released under the MIT license.")
        }
    }
}
