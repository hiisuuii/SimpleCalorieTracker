package live.hisui.calorietracker.data.net

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.db.Portion
import live.hisui.calorietracker.domain.FoodSource
import live.hisui.calorietracker.domain.Nutrition
import live.hisui.calorietracker.domain.NutritionSanity

private const val KJ_PER_KCAL = 4.184

// Returns null for products whose calories are missing or fail the macro sanity check, so the caller can fall back to USDA.
fun OffProduct.toCandidate(barcode: String): FoodCandidate? {
    val servingGrams = (servingQuantity.asDouble() ?: servingSize?.let(::gramsInText))?.takeIf { it > 0 }
    val kcal = nutriments.number("energy-kcal_100g")
        ?: nutriments.number("energy_100g")?.div(KJ_PER_KCAL)
        ?: servingGrams?.let { g -> nutriments.number("energy-kcal_serving")?.let { it * 100 / g } }
        ?: return null
    val nutrition = Nutrition(
        kcal = kcal,
        proteinG = nutriments.number("proteins_100g"),
        carbsG = nutriments.number("carbohydrates_100g"),
        fatG = nutriments.number("fat_100g"),
    )
    if (!NutritionSanity.plausiblePer100g(nutrition)) return null

    val brand = brands?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
    val food = Food(
        name = productName?.trim()?.takeIf { it.isNotEmpty() } ?: brand ?: "Product $barcode",
        brand = brand.takeIf { productName?.isNotBlank() == true },
        source = FoodSource.OFF,
        externalId = barcode,
        barcode = barcode,
        kcalPer100g = nutrition.kcal,
        proteinPer100g = nutrition.proteinG,
        carbsPer100g = nutrition.carbsG,
        fatPer100g = nutrition.fatG,
    )
    val portions = servingGrams?.let {
        val detail = servingSize?.trim()?.takeIf { s -> s.isNotEmpty() }?.let { s -> " ($s)" }.orEmpty()
        listOf(Portion(foodId = 0, label = "1 serving$detail", grams = it))
    }.orEmpty()
    return FoodCandidate(food, portions)
}

private val gramsPattern = Regex("""(\d+(?:[.,]\d+)?)\s*(?:g|ml)\b""", RegexOption.IGNORE_CASE)

private fun gramsInText(text: String): Double? =
    gramsPattern.find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

private fun Map<String, JsonElement>.number(key: String): Double? = this[key].asDouble()

private fun JsonElement?.asDouble(): Double? = (this as? JsonPrimitive)?.content?.toDoubleOrNull()
