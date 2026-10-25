package live.hisui.calorietracker.domain

enum class QuestionKind { AMOUNT, STATE }

data class Question(val itemIndex: Int, val kind: QuestionKind, val prompt: String, val options: List<String>)

object ClarificationPlanner {
    private val needsState = setOf(
        "rice", "pasta", "spaghetti", "macaroni", "noodle", "quinoa", "couscous", "barley", "oatmeal", "oat",
        "farro", "bulgur", "lentil", "bean", "chickpea",
        "chicken", "beef", "pork", "turkey", "lamb", "steak", "bacon", "salmon", "fish", "shrimp", "cod",
        "tilapia", "veal", "duck",
    )
    private val prepared = setOf(
        "cake", "cracker", "milk", "bread", "cereal", "flour", "sauce", "soup", "salad", "sandwich",
        "burrito", "nugget", "canned", "deli", "smoked", "cured", "green",
    )

    fun questionsFor(index: Int, item: ParsedItem, alias: AliasHit?): List<Question> {
        val needsAmount = item.quantity == null && alias?.hasGrams != true
        val needsStateAnswer = alias == null && item.state == null && requiresState(item.name)
        return buildList {
            if (needsAmount) add(amountQuestion(index, item))
            if (needsStateAnswer) add(Question(index, QuestionKind.STATE, "Cooked or raw?", listOf("Cooked", "Raw")))
        }
    }

    fun apply(item: ParsedItem, kind: QuestionKind, answer: String): ParsedItem? = when (kind) {
        QuestionKind.AMOUNT -> RuleBasedParser.parseAmount(answer)?.let { (q, u) -> item.copy(quantity = q, unit = u) }
        QuestionKind.STATE -> item.copy(state = answer.lowercase())
    }

    private fun requiresState(name: String): Boolean {
        val words = Words.tokens(name)
        return words.any { it in needsState } && words.none { it in prepared }
    }

    private fun amountQuestion(index: Int, item: ParsedItem): Question {
        val countable = item.name.split(" ").last().let { it.endsWith("s") && !it.endsWith("ss") }
        return if (countable) {
            Question(index, QuestionKind.AMOUNT, "How many ${item.name}?", listOf("1", "2", "3"))
        } else {
            Question(index, QuestionKind.AMOUNT, "How much ${item.name}?", listOf("100 g", "1 cup", "1 serving"))
        }
    }
}

data class AliasHit(val hasGrams: Boolean)
