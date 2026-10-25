package live.hisui.calorietracker.data.net

import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.db.Portion
import live.hisui.calorietracker.domain.FoodSource

data class FoodCandidate(val food: Food, val portions: List<Portion>)

private const val ENERGY_KCAL = 1008
private const val ENERGY_ATWATER_GENERAL = 2047
private const val ENERGY_ATWATER_SPECIFIC = 2048
private const val PROTEIN = 1003
private const val FAT = 1004
private const val CARBS = 1005

fun UsdaFood.toCandidate(): FoodCandidate? {
    val byId = foodNutrients.mapNotNull { n -> n.nutrientId?.let { id -> n.value?.let { id to it } } }.toMap()
    val kcal = byId[ENERGY_KCAL] ?: byId[ENERGY_ATWATER_GENERAL] ?: byId[ENERGY_ATWATER_SPECIFIC] ?: return null
    val brand = brandName?.takeIf { it.isNotBlank() } ?: brandOwner?.takeIf { it.isNotBlank() }
    val food = Food(
        name = description.toDisplayName(),
        brand = brand,
        source = FoodSource.USDA,
        externalId = fdcId.toString(),
        dataType = dataType,
        barcode = gtinUpc?.takeIf { it.isNotBlank() },
        kcalPer100g = kcal,
        proteinPer100g = byId[PROTEIN],
        carbsPer100g = byId[CARBS],
        fatPer100g = byId[FAT],
    )
    return FoodCandidate(food, brandedServingPortion())
}

// ml is treated as grams: close enough for the water-like drinks that Branded entries list in ml.
private fun UsdaFood.brandedServingPortion(): List<Portion> {
    val size = servingSize?.takeIf { it > 0 } ?: return emptyList()
    if (servingSizeUnit?.lowercase() !in setOf("g", "grm", "ml", "mlt")) return emptyList()
    val household = householdServingFullText?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    return listOf(Portion(foodId = 0, label = "1 serving$household", grams = size))
}

fun UsdaDetails.toPortions(): List<Portion> = foodPortions.mapNotNull { p ->
    val grams = p.gramWeight?.takeIf { it > 0 } ?: return@mapNotNull null
    val description = p.portionDescription?.takeIf { it.isNotBlank() && !it.startsWith("Quantity not specified") }
    val (amount, text) = if (description != null) {
        splitLeadingAmount(description)
    } else {
        val unit = p.measureUnit?.name?.takeIf { it.isNotBlank() && it != "undetermined" }
        (p.amount?.takeIf { it > 0 } ?: 1.0) to listOfNotNull(unit, p.modifier?.takeIf { it.isNotBlank() }).joinToString(" ")
    }
    text.takeIf { it.isNotBlank() }?.let { Portion(foodId = 0, label = "1 $it", grams = grams / amount) }
}.distinctBy { it.label }

private val leadingAmount = Regex("""^\s*(\d+(?:\.\d+)?)(?:\s+(\d+)/(\d+)|/(\d+))?\s+(.+)$""")

private fun splitLeadingAmount(description: String): Pair<Double, String> {
    val m = leadingAmount.find(description) ?: return 1.0 to description.trim()
    val (whole, num, den, simpleDen, rest) = m.destructured
    val amount = when {
        num.isNotEmpty() -> whole.toDouble() + num.toDouble() / den.toDouble()
        simpleDen.isNotEmpty() -> whole.toDouble() / simpleDen.toDouble()
        else -> whole.toDouble()
    }
    return (amount.takeIf { it > 0 } ?: 1.0) to rest.trim()
}

private fun String.toDisplayName(): String =
    if (any { it.isLowerCase() }) this else lowercase().replaceFirstChar { it.uppercase() }
