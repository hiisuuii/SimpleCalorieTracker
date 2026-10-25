package live.hisui.calorietracker.ui.sentence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.domain.QuestionKind
import live.hisui.calorietracker.ui.common.MealChips
import live.hisui.calorietracker.ui.common.kcalText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentenceScreen(onBack: () -> Unit, onLogged: () -> Unit, viewModel: SentenceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Describe a meal") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state.stage) {
                Stage.INPUT -> InputStage(state, viewModel)
                Stage.ASKING -> AskingStage(state, viewModel)
                Stage.RESOLVING -> CircularProgressIndicator()
                Stage.CONFIRM -> ConfirmStage(state, viewModel, onLogged)
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun optionLabel(food: Food, all: List<Food>): String {
    val base = food.name.take(32)
    if (all.count { it.name == food.name } < 2) return base
    return "$base (${food.brand ?: food.dataType ?: "saved"})"
}

@Composable
private fun InputStage(state: SentenceUiState, viewModel: SentenceViewModel) {
    OutlinedTextField(
        value = state.text,
        onValueChange = viewModel::setText,
        label = { Text("What did you eat?") },
        placeholder = { Text("chicken breast with rice and broccoli") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = viewModel::submit,
        enabled = state.text.isNotBlank() && !state.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Continue") }
    if (state.busy) CircularProgressIndicator()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskingStage(state: SentenceUiState, viewModel: SentenceViewModel) {
    val question = state.question ?: return
    var typed by remember(question) { mutableStateOf("") }
    Text(question.prompt, style = MaterialTheme.typography.titleLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        question.options.forEach { option ->
            AssistChip(onClick = { viewModel.answer(option) }, label = { Text(option) })
        }
    }
    if (question.kind == QuestionKind.AMOUNT) {
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            label = { Text("Or type an amount (150 g, 1 cup)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { viewModel.answer(typed) }, enabled = typed.isNotBlank()) { Text("OK") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ConfirmStage(state: SentenceUiState, viewModel: SentenceViewModel, onLogged: () -> Unit) {
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(state.items) { index, row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val food = row.resolved.food
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.resolved.pending.item.phrase, style = MaterialTheme.typography.labelMedium)
                            Text(food?.name ?: "No match found", style = MaterialTheme.typography.titleMedium)
                            food?.let {
                                Text(
                                    listOfNotNull(it.brand, "${it.kcalPer100g.toInt()} kcal / 100 g").joinToString(" - "),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.remove(index) }) { Icon(Icons.Default.Close, "Remove") }
                    }
                    row.resolved.searchError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    if (row.resolved.options.size > 1) {
                        if (!row.resolved.confident) Text("Check this match:", style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.resolved.options.take(3).forEachIndexed { optionIndex, option ->
                                FilterChip(
                                    selected = optionIndex == row.resolved.selected,
                                    onClick = { viewModel.selectOption(index, optionIndex) },
                                    label = { Text(optionLabel(option.food, row.resolved.options.map { it.food }), maxLines = 1) },
                                )
                            }
                        }
                    }
                    if (food != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = row.gramsText,
                                onValueChange = { viewModel.setGrams(index, it) },
                                label = { Text("Grams") },
                                isError = row.grams == null,
                                supportingText = if (row.grams == null) ({ Text("Enter an amount") }) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.width(140.dp),
                            )
                            Text(row.kcal?.kcalText() ?: "-", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
    val total = state.items.sumOf { it.kcal ?: 0.0 }
    Text("Total ${total.kcalText()}", style = MaterialTheme.typography.titleMedium)
    MealChips(state.meal, viewModel::setMeal)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = viewModel::backToInput) { Text("Edit text") }
        Button(onClick = { viewModel.logAll(onLogged) }, enabled = state.canLog, modifier = Modifier.weight(1f)) { Text("Log all") }
    }
}
