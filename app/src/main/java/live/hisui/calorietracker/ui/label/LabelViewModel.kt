package live.hisui.calorietracker.ui.label

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import live.hisui.calorietracker.data.FoodRepository
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.db.Portion
import live.hisui.calorietracker.domain.FoodSource
import live.hisui.calorietracker.domain.LabelParser
import live.hisui.calorietracker.domain.Nutrition
import live.hisui.calorietracker.domain.NutritionSanity
import live.hisui.calorietracker.platform.LabelOcr

enum class LabelStage { CAPTURE, PROCESSING, REVIEW }

data class LabelUiState(
    val stage: LabelStage = LabelStage.CAPTURE,
    val photo: Uri? = null,
    val message: String? = null,
    val name: String = "",
    val servingText: String = "",
    val servingGrams: String = "",
    val kcal: String = "",
    val fat: String = "",
    val carbs: String = "",
    val protein: String = "",
) {
    private val kcalValue get() = kcal.toDoubleOrNull()?.takeIf { it >= 0 }
    private val gramsValue get() = servingGrams.toDoubleOrNull()?.takeIf { it > 0 }
    private val perServing
        get() = kcalValue?.let { Nutrition(it, protein.toDoubleOrNull(), carbs.toDoubleOrNull(), fat.toDoubleOrNull()) }

    val canSave: Boolean get() = name.isNotBlank() && perServing != null && gramsValue != null

    val warning: String?
        get() {
            val n = perServing ?: return null
            val (p, c, f) = Triple(n.proteinG, n.carbsG, n.fatG)
            if (p == null || c == null || f == null || NutritionSanity.kcalMatchesMacros(n.kcal, p, c, f)) return null
            val expected = (4 * p + 4 * c + 9 * f).roundToInt()
            return "Calories (${n.kcal.roundToInt()}) don't match the macros (about $expected). A digit may be misread, so compare with the photo."
        }

    fun toFood(barcode: String?): Pair<Food, List<Portion>>? {
        val per100 = perServing?.let { n -> gramsValue?.let { n.per100g(it) } } ?: return null
        val grams = gramsValue ?: return null
        val food = Food(
            name = name.trim(),
            source = FoodSource.CUSTOM,
            barcode = barcode,
            kcalPer100g = per100.kcal,
            proteinPer100g = per100.proteinG,
            carbsPer100g = per100.carbsG,
            fatPer100g = per100.fatG,
        )
        val detail = servingText.trim().takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
        return food to listOf(Portion(foodId = 0, label = "1 serving$detail", grams = grams))
    }
}

@HiltViewModel
class LabelViewModel @Inject constructor(
    handle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val foods: FoodRepository,
) : ViewModel() {
    private val barcode: String? = handle["barcode"]
    private val _state = MutableStateFlow(LabelUiState())
    val state: StateFlow<LabelUiState> = _state.asStateFlow()

    fun onPhoto(photo: Uri) {
        _state.update { it.copy(stage = LabelStage.PROCESSING, photo = photo, message = null) }
        viewModelScope.launch {
            val parsed = try {
                LabelParser.parse(LabelOcr.recognize(context, photo))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.update { s ->
                s.copy(
                    stage = LabelStage.REVIEW,
                    message = when {
                        parsed == null -> "Couldn't read that photo. Fill in the numbers yourself or retake it."
                        parsed.isEmpty -> "Couldn't find a nutrition panel. Fill in the numbers from the photo or retake it."
                        else -> null
                    },
                    servingText = parsed?.servingText.orEmpty(),
                    servingGrams = parsed?.servingGrams?.clean().orEmpty(),
                    kcal = parsed?.kcal?.clean().orEmpty(),
                    fat = parsed?.fatG?.clean().orEmpty(),
                    carbs = parsed?.carbsG?.clean().orEmpty(),
                    protein = parsed?.proteinG?.clean().orEmpty(),
                )
            }
        }
    }

    fun onCaptureFailed() = _state.update { it.copy(message = "The camera couldn't take the photo. Try again or choose a photo.") }

    fun edit(change: LabelUiState.() -> LabelUiState) = _state.update { it.change() }

    fun retake() {
        discardPhoto()
        _state.value = LabelUiState()
    }

    fun save(onSaved: (Long) -> Unit) {
        val (food, portions) = _state.value.toFood(barcode) ?: return
        viewModelScope.launch {
            val id = foods.saveCustom(food, portions)
            discardPhoto()
            onSaved(id)
        }
    }

    override fun onCleared() = discardPhoto()

    // Only photos taken here live in our cache; picked photos belong to the user's gallery.
    private fun discardPhoto() {
        _state.value.photo?.takeIf { it.scheme == "file" }?.path?.let { File(it).delete() }
    }

    private fun Double.clean() = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(this)
}
