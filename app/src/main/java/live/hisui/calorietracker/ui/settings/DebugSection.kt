package live.hisui.calorietracker.ui.settings

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import live.hisui.calorietracker.platform.LogFiles

@Composable
fun DebugSection(enabled: Boolean, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Troubleshooting", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Save a debug log")
                Text(
                    "Records what the app does so a problem can be diagnosed. Files are kept in ${LogFiles.FOLDER}, " +
                        "where any file manager can open them. The last ${LogFiles.KEEP} sessions are kept.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(enabled, { viewModel.update { copy(debugLogging = it) } })
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val uris = viewModel.logFiles()
                    if (uris.isEmpty()) return@launch
                    val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "text/plain"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                        clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Share logs"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Export logs") }
    }
}
