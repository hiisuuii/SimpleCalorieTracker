package live.hisui.calorietracker.domain

import kotlin.math.abs
import kotlin.math.max

object NutritionSanity {
    private const val MAX_KCAL_PER_100G = 900.0
    private const val RELATIVE_TOLERANCE = 0.2
    private const val ABSOLUTE_TOLERANCE_KCAL = 20.0

    // Fiber and alcohol make exact Atwater agreement impossible, hence the generous tolerance.
    fun kcalMatchesMacros(kcal: Double, proteinG: Double?, carbsG: Double?, fatG: Double?): Boolean {
        if (proteinG == null || carbsG == null || fatG == null) return true
        val expected = 4 * proteinG + 4 * carbsG + 9 * fatG
        return abs(kcal - expected) <= max(RELATIVE_TOLERANCE * max(kcal, expected), ABSOLUTE_TOLERANCE_KCAL)
    }

    fun plausiblePer100g(n: Nutrition): Boolean {
        if (n.kcal < 0 || n.kcal > MAX_KCAL_PER_100G) return false
        val macros = listOfNotNull(n.proteinG, n.carbsG, n.fatG)
        if (macros.any { it < 0 || it > 100 }) return false
        return kcalMatchesMacros(n.kcal, n.proteinG, n.carbsG, n.fatG)
    }
}
