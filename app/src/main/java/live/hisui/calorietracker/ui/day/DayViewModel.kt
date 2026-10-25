package live.hisui.calorietracker.ui.day

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import live.hisui.calorietracker.data.LogRepository
import live.hisui.calorietracker.data.SavedMealRepository
import live.hisui.calorietracker.data.SettingsRepository
import live.hisui.calorietracker.data.db.LogEntry

data class DayUiState(
    val date: LocalDate? = null,
    val isToday: Boolean = true,
    val entries: List<LogEntry> = emptyList(),
    val total: Double = 0.0,
    val target: Int = 2000,
    val useOunces: Boolean = false,
)

@HiltViewModel
class DayViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val log: LogRepository,
    private val savedMeals: SavedMealRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val fixedDate = handle.get<String>("date")?.let(LocalDate::parse)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DayUiState> = (fixedDate?.let { flowOf(it) } ?: log.currentLogDate)
        .flatMapLatest { date ->
            combine(log.entriesForDate(date), log.totalForDate(date), settings.settings) { entries, total, s ->
                DayUiState(date, fixedDate == null, entries, total, s.dailyTarget, s.useOunces)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState())

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    fun save(entry: LogEntry) = viewModelScope.launch { log.update(entry) }

    fun delete(entry: LogEntry) = viewModelScope.launch { log.delete(entry) }

    fun saveAsMeal(name: String, entries: List<LogEntry>) = viewModelScope.launch {
        val saved = savedMeals.saveFromEntries(name.trim().ifBlank { "Saved meal" }, entries)
        messageChannel.send(if (saved) "Saved meal" else "Quick-add entries can't be saved as a meal")
    }
}
