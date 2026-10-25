package live.hisui.calorietracker.ui.custom

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFoodScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onDeleted: () -> Unit,
    viewModel: CustomFoodViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val editing = state.existing != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Edit food" else "Custom food") },
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
                state.name,
                { v -> viewModel.edit { copy(name = v) } },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                state.brand,
                { v -> viewModel.edit { copy(brand = v) } },
                label = { Text("Brand (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!editing) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    EntryMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { viewModel.edit { copy(mode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index, EntryMode.entries.size),
                        ) { Text(mode.label, maxLines = 1) }
                    }
                }
            }
            when (state.mode) {
                EntryMode.PER_100G -> Unit
                EntryMode.PER_SERVING -> OutlinedTextField(
                    state.weight,
                    { v -> viewModel.edit { copy(weight = v) } },
                    label = { Text("Serving size (g)") },
                    keyboardOptions = numeric,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                EntryMode.RECIPE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        state.weight,
                        { v -> viewModel.edit { copy(weight = v) } },
                        label = { Text("Total weight (g)") },
                        keyboardOptions = numeric,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        state.servings,
                        { v -> viewModel.edit { copy(servings = v) } },
                        label = { Text("Servings") },
                        keyboardOptions = numeric,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val basis = when (state.mode) {
                EntryMode.PER_100G -> "per 100 g"
                EntryMode.PER_SERVING -> "per serving"
                EntryMode.RECIPE -> "for the whole recipe"
            }
            OutlinedTextField(
                state.kcal,
                { v -> viewModel.edit { copy(kcal = v) } },
                label = { Text("Calories $basis") },
                keyboardOptions = numeric,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    state.protein,
                    { v -> viewModel.edit { copy(protein = v) } },
                    label = { Text("Protein g") },
                    keyboardOptions = numeric,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    state.carbs,
                    { v -> viewModel.edit { copy(carbs = v) } },
                    label = { Text("Carbs g") },
                    keyboardOptions = numeric,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    state.fat,
                    { v -> viewModel.edit { copy(fat = v) } },
                    label = { Text("Fat g") },
                    keyboardOptions = numeric,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Text("Portions", style = MaterialTheme.typography.titleMedium)
            state.portions.forEachIndexed { index, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        row.label,
                        { v -> viewModel.edit { copy(portions = portions.replaced(index) { it.copy(label = v) }) } },
                        label = { Text("Name (cup, slice...)") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                    )
                    OutlinedTextField(
                        row.grams,
                        { v -> viewModel.edit { copy(portions = portions.replaced(index) { it.copy(grams = v) }) } },
                        label = { Text("Grams") },
                        keyboardOptions = numeric,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.edit { copy(portions = portions.filterIndexed { i, _ -> i != index }) } }) {
                        Icon(Icons.Default.Close, "Remove portion")
                    }
                }
            }
            TextButton(onClick = { viewModel.edit { copy(portions = portions + PortionRow()) } }) { Text("Add portion") }

            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            if (editing) {
                TextButton(onClick = { viewModel.delete(onDeleted) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete food", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun <T> List<T>.replaced(index: Int, change: (T) -> T): List<T> =
    mapIndexed { i, item -> if (i == index) change(item) else item }
