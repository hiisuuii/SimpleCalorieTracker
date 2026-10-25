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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import live.hisui.calorietracker.domain.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onAbout: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val loaded by viewModel.settings.collectAsStateWithLifecycle()
    val settings = loaded
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
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
                Column(modifier = Modifier.weight(1f)) {
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

            RemindersSection(settings, viewModel)

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    viewModel.update { copy(usdaApiKey = it.trim()) }
                },
                label = { Text("USDA API key") },
                supportingText = { Text("Free from api.data.gov. Without one, the shared demo key is used and allows only about 10 lookups an hour.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            DataSection(viewModel)
            TextButton(onClick = onAbout) { Text("About") }
        }

        pickingTime?.let { meal ->
            TimeDialog(
                initial = meal.read(settings),
                onConfirm = { time ->
                    viewModel.update { meal.write(this, time) }
                    pickingTime = null
                },
                onDismiss = { pickingTime = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberTimePickerState(initial.hour, initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(pickerState) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
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
