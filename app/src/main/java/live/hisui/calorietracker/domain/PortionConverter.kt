package live.hisui.calorietracker.domain

data class PortionOption(val label: String, val grams: Double)

object PortionConverter {
    // Volume units assume water density, so they are only a fallback for foods with no matching portion.
    private val genericGrams = mapOf(
        "g" to 1.0,
        "gram" to 1.0,
        "kg" to 1000.0,
        "ml" to 1.0,
        "oz" to GRAMS_PER_OUNCE,
        "ounce" to GRAMS_PER_OUNCE,
        "lb" to 453.592,
        "pound" to 453.592,
        "tsp" to 4.92892,
        "teaspoon" to 4.92892,
        "tbsp" to 14.7868,
        "tablespoon" to 14.7868,
        "cup" to 236.588,
    )

    fun toGrams(quantity: Double, unit: String, portions: List<PortionOption>): Double? {
        val wanted = Words.singular(unit.lowercase().trim().trimEnd('.'))
        if (wanted.isEmpty()) return null
        val matched = portions.firstOrNull { words(it.label).firstOrNull() == wanted }
            ?: portions.firstOrNull { wanted in words(it.label) }
        val gramsPerUnit = matched?.grams ?: genericGrams[wanted] ?: return null
        return quantity * gramsPerUnit
    }

    // Portions are stored per one unit ("1 medium"), so the first of these wins when a count has no unit.
    private val preferredCountPortions = listOf("medium", "large", "serving")

    fun defaultCountPortion(portions: List<PortionOption>): PortionOption? =
        preferredCountPortions.firstNotNullOfOrNull { wanted -> portions.firstOrNull { wanted in words(it.label) } }
            ?: portions.firstOrNull()

    private fun words(label: String): List<String> = Words.tokens(label)
}
