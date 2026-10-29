package live.hisui.calorietracker.domain

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

data class SharedFood(
    val name: String,
    val kcalPer100g: Double,
    val proteinPer100g: Double?,
    val carbsPer100g: Double?,
    val fatPer100g: Double?,
    val grams: Double,
)

data class SharedMeal(
    val name: String,
    val totalGrams: Double?,
    val totalServings: Double?,
    val items: List<SharedFood>,
)

class MealCodeException : Exception("That isn't a valid meal code.")

// Positional JSON, raw-deflated, URL-safe base64 without padding: the code gets pasted into a text message, so
// every character counts. Foods travel with their nutrition (no database ids) so the code works offline.
object MealShare {
    private const val MAX_INFLATED_BYTES = 256 * 1024

    fun encode(meal: SharedMeal): String {
        val json = JsonArray(
            listOf(
                JsonPrimitive(meal.name),
                number(meal.totalGrams ?: meal.totalServings),
                JsonPrimitive(if (meal.totalServings != null) 1 else 0),
                JsonArray(meal.items.map { food ->
                    JsonArray(listOf(JsonPrimitive(food.name), number(food.kcalPer100g), number(food.proteinPer100g), number(food.carbsPer100g), number(food.fatPer100g), number(food.grams)))
                }),
            ),
        )
        return Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(json.toString().toByteArray()))
    }

    fun decode(code: String): SharedMeal = try {
        val root = Json.parseToJsonElement(String(inflate(Base64.getUrlDecoder().decode(code.trim())))).jsonArray
        val total = root[1].jsonPrimitive.doubleOrNull
        val byServings = root[2].jsonPrimitive.content == "1"
        SharedMeal(
            name = root[0].jsonPrimitive.content,
            totalGrams = total.takeIf { !byServings },
            totalServings = total.takeIf { byServings },
            items = root[3].jsonArray.map { element ->
                val f = element.jsonArray
                SharedFood(
                    name = f[0].jsonPrimitive.content,
                    kcalPer100g = f[1].jsonPrimitive.content.toDouble(),
                    proteinPer100g = f[2].jsonPrimitive.doubleOrNull,
                    carbsPer100g = f[3].jsonPrimitive.doubleOrNull,
                    fatPer100g = f[4].jsonPrimitive.doubleOrNull,
                    grams = f[5].jsonPrimitive.content.toDouble(),
                )
            },
        ).also { if (it.items.isEmpty() || it.name.isBlank() || it.items.any { f -> f.grams <= 0 }) throw MealCodeException() }
    } catch (e: Exception) {
        throw e as? MealCodeException ?: MealCodeException()
    }

    // Whole numbers drop the ".0"; two decimals covers what USDA publishes.
    private fun number(value: Double?) =
        if (value == null) JsonNull else JsonPrimitive(Math.round(value * 100) / 100.0).let { if (it.content.endsWith(".0")) JsonPrimitive(it.content.dropLast(2).toLong()) else it }

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(bytes)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        val inflater = Inflater(true)
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) throw MealCodeException()
                out.write(buffer, 0, n)
                if (out.size() > MAX_INFLATED_BYTES) throw MealCodeException()
            }
        } catch (e: DataFormatException) {
            throw MealCodeException()
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }
}
