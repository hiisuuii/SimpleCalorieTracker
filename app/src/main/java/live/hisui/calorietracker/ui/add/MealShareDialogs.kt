package live.hisui.calorietracker.ui.add

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ExportMealDialog(code: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share this meal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Send this code to someone. They can paste it under Import meal.", style = MaterialTheme.typography.bodySmall)
                SelectionContainer { Text(code, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Meal code", code))
                    }) { Text("Copy") }
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, code)
                        context.startActivity(Intent.createChooser(send, "Share meal"))
                    }) { Text("Share") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
fun ImportMealDialog(step: ImportStep, viewModel: AddViewModel) {
    val context = LocalContext.current
    when (step) {
        ImportStep.Idle -> Unit
        is ImportStep.Entering -> {
            var code by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = viewModel::dismissImport,
                title = { Text("Import meal") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text("Meal code") },
                            isError = step.error != null,
                            supportingText = step.error?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            code = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        }) { Text("Paste") }
                    }
                },
                confirmButton = { TextButton(onClick = { viewModel.submitImport(code) }, enabled = code.isNotBlank()) { Text("Import") } },
                dismissButton = { TextButton(onClick = viewModel::dismissImport) { Text("Cancel") } },
            )
        }
        is ImportStep.NameTaken -> {
            var name by remember(step) { mutableStateOf(step.suggestedName) }
            AlertDialog(
                onDismissRequest = viewModel::dismissImport,
                title = { Text("You already have \"${step.meal.name}\"") },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Save the new one as") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmImport(step, name, replace = false) }, enabled = name.isNotBlank()) { Text("Save as new") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = viewModel::dismissImport) { Text("Cancel") }
                        TextButton(onClick = { viewModel.confirmImport(step, step.meal.name, replace = true) }) { Text("Replace existing") }
                    }
                },
            )
        }
    }
}
