package live.hisui.calorietracker.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun DataSection(viewModel: SettingsViewModel) {
    val today = LocalDate.now()
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportJson)
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Data", style = MaterialTheme.typography.titleMedium)
        Text("A backup holds your whole history, foods and saved meals. Keep a copy somewhere safe.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { exportJson.launch("calorie-tracker-backup-$today.json") }, modifier = Modifier.fillMaxWidth()) {
            Text("Export backup (JSON)")
        }
        OutlinedButton(onClick = { exportCsv.launch("calorie-tracker-log-$today.csv") }, modifier = Modifier.fillMaxWidth()) {
            Text("Export log (CSV)")
        }
        OutlinedButton(onClick = { import.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Restore from backup")
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace everything?") },
            text = { Text("Restoring replaces all history, foods and saved meals on this phone with the contents of the file.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importJson(uri)
                    pendingImport = null
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}
