package live.hisui.calorietracker.domain

data class PortionOption(val label: String, val grams: Double)

object PortionConverter {
    // Volume units assume water density, so they are only a fallback for foods with no matching portion.
    private val genericGrams = mapOf(
        "g" to 1.0,
        "gram" to 1.0,
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
        val wanted = singular(unit.lowercase().trim().trimEnd('.'))
        if (wanted.isEmpty()) return null
        val matched = portions.firstOrNull { words(it.label).firstOrNull() == wanted }
            ?: portions.firstOrNull { wanted in words(it.label) }
        val gramsPerUnit = matched?.grams ?: genericGrams[wanted] ?: return null
        return quantity * gramsPerUnit
    }

    private fun words(label: String): List<String> =
        Regex("[a-z]+").findAll(label.lowercase()).map { singular(it.value) }.toList()

    private fun singular(word: String): String = when {
        word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
        listOf("ches", "shes", "xes", "sses").any { word.endsWith(it) } -> word.dropLast(2)
        word.endsWith("s") && !word.endsWith("ss") && word.length > 2 -> word.dropLast(1)
        else -> word
    }
}
