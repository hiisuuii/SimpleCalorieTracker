package live.hisui.calorietracker.domain

const val GRAMS_PER_OUNCE = 28.3495

data class Nutrition(
    val kcal: Double,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
) {
    fun per100g(totalWeightG: Double): Nutrition = scaledTo(10_000.0 / totalWeightG)

    fun scaledTo(grams: Double): Nutrition {
        val factor = grams / 100.0
        return Nutrition(
            kcal = kcal * factor,
            proteinG = proteinG?.times(factor),
            carbsG = carbsG?.times(factor),
            fatG = fatG?.times(factor),
        )
    }
}
