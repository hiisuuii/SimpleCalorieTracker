package live.hisui.calorietracker.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlin.math.roundToInt
import live.hisui.calorietracker.domain.Reminder
import live.hisui.calorietracker.domain.ReminderKind
import live.hisui.calorietracker.domain.Settings
import live.hisui.calorietracker.platform.BatteryOptimization
import live.hisui.calorietracker.platform.Notifications

@Composable
fun RemindersSection(settings: Settings, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var editingTime by remember { mutableStateOf<Reminder?>(null) }
    var pendingEnable by remember { mutableStateOf<Long?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var restricted by remember { mutableStateOf(BatteryOptimization.isRestricted(context)) }
    var fraction by remember { mutableStateOf((settings.middayFraction * 100).roundToInt().toString()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { restricted = BatteryOptimization.isRestricted(context) }

    fun setEnabled(id: Long, enabled: Boolean) =
        viewModel.update { copy(reminders = reminders.map { if (it.id == id) it.copy(enabled = enabled) else it }) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingEnable?.let { id -> if (granted) setEnabled(id, true) else permissionDenied = true }
        pendingEnable = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Reminders", style = MaterialTheme.typography.titleMedium)
        Text(
            "Each reminder checks what you've logged when it fires. Timing is approximate and can be late under battery saver.",
            style = MaterialTheme.typography.bodySmall,
        )
        settings.reminders.forEach { reminder ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(reminder.label.ifBlank { "Reminder" })
                    if (reminder.kind != ReminderKind.CUSTOM && reminder.minuteOfDay == null) {
                        Text("Time follows your meal times", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = { editingTime = reminder }) { Text(reminder.timeOrDefault(settings).format(TIME_FORMAT)) }
                Switch(
                    checked = reminder.enabled,
                    onCheckedChange = { on ->
                        if (on && !Notifications.canPost(context)) {
                            pendingEnable = reminder.id
                            permissionDenied = false
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            setEnabled(reminder.id, on)
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
                if (reminder.kind == ReminderKind.CUSTOM) {
                    IconButton(onClick = { viewModel.removeReminder(reminder.id) }) { Icon(Icons.Default.Close, "Delete reminder") }
                }
            }
        }
        TextButton(onClick = {
            viewModel.update {
                val id = (reminders.maxOfOrNull { it.id } ?: 0L).coerceAtLeast(CUSTOM_ID_BASE) + 1
                copy(reminders = reminders + Reminder(id, ReminderKind.CUSTOM, minuteOfDay = DEFAULT_CUSTOM_MINUTE, label = "Custom reminder"))
            }
        }) { Text("Add reminder") }

        if (permissionDenied) {
            Text("Notifications are blocked for this app, so reminders can't appear.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { context.startActivity(BatteryOptimization.notificationSettingsIntent(context)) }) {
                Text("Open notification settings")
            }
        }

        OutlinedTextField(
            value = fraction,
            onValueChange = { text ->
                fraction = text
                text.toIntOrNull()?.takeIf { it in 1..100 }?.let { viewModel.update { copy(middayFraction = it / 100.0) } }
            },
            label = { Text("Midday nudge below (% of target)") },
            supportingText = { Text("The midday reminder speaks up only if you've logged less than this share of your target.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Also tell me when I'm over target")
            Switch(settings.notifyWhenOver, { viewModel.update { copy(notifyWhenOver = it) } })
        }

        if (restricted) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Battery optimization is on", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Some phones stop background work for apps they think are idle, which silently stops reminders. " +
                            "Set this app to \"Unrestricted\" or \"Don't optimize\" to keep them reliable.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { context.startActivity(BatteryOptimization.settingsIntent()) }) { Text("Battery settings") }
                        OutlinedButton(onClick = { context.startActivity(BatteryOptimization.appInfoIntent(context)) }) { Text("App info") }
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BatteryOptimization.tipsUrl())))
                    }) { Text("Tips for ${Build.MANUFACTURER} phones") }
                }
            }
        }
    }

    editingTime?.let { reminder ->
        TimeDialog(
            initial = reminder.timeOrDefault(settings),
            onConfirm = { time ->
                viewModel.update {
                    copy(reminders = reminders.map { if (it.id == reminder.id) it.copy(minuteOfDay = time.hour * 60 + time.minute) else it })
                }
                editingTime = null
            },
            onDismiss = { editingTime = null },
        )
    }
}

private const val CUSTOM_ID_BASE = 99L
private const val DEFAULT_CUSTOM_MINUTE = 15 * 60
