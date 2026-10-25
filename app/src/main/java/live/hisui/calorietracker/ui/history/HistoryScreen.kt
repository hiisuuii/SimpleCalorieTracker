package live.hisui.calorietracker.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import live.hisui.calorietracker.data.LogRepository
import live.hisui.calorietracker.data.SettingsRepository
import live.hisui.calorietracker.data.db.DayTotal
import live.hisui.calorietracker.ui.common.friendly
import live.hisui.calorietracker.ui.common.kcalText

data class HistoryUiState(val days: List<DayTotal> = emptyList(), val target: Int = 2000)

@HiltViewModel
class HistoryViewModel @Inject constructor(log: LogRepository, settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<HistoryUiState> = combine(log.dailyTotals, settings.settings) { days, s ->
        HistoryUiState(days, s.dailyTarget)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onDay: (LocalDate) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("History") }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(padding),
        ) {
            if (state.days.isEmpty()) item { Text("No days logged yet.") }
            items(state.days, key = { it.logDate }) { day ->
                val date = LocalDate.parse(day.logDate)
                val over = day.kcal > state.target
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDay(date) }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(date.friendly(), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        day.kcal.kcalText(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
