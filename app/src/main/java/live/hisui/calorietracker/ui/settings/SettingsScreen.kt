package live.hisui.calorietracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import live.hisui.calorietracker.data.SettingsRepository
import live.hisui.calorietracker.domain.Settings

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<Settings?> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(change: Settings.() -> Settings) {
        viewModelScope.launch { repo.update(repo.current().change()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onAbout: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val loaded by viewModel.settings.collectAsStateWithLifecycle()
    val settings = loaded
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        if (settings == null) return@Scaffold
        var target by remember { mutableStateOf(settings.dailyTarget.toString()) }
        var apiKey by remember { mutableStateOf(settings.usdaApiKey) }
        var pickingTime by remember { mutableStateOf<MealTime?>(null) }

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = target,
                onValueChange = { text ->
                    target = text
                    text.toIntOrNull()?.takeIf { it > 0 }?.let { viewModel.update { copy(dailyTarget = it) } }
                },
                label = { Text("Daily calorie target (kcal)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Show weights in ounces")
                Switch(settings.useOunces, { viewModel.update { copy(useOunces = it) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Day starts at")
                    Text(
                        "Entries before this hour count toward the previous day",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { viewModel.update { copy(rolloverHour = (rolloverHour - 1).coerceAtLeast(0)) } },
                        enabled = settings.rolloverHour > 0,
                    ) { Text("-") }
                    Text(LocalTime.of(settings.rolloverHour, 0).format(TIME_FORMAT))
                    TextButton(
                        onClick = { viewModel.update { copy(rolloverHour = (rolloverHour + 1).coerceAtMost(MAX_ROLLOVER_HOUR)) } },
                        enabled = settings.rolloverHour < MAX_ROLLOVER_HOUR,
                    ) { Text("+") }
                }
            }
            Text("Typical meal times", style = MaterialTheme.typography.titleMedium)
            MealTime.entries.forEach { meal ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(meal.label)
                    OutlinedButton(onClick = { pickingTime = meal }) { Text(meal.read(settings).format(TIME_FORMAT)) }
                }
            }
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    viewModel.update { copy(usdaApiKey = it.trim()) }
                },
                label = { Text("USDA API key") },
                supportingText = { Text("Free from api.data.gov. Without one, the shared demo key is used and is heavily rate limited.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onAbout) { Text("About") }
        }

        pickingTime?.let { meal ->
            val current = meal.read(settings)
            val pickerState = rememberTimePickerState(current.hour, current.minute, is24Hour = false)
            AlertDialog(
                onDismissRequest = { pickingTime = null },
                text = { TimePicker(pickerState) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.update { meal.write(this, LocalTime.of(pickerState.hour, pickerState.minute)) }
                        pickingTime = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { pickingTime = null }) { Text("Cancel") } },
            )
        }
    }
}

private val TIME_FORMAT = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private const val MAX_ROLLOVER_HOUR = 12

private enum class MealTime(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    ;

    fun read(s: Settings): LocalTime = when (this) {
        BREAKFAST -> s.breakfast
        LUNCH -> s.lunch
        DINNER -> s.dinner
    }

    fun write(s: Settings, time: LocalTime): Settings = when (this) {
        BREAKFAST -> s.copy(breakfast = time)
        LUNCH -> s.copy(lunch = time)
        DINNER -> s.copy(dinner = time)
    }
}
