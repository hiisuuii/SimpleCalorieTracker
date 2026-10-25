package live.hisui.calorietracker.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import live.hisui.calorietracker.data.db.LogEntry
import live.hisui.calorietracker.data.db.withGrams
import live.hisui.calorietracker.domain.Meal
import live.hisui.calorietracker.ui.common.MealChips
import live.hisui.calorietracker.ui.common.friendly
import live.hisui.calorietracker.ui.common.gramsText
import live.hisui.calorietracker.ui.common.kcalText
import live.hisui.calorietracker.ui.common.label
import live.hisui.calorietracker.ui.common.macroText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(onAdd: () -> Unit, onBack: (() -> Unit)?, viewModel: DayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<LogEntry?>(null) }
    var savingMeal by remember { mutableStateOf<List<LogEntry>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isToday) "Today" else state.date?.friendly().orEmpty()) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.isToday) {
                FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "Log food") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(padding),
        ) {
            item { SummaryCard(state) }
            Meal.entries.forEach { meal ->
                val rows = state.entries.filter { it.meal == meal }
                if (rows.isNotEmpty()) {
                    item(key = "header-$meal") {
                        MealHeader(meal, rows.sumOf { it.kcal }, onSaveAsMeal = { savingMeal = rows })
                    }
                    items(rows, key = { it.id }) { entry ->
                        EntryRow(entry, state.useOunces, onClick = { editing = entry })
                    }
                }
            }
            if (state.entries.isEmpty()) {
                item { Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }

    editing?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = {
                viewModel.delete(entry)
                editing = null
            },
        )
    }
    savingMeal?.let { rows ->
        SaveMealDialog(
            onDismiss = { savingMeal = null },
            onSave = {
                viewModel.saveAsMeal(it, rows)
                savingMeal = null
            },
        )
    }
}

@Composable
private fun SummaryCard(state: DayUiState) {
    val remaining = state.target - state.total
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${state.total.roundToInt()} / ${state.target} kcal", style = MaterialTheme.typography.headlineSmall)
            LinearProgressIndicator(
                progress = { (state.total / state.target.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (remaining >= 0) "${remaining.roundToInt()} kcal left" else "${(-remaining).roundToInt()} kcal over",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MealHeader(meal: Meal, kcal: Double, onSaveAsMeal: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${meal.label} - ${kcal.kcalText()}", style = MaterialTheme.typography.titleMedium)
        Column {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Meal options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Save as meal") },
                    onClick = {
                        menu = false
                        onSaveAsMeal()
                    },
                )
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LogEntry, useOunces: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge)
            entry.grams?.let { Text(it.gramsText(useOunces), style = MaterialTheme.typography.bodySmall) }
        }
        Text(entry.kcal.kcalText(), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EditEntryDialog(
    entry: LogEntry,
    onDismiss: () -> Unit,
    onSave: (LogEntry) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(entry.name) }
    var grams by remember { mutableStateOf(entry.grams?.roundToInt()?.toString().orEmpty()) }
    var kcal by remember { mutableStateOf(entry.kcal.roundToInt().toString()) }
    var meal by remember { mutableStateOf(entry.meal) }
    var showMacros by remember { mutableStateOf(false) }
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                if (entry.grams != null) {
                    OutlinedTextField(
                        value = grams,
                        onValueChange = { text ->
                            grams = text
                            text.toDoubleOrNull()?.let { kcal = entry.withGrams(it).kcal.roundToInt().toString() }
                        },
                        label = { Text("Grams") },
                        keyboardOptions = numeric,
                        singleLine = true,
                    )
                }
                OutlinedTextField(kcal, { kcal = it }, label = { Text("Calories") }, keyboardOptions = numeric, singleLine = true)
                MealChips(meal, { meal = it })
                if (entry.proteinG != null || entry.carbsG != null || entry.fatG != null) {
                    TextButton(onClick = { showMacros = !showMacros }) { Text(if (showMacros) "Hide macros" else "Show macros") }
                    if (showMacros) {
                        Text(
                            "Protein ${entry.proteinG?.macroText() ?: "-"} g, carbs ${entry.carbsG?.macroText() ?: "-"} g, " +
                                "fat ${entry.fatG?.macroText() ?: "-"} g",
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val g = grams.toDoubleOrNull()
                val base = if (entry.grams != null && g != null && g > 0) entry.withGrams(g) else entry
                onSave(base.copy(name = name.ifBlank { entry.name }, kcal = kcal.toDoubleOrNull() ?: base.kcal, meal = meal))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun SaveMealDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as meal") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Meal name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
