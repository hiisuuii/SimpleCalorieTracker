package live.hisui.calorietracker.domain

data class ParsedItem(
    val name: String,
    val state: String?,
    val quantity: Double?,
    val unit: String?,
) {
    val phrase: String get() = listOfNotNull(state, name).joinToString(" ")
}

interface MealParser {
    suspend fun parse(text: String): List<ParsedItem>
}

class RuleBasedParser : MealParser {
    override suspend fun parse(text: String): List<ParsedItem> = parseSync(text)

    fun parseSync(text: String): List<ParsedItem> {
        val body = LEAD_IN.replace(text.lowercase().replace(Regex("[\\r\\n;]+"), ","), "").trim()
        return body.split(SEPARATORS).mapNotNull { parseSegment(it.trim()) }
    }

    private fun parseSegment(segment: String): ParsedItem? {
        val tokens = tokenize(segment).toMutableList()
        var (quantity, unit) = takeAmount(tokens)

        var state: String? = null
        val rest = tokens.filter { token ->
            val normalized = STATES[token]
            if (normalized != null && state == null) {
                state = normalized
                false
            } else {
                normalized == null
            }
        }
        val name = rest.dropWhile { it in FILLER }.joinToString(" ")
        if (name.isBlank()) return null
        if (quantity == null && unit != null) quantity = 1.0
        return ParsedItem(name, state, quantity, unit)
    }

    companion object {
        private val LEAD_IN = Regex(
            """^\s*(?:for\s+(?:breakfast|lunch|dinner|a snack|supper)\s*[,:]?\s*)?(?:(?:i\s+(?:just\s+)?(?:had|ate|have eaten|am having)|had|ate)\b)?\s*""",
        )
        private val SEPARATORS = Regex("""\s*,\s*|\s+(?:and|with|plus)\s+|\s*[&+]\s*""")
        private val FILLER = setOf("of", "some", "the")
        private val UNITS = setOf(
            "g", "gram", "kg", "oz", "ounce", "lb", "pound", "cup", "tbsp", "tablespoon", "tsp",
            "teaspoon", "ml", "slice", "piece", "serving", "scoop",
        )
        private val STATES = mapOf(
            "raw" to "raw", "uncooked" to "raw", "dry" to "raw",
            "cooked" to "cooked", "grilled" to "grilled", "roasted" to "roasted", "baked" to "baked",
            "fried" to "fried", "boiled" to "boiled", "steamed" to "steamed", "pan-fried" to "fried",
            "deep-fried" to "fried",
        )
        private val NUMBER_WORDS = mapOf(
            "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0,
            "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "eleven" to 11.0,
            "twelve" to 12.0, "half" to 0.5, "quarter" to 0.25, "a" to 1.0, "an" to 1.0,
        )
        private val FRACTION = Regex("""(\d+)/(\d+)""")

        fun parseAmount(text: String): Pair<Double, String?>? {
            val tokens = tokenize(text.lowercase().trim()).toMutableList()
            val (quantity, unit) = takeAmount(tokens)
            if (quantity == null) return null
            return quantity to (unit ?: tokens.singleOrNull()?.takeIf { it.all(Char::isLetter) })
        }

        private fun tokenize(segment: String): List<String> =
            segment.replace(Regex("""(\d)([a-z])"""), "$1 $2").split(Regex("\\s+")).filter { it.isNotEmpty() }

        private fun takeAmount(tokens: MutableList<String>): Pair<Double?, String?> {
            val quantity = takeQuantity(tokens)
            if (quantity != null) {
                if (tokens.firstOrNull() in setOf("a", "an", "of")) tokens.removeAt(0)
            }
            val unit = takeUnit(tokens)
            return quantity to unit
        }

        private fun takeQuantity(tokens: MutableList<String>): Double? {
            val first = tokens.firstOrNull() ?: return null
            if (first in setOf("a", "an") && tokens.getOrNull(1) == "couple") {
                tokens.removeAt(0)
                tokens.removeAt(0)
                if (tokens.firstOrNull() == "of") tokens.removeAt(0)
                return 2.0
            }
            val value = fraction(first) ?: first.toDoubleOrNull() ?: NUMBER_WORDS[first] ?: return null
            tokens.removeAt(0)
            val next = tokens.firstOrNull()?.let(::fraction)
            if (next != null && first.toDoubleOrNull() != null && !first.contains('/')) {
                tokens.removeAt(0)
                return value + next
            }
            return value
        }

        private fun fraction(token: String): Double? =
            FRACTION.matchEntire(token)?.destructured?.let { (n, d) -> d.toDouble().takeIf { it != 0.0 }?.let { n.toDouble() / it } }

        private fun takeUnit(tokens: MutableList<String>): String? {
            val token = tokens.firstOrNull() ?: return null
            val followedByOf = tokens.getOrNull(1) == "of"
            val isUnit = Words.singular(token) in UNITS || (followedByOf && token.all(Char::isLetter))
            if (!isUnit) return null
            tokens.removeAt(0)
            if (tokens.firstOrNull() == "of") tokens.removeAt(0)
            return Words.singular(token)
        }
    }
}
