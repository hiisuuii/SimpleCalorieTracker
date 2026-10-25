package live.hisui.calorietracker.domain

object Words {
    fun singular(word: String): String = when {
        word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
        listOf("ches", "shes", "xes", "sses").any { word.endsWith(it) } -> word.dropLast(2)
        word.endsWith("s") && !word.endsWith("ss") && word.length > 2 -> word.dropLast(1)
        else -> word
    }

    fun tokens(text: String): List<String> =
        Regex("[a-z]+").findAll(text.lowercase()).map { singular(it.value) }.toList()
}
