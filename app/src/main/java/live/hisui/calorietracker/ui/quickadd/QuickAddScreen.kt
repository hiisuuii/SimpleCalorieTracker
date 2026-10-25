package live.hisui.calorietracker.ui.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import live.hisui.calorietracker.data.LogRepository
import live.hisui.calorietracker.domain.Meal
import live.hisui.calorietracker.ui.common.MealChips

@HiltViewModel
class QuickAddViewModel @Inject constructor(private val log: LogRepository) : ViewModel() {
    suspend fun defaultMeal() = log.mealNow()

    fun add(label: String, kcal: Double, meal: Meal, onDone: () -> Unit) {
        viewModelScope.launch {
            log.quickAdd(label.trim().ifBlank { "Quick add" }, kcal, meal)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(onBack: () -> Unit, onLogged: () -> Unit, viewModel: QuickAddViewModel = hiltViewModel()) {
    var label by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf(Meal.SNACK) }
    LaunchedEffect(Unit) { meal = viewModel.defaultMeal() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick add") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                kcal,
                { kcal = it },
                label = { Text("Calories") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                label,
                { label = it },
                label = { Text("Label (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            MealChips(meal, { meal = it })
            Button(
                onClick = { viewModel.add(label, kcal.toDouble(), meal, onLogged) },
                enabled = kcal.toDoubleOrNull() != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Log") }
        }
    }
}
