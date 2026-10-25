package live.hisui.calorietracker.domain

object FtsQuery {
    fun build(raw: String): String? {
        val tokens = raw.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return tokens.takeIf { it.isNotEmpty() }?.joinToString(" ") { "$it*" }
    }
}
