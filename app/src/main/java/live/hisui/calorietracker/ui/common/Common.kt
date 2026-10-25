package live.hisui.calorietracker.ui.common

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import live.hisui.calorietracker.domain.GRAMS_PER_OUNCE
import live.hisui.calorietracker.domain.Meal

fun Double.kcalText() = "${roundToInt()} kcal"

fun Double.gramsText(useOunces: Boolean): String =
    if (useOunces) "%.1f oz".format(this / GRAMS_PER_OUNCE) else "${roundToInt()} g"

fun Double.macroText(): String = "%.1f".format(this)

fun LocalDate.friendly(): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

val Meal.label: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealChips(selected: Meal, onSelect: (Meal) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Meal.entries.forEach { meal ->
            FilterChip(
                selected = meal == selected,
                onClick = { onSelect(meal) },
                label = { Text(meal.label) },
            )
        }
    }
}
