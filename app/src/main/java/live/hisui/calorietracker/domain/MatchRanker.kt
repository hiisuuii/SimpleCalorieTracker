package live.hisui.calorietracker.domain

data class RankInput(val description: String, val dataType: String?, val bonus: Double = 0.0)

data class Ranked(val index: Int, val score: Double)

data class RankResult(val ranked: List<Ranked>, val confident: Boolean)

object MatchRanker {
    private val cookedWords = setOf("cooked", "boiled", "roasted", "grilled", "baked", "fried", "steamed", "broiled", "simmered", "braised", "stewed")
    private val rawWords = setOf("raw", "uncooked", "dry")
    private val dataTypeBonus = mapOf("Foundation" to 0.15, "SR Legacy" to 0.12, "Survey (FNDDS)" to 0.1)

    private const val EXTRA_WORD_PENALTY = 0.02
    private const val MAX_EXTRA_PENALTY = 0.3
    private const val CONFIDENT_LEAD = 0.12
    private const val MIN_CONFIDENT_OVERLAP = 0.5

    fun rank(item: ParsedItem, inputs: List<RankInput>): RankResult {
        val query = Words.tokens(item.name).toSet()
        val scored = inputs.mapIndexed { index, input ->
            val words = Words.tokens(input.description).toSet()
            val overlap = if (query.isEmpty()) 0.0 else query.count { it in words }.toDouble() / query.size
            val extra = (words.size - query.count { it in words }).coerceAtLeast(0)
            val score = overlap +
                stateScore(item.state, words) +
                (dataTypeBonus[input.dataType] ?: 0.0) +
                input.bonus -
                (extra * EXTRA_WORD_PENALTY).coerceAtMost(MAX_EXTRA_PENALTY)
            Ranked(index, score) to overlap
        }
        val sorted = scored.sortedByDescending { it.first.score }
        val confident = sorted.isNotEmpty() &&
            sorted[0].second >= MIN_CONFIDENT_OVERLAP &&
            (sorted.size == 1 || sorted[0].first.score - sorted[1].first.score >= CONFIDENT_LEAD)
        return RankResult(sorted.map { it.first }, confident)
    }

    private fun stateScore(state: String?, words: Set<String>): Double {
        state ?: return 0.0
        val wantsRaw = state in rawWords
        val hasRaw = words.any { it in rawWords }
        val hasCooked = words.any { it in cookedWords }
        return when {
            wantsRaw && hasRaw -> 0.3
            wantsRaw && hasCooked -> -0.3
            !wantsRaw && state in words -> 0.35
            !wantsRaw && hasCooked -> 0.25
            !wantsRaw && hasRaw -> -0.3
            else -> 0.0
        }
    }
}
