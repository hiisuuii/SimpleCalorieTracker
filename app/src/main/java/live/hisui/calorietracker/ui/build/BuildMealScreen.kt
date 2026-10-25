package live.hisui.calorietracker.ui.build

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import live.hisui.calorietracker.ui.common.MealChips
import live.hisui.calorietracker.ui.common.kcalText
import live.hisui.calorietracker.ui.common.macroText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildMealScreen(
    onBack: () -> Unit,
    onAddIngredient: () -> Unit,
    onLogged: () -> Unit,
    viewModel: BuildMealViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(state.name) }
    var editing by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Build a meal") },
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
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    viewModel.setName(it)
                },
                label = { Text("Meal name") },
                placeholder = { Text("Chicken sandwich") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Foods in this meal", style = MaterialTheme.typography.titleMedium)
            if (state.items.isEmpty()) Text("Nothing added yet.", style = MaterialTheme.typography.bodyMedium)
            state.items.forEachIndexed { index, item ->
                val kcal = item.food.kcalPer100g * item.grams / 100
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = index },
                ) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(item.food.name, style = MaterialTheme.typography.bodyLarge)
                        Text("${item.grams.roundToInt()} g - ${kcal.kcalText()}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { viewModel.remove(index) }) { Icon(Icons.Default.Close, "Remove ${item.food.name}") }
                }
            }
            OutlinedButton(onClick = onAddIngredient, modifier = Modifier.fillMaxWidth()) { Text("Add food") }

            if (state.items.isNotEmpty()) {
                val total = state.total
                Text("Total ${total.kcal.kcalText()}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Protein ${total.proteinG?.macroText() ?: "-"} g, carbs ${total.carbsG?.macroText() ?: "-"} g, fat ${total.fatG?.macroText() ?: "-"} g",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            MealChips(state.slot, viewModel::setSlot)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(state.saveForReuse, viewModel::setSaveForReuse)
                Text("Save to my meals so I can log it again in one tap")
            }
            Button(onClick = { viewModel.logMeal(onLogged) }, enabled = state.canLog, modifier = Modifier.fillMaxWidth()) { Text("Log meal") }
            if (state.items.isNotEmpty() || name.isNotBlank()) {
                TextButton(
                    onClick = {
                        name = ""
                        viewModel.startOver()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start over") }
            }
        }
    }

    editing?.let { index ->
        state.items.getOrNull(index)?.let { item ->
            var text by remember(index) { mutableStateOf(item.grams.roundToInt().toString()) }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(item.food.name) },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Grams") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        text.toDoubleOrNull()?.takeIf { it > 0 }?.let { viewModel.setGrams(index, it) }
                        editing = null
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
            )
        }
    }
}
