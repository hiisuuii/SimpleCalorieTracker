package live.hisui.calorietracker.ui.sentence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import live.hisui.calorietracker.data.LogRepository
import live.hisui.calorietracker.data.PendingItem
import live.hisui.calorietracker.data.ResolvedItem
import live.hisui.calorietracker.data.SentenceService
import live.hisui.calorietracker.domain.AliasHit
import live.hisui.calorietracker.domain.ClarificationPlanner
import live.hisui.calorietracker.domain.Meal
import live.hisui.calorietracker.domain.Question

enum class Stage { INPUT, ASKING, RESOLVING, CONFIRM }

data class ItemRow(val resolved: ResolvedItem, val gramsText: String) {
    val grams: Double? get() = gramsText.toDoubleOrNull()?.takeIf { it > 0 }
    val kcal: Double? get() = resolved.food?.let { f -> grams?.let { f.kcalPer100g * it / 100.0 } }
}

data class SentenceUiState(
    val text: String = "",
    val stage: Stage = Stage.INPUT,
    val question: Question? = null,
    val items: List<ItemRow> = emptyList(),
    val meal: Meal = Meal.SNACK,
    val message: String? = null,
    val busy: Boolean = false,
) {
    val canLog: Boolean get() = items.any { it.kcal != null } && items.none { it.resolved.food != null && it.grams == null }
}

@HiltViewModel
class SentenceViewModel @Inject constructor(
    private val service: SentenceService,
    private val log: LogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SentenceUiState())
    val state: StateFlow<SentenceUiState> = _state.asStateFlow()

    private var pending = mutableListOf<PendingItem>()
    private val queue = ArrayDeque<Question>()

    fun setText(text: String) = _state.update { it.copy(text = text, message = null) }

    fun submit() {
        val text = _state.value.text
        if (text.isBlank()) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val parsed = service.parse(text)
            _state.update { it.copy(busy = false) }
            if (parsed.isEmpty()) {
                _state.update { it.copy(message = "Couldn't find any foods in that. Try listing them, like \"2 eggs, toast\".") }
                return@launch
            }
            pending = parsed.toMutableList()
            queue.clear()
            parsed.forEachIndexed { i, p ->
                queue += ClarificationPlanner.questionsFor(i, p.item, p.alias?.let { AliasHit(it.defaultGrams != null) })
            }
            advance()
        }
    }

    fun answer(text: String) {
        val question = queue.firstOrNull() ?: return
        val updated = ClarificationPlanner.apply(pending[question.itemIndex].item, question.kind, text)
        if (updated == null) {
            _state.update { it.copy(message = "Enter an amount like 150 g or 1 cup") }
            return
        }
        pending[question.itemIndex] = pending[question.itemIndex].copy(item = updated)
        queue.removeFirst()
        _state.update { it.copy(message = null) }
        advance()
    }

    fun backToInput() = _state.update { it.copy(stage = Stage.INPUT, items = emptyList(), question = null, message = null) }

    fun setGrams(index: Int, text: String) = updateRow(index) { it.copy(gramsText = text) }

    fun selectOption(index: Int, option: Int) {
        val row = _state.value.items.getOrNull(index) ?: return
        viewModelScope.launch {
            val resolved = service.select(row.resolved, option)
            updateRow(index) { ItemRow(resolved, resolved.grams.asText()) }
        }
    }

    fun remove(index: Int) = _state.update { s -> s.copy(items = s.items.filterIndexed { i, _ -> i != index }) }

    fun setMeal(meal: Meal) = _state.update { it.copy(meal = meal) }

    fun logAll(onDone: () -> Unit) {
        val s = _state.value
        val toLog = s.items.mapNotNull { row -> row.grams?.let { row.resolved to it } }
        viewModelScope.launch {
            service.logAll(toLog, s.meal)
            onDone()
        }
    }

    private fun advance() {
        val next = queue.firstOrNull()
        if (next != null) {
            _state.update { it.copy(stage = Stage.ASKING, question = next) }
            return
        }
        _state.update { it.copy(stage = Stage.RESOLVING, question = null) }
        viewModelScope.launch {
            val resolved = pending.map { p -> async { service.resolve(p) } }.awaitAll()
            _state.update {
                it.copy(
                    stage = Stage.CONFIRM,
                    items = resolved.map { r -> ItemRow(r, r.grams.asText()) },
                    meal = log.mealNow(),
                )
            }
        }
    }

    private fun updateRow(index: Int, change: (ItemRow) -> ItemRow) = _state.update { s ->
        s.copy(items = s.items.mapIndexed { i, row -> if (i == index) change(row) else row })
    }

    private fun Double?.asText() = this?.roundToInt()?.toString().orEmpty()
}
