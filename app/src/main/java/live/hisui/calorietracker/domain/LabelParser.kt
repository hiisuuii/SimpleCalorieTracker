package live.hisui.calorietracker.domain

data class ParsedLabel(
    val servingText: String? = null,
    val servingGrams: Double? = null,
    val kcal: Double? = null,
    val fatG: Double? = null,
    val carbsG: Double? = null,
    val proteinG: Double? = null,
) {
    val isEmpty: Boolean get() = kcal == null && fatG == null && carbsG == null && proteinG == null && servingGrams == null
}

// Reads US-style Nutrition Facts panels. Always the first column wins, which is "per serving" on those panels.
object LabelParser {
    private const val KJ_PER_KCAL = 4.184
    private const val MAX_SERVING_KCAL = 2500.0
    private val bareNumber = Regex("""\d{1,4}""")

    private val lookalikeDigits = mapOf('O' to '0', 'o' to '0', 'I' to '1', 'l' to '1', 'S' to '5')
    private val numberToken = Regex("""(?<![A-Za-z])[0-9OoIlS]*[0-9][0-9OoIlS]*""")
    private val calorieWord = Regex("""(?i)\bcal[a-z]{2,6}\b""")
    private val servingHeader = Regex("""(?i)\bserving\s*s[a-z]{2,3}\b[:\s]*(.*)$""")
    private val gramsInParens = Regex("""\((\d+(?:\.\d+)?)\s*(?:g|ml)\)""", RegexOption.IGNORE_CASE)
    private val gramsAnywhere = Regex("""(\d+(?:\.\d+)?)\s*(?:g|ml)\b""", RegexOption.IGNORE_CASE)
    private val kcalExplicit = Regex("""(\d+(?:\.\d+)?)\s*kcal""", RegexOption.IGNORE_CASE)
    private val kilojoules = Regex("""(\d+(?:\.\d+)?)\s*kj""", RegexOption.IGNORE_CASE)
    private val amount = Regex("""(\d+(?:\.\d+)?)\s*(mg|mcg|g|%)?""", RegexOption.IGNORE_CASE)
    private val leadingGrams = Regex("""^\s*(\d+(?:\.\d+)?)\s*g\b""", RegexOption.IGNORE_CASE)

    private val fatLabel = Regex("""(?i)^\W*(total\s+)?fat\b""")
    private val carbLabel = Regex("""(?i)^\W*(total\s+)?carb\w*""")
    private val proteinLabel = Regex("""(?i)^\W*protein\w*""")

    fun parse(ocrText: String): ParsedLabel {
        val lines = ocrText.lines().map(::clean).filter { it.isNotBlank() }
        val (servingText, servingGrams) = servingSize(lines)
        return ParsedLabel(
            servingText = servingText,
            servingGrams = servingGrams,
            kcal = calories(lines, ocrText),
            fatG = macro(lines, fatLabel),
            carbsG = macro(lines, carbLabel),
            proteinG = macro(lines, proteinLabel),
        )
    }

    private fun clean(line: String): String = line
        .replace('’', '\'')
        .replace(Regex("""(?<=\d),(?=\d)"""), ".")
        .replace(Regex("""\b[Oo]\s?g\b"""), "0g")
        .replace(numberToken) { m -> m.value.map { lookalikeDigits[it] ?: it }.joinToString("") }
        .trim()

    private fun servingSize(lines: List<String>): Pair<String?, Double?> {
        val index = lines.indexOfFirst { servingHeader.containsMatchIn(it) && !it.contains("per container", ignoreCase = true) }
        if (index >= 0) {
            var rest = servingHeader.find(lines[index])!!.groupValues[1].trim()
            if (rest.isEmpty()) rest = lines.getOrNull(index + 1).orEmpty().takeIf { line -> line.any(Char::isDigit) }.orEmpty()
            if (rest.isNotEmpty()) {
                val grams = (gramsInParens.find(rest) ?: gramsAnywhere.find(rest))?.groupValues?.get(1)?.toDoubleOrNull()
                return rest to grams
            }
        }
        // ML Kit often detaches the value from its "Serving size" header, so look for a lone "(55g)" line.
        val detached = lines.firstOrNull { gramsInParens.containsMatchIn(it) } ?: return null to null
        return detached to gramsInParens.find(detached)!!.groupValues[1].toDoubleOrNull()
    }

    private fun calories(lines: List<String>, fullText: String): Double? {
        lines.forEachIndexed { i, line ->
            val word = calorieWord.find(line) ?: return@forEachIndexed
            val after = line.substring(word.range.last + 1).trim()
            if (after.startsWith("from", ignoreCase = true)) return@forEachIndexed
            val labelOnly = after.isEmpty() && line.trim().length == word.value.length
            val value = amount.find(after)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: lines.getOrNull(i + 1)?.takeIf { labelOnly }
                    ?.let { next -> amount.find(next)?.takeIf { it.range.first == 0 }?.groupValues?.get(1)?.toDoubleOrNull() }
                ?: lines.drop(i + 1).firstOrNull { labelOnly && bareNumber.matches(it) }?.toDoubleOrNull()?.takeIf { it <= MAX_SERVING_KCAL }
            if (value != null) return value
        }
        kcalExplicit.find(fullText)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }
        return kilojoules.find(fullText)?.groupValues?.get(1)?.toDoubleOrNull()?.div(KJ_PER_KCAL)
    }

    private fun macro(lines: List<String>, label: Regex): Double? {
        lines.forEachIndexed { i, line ->
            val m = label.find(line) ?: return@forEachIndexed
            if (line.contains("from", ignoreCase = true) && line.contains("calor", ignoreCase = true)) return@forEachIndexed
            gramValue(line.substring(m.range.last + 1))?.let { return it }
            leadingGrams.find(lines.getOrNull(i + 1).orEmpty())?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }
        }
        return null
    }

    // A bare number is accepted only when no explicit "g" value exists, since the other numbers are % daily values.
    private fun gramValue(text: String): Double? {
        val tokens = amount.findAll(text).toList()
        val grams = tokens.firstOrNull { it.groupValues[2].equals("g", ignoreCase = true) }
        val chosen = grams ?: tokens.firstOrNull { it.groupValues[2].isEmpty() }
        return chosen?.groupValues?.get(1)?.toDoubleOrNull()
    }
}
