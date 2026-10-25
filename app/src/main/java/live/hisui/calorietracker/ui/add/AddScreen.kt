package live.hisui.calorietracker.ui.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.db.LogEntry
import live.hisui.calorietracker.ui.common.gramsText
import live.hisui.calorietracker.ui.common.kcalText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddScreen(
    onBack: () -> Unit,
    onFood: (Long) -> Unit,
    onQuickAdd: () -> Unit,
    onCustomFood: () -> Unit,
    onBuildMeal: () -> Unit,
    onScanBarcode: () -> Unit,
    onScanLabel: () -> Unit,
    onLogged: () -> Unit,
    picking: Boolean,
    viewModel: AddViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searching = state.query.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (picking) "Add food to meal" else "Log food") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(padding),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQuery,
                    label = { Text("Search foods") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!picking) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onBuildMeal, modifier = Modifier.weight(1f)) { Text("Build a meal") }
                        Button(onClick = onScanBarcode, modifier = Modifier.weight(1f)) { Text("Scan barcode") }
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onQuickAdd) { Text("Quick add") }
                        OutlinedButton(onClick = onScanLabel) { Text("Scan label") }
                        OutlinedButton(onClick = onCustomFood) { Text("Custom food") }
                    }
                }
            }
            if (searching) {
                searchResults(state, viewModel, onFood)
            } else {
                browse(state, viewModel, onFood, onLogged, picking)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.searchResults(
    state: AddUiState,
    viewModel: AddViewModel,
    onFood: (Long) -> Unit,
) {
    items(state.local, key = { "local-${it.id}" }) { food -> FoodRow(food, onClick = { onFood(food.id) }) }
    item {
        when {
            state.usdaLoading -> CircularProgressIndicator()
            state.usda == null -> TextButton(onClick = viewModel::searchUsda) { Text("Search USDA") }
        }
    }
    state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
    state.usda?.let { found ->
        items(found, key = { "usda-${it.food.externalId}" }) { candidate ->
            FoodRow(candidate.food, onClick = { viewModel.open(candidate, onFood) })
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.browse(
    state: AddUiState,
    viewModel: AddViewModel,
    onFood: (Long) -> Unit,
    onLogged: () -> Unit,
    picking: Boolean,
) {
    if (!picking && state.savedMeals.isNotEmpty()) {
        item { Text("Saved meals", style = MaterialTheme.typography.titleMedium) }
        items(state.savedMeals, key = { "meal-${it.id}" }) { meal ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.logSavedMeal(meal, onLogged) },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(meal.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 12.dp))
                IconButton(onClick = { viewModel.deleteSavedMeal(meal) }) { Icon(Icons.Default.Delete, "Delete saved meal") }
            }
        }
    }
    if (state.recents.isNotEmpty()) {
        item { Text("Recent", style = MaterialTheme.typography.titleMedium) }
        items(state.recents, key = { "recent-${it.id}" }) { entry ->
            RecentRow(
                entry,
                onOpen = { entry.foodId?.let(onFood) },
                onRelog = if (picking) null else ({ viewModel.relog(entry, onLogged) }),
            )
        }
    }
    if (state.favorites.isNotEmpty()) {
        item { Text("Favorites", style = MaterialTheme.typography.titleMedium) }
        items(state.favorites, key = { "fav-${it.id}" }) { food -> FoodRow(food, onClick = { onFood(food.id) }) }
    }
}

@Composable
private fun FoodRow(food: Food, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(food.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            listOfNotNull(food.brand, "${food.kcalPer100g.toInt()} kcal / 100 g").joinToString(" - "),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RecentRow(entry: LogEntry, onOpen: () -> Unit, onRelog: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier
            .weight(1f)
            .padding(vertical = 8.dp)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(entry.grams?.gramsText(false), entry.kcal.kcalText()).joinToString(" - "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (onRelog != null) IconButton(onClick = onRelog) { Icon(Icons.Default.Add, "Log again") }
    }
}
