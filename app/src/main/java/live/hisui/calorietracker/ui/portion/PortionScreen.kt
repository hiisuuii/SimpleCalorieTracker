package live.hisui.calorietracker.ui.portion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import live.hisui.calorietracker.domain.FoodSource
import live.hisui.calorietracker.ui.common.MealChips
import live.hisui.calorietracker.ui.common.friendly
import live.hisui.calorietracker.ui.common.kcalText
import live.hisui.calorietracker.ui.common.macroText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PortionScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onLogged: () -> Unit,
    viewModel: PortionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val food = state.food

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Food details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (food != null) {
                        if (food.source == FoodSource.CUSTOM) {
                            IconButton(onClick = { onEdit(food.id) }) { Icon(Icons.Default.Edit, "Edit food") }
                        }
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                if (food.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                if (food.isFavorite) "Remove favorite" else "Add favorite",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (food == null) return@Scaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(food.name, style = MaterialTheme.typography.headlineSmall)
            food.brand?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                state.nutrition?.kcal?.kcalText() ?: "-",
                style = MaterialTheme.typography.displaySmall,
            )
            Text("${food.kcalPer100g.toInt()} kcal per 100 g", style = MaterialTheme.typography.bodySmall)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.selected == null,
                    onClick = { viewModel.selectPortion(null) },
                    label = { Text(if (state.useOunces) "oz" else "g") },
                )
                state.portions.forEach { portion ->
                    FilterChip(
                        selected = state.selected?.id == portion.id,
                        onClick = { viewModel.selectPortion(portion) },
                        label = { Text(portion.label.removePrefix("1 ")) },
                    )
                }
            }
            OutlinedTextField(
                value = state.amount,
                onValueChange = viewModel::setAmount,
                label = { Text(if (state.selected == null) "Amount" else "Number of ${state.selected?.label?.removePrefix("1 ")}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!viewModel.picking) MealChips(state.meal, viewModel::setMeal)

            state.nutrition?.let { n ->
                TextButton(onClick = viewModel::toggleMacros) { Text(if (state.showMacros) "Hide macros" else "Show macros") }
                if (state.showMacros) {
                    Text(
                        "Protein ${n.proteinG?.macroText() ?: "-"} g, carbs ${n.carbsG?.macroText() ?: "-"} g, " +
                            "fat ${n.fatG?.macroText() ?: "-"} g",
                    )
                }
            }
            Button(
                onClick = { viewModel.log(onLogged) },
                enabled = state.grams != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (viewModel.picking) "Add to meal" else viewModel.logDate?.let { "Log to ${it.friendly()}" } ?: "Log") }
        }
    }
}
