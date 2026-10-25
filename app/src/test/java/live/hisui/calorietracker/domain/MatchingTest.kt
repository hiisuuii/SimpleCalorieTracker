package live.hisui.calorietracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchRankerTest {
    private val chickenCooked = RankInput("Chicken, broilers or fryers, breast, meat only, cooked, roasted", "SR Legacy")
    private val chickenRaw = RankInput("Chicken, broilers or fryers, breast, meat only, raw", "SR Legacy")
    private val chickenBranded = RankInput("Chicken breast tenders, breaded, cooked", "Branded")

    private fun topIndex(item: ParsedItem, inputs: List<RankInput>) = MatchRanker.rank(item, inputs).ranked.first().index

    @Test
    fun cookedStatePrefersCookedDescription() {
        val item = ParsedItem("chicken breast", "cooked", null, null)
        assertEquals(1, topIndex(item, listOf(chickenRaw, chickenCooked)))
    }

    @Test
    fun rawStatePrefersRawDescription() {
        val item = ParsedItem("chicken breast", "raw", null, null)
        assertEquals(1, topIndex(item, listOf(chickenCooked, chickenRaw)))
    }

    @Test
    fun cookingVerbMatchesExactWordBestButAcceptsOtherCookedForms() {
        val item = ParsedItem("chicken breast", "grilled", null, null)
        val grilled = RankInput("Chicken, breast, grilled", "SR Legacy")
        assertEquals(1, topIndex(item, listOf(chickenCooked, grilled)))
        assertEquals(0, topIndex(item, listOf(chickenCooked, chickenRaw)))
    }

    @Test
    fun generalFoodDataBeatsBrandedWhenOtherwiseEqual() {
        val item = ParsedItem("chicken breast", "cooked", null, null)
        assertEquals(0, topIndex(item, listOf(chickenCooked, chickenBranded)))
    }

    @Test
    fun localBonusLiftsFoodsYouAlreadyUse() {
        val item = ParsedItem("rice", null, null, null)
        val local = RankInput("Rice, white, cooked", null, bonus = 0.3)
        val usda = RankInput("Rice, white, long-grain, regular, cooked", "SR Legacy")
        assertEquals(0, topIndex(item, listOf(local, usda)))
    }

    @Test
    fun clearWinnerIsConfidentAndCloseCallIsNot() {
        val item = ParsedItem("chicken breast", "cooked", null, null)
        assertTrue(MatchRanker.rank(item, listOf(chickenCooked, chickenRaw)).confident)
        val twin = RankInput("Chicken, breast, cooked, roasted", "SR Legacy")
        assertFalse(MatchRanker.rank(item, listOf(chickenCooked, twin)).confident)
    }

    @Test
    fun poorOverlapIsNeverConfident() {
        val item = ParsedItem("dragonfruit smoothie bowl", null, null, null)
        assertFalse(MatchRanker.rank(item, listOf(RankInput("Smoothie, banana", "Survey (FNDDS)"))).confident)
    }

    @Test
    fun emptyInputsGiveEmptyResult() {
        assertEquals(emptyList<Ranked>(), MatchRanker.rank(ParsedItem("x", null, null, null), emptyList()).ranked)
    }
}

class ClarificationPlannerTest {
    private fun questions(item: ParsedItem, alias: AliasHit? = null) = ClarificationPlanner.questionsFor(0, item, alias)

    @Test
    fun asksAmountAndStateForBareGrain() {
        val q = questions(ParsedItem("rice", null, null, null))
        assertEquals(listOf(QuestionKind.AMOUNT, QuestionKind.STATE), q.map { it.kind })
        assertEquals("How much rice?", q[0].prompt)
        assertTrue("100 g" in q[0].options && "1 cup" in q[0].options)
        assertEquals("Cooked or raw?", q[1].prompt)
    }

    @Test
    fun countableNamesAskHowMany() {
        assertEquals("How many chicken breasts?", questions(ParsedItem("chicken breasts", "cooked", null, null)).single().prompt)
    }

    @Test
    fun skipsWhatIsAlreadyKnown() {
        assertEquals(emptyList<Question>(), questions(ParsedItem("rice", "cooked", 1.0, "cup")))
        assertEquals(emptyList<Question>(), questions(ParsedItem("banana", null, 1.0, null)))
        assertEquals(listOf(QuestionKind.AMOUNT), questions(ParsedItem("banana", null, null, null)).map { it.kind })
    }

    @Test
    fun preparedFoodsDoNotNeedAState() {
        assertEquals(emptyList<Question>(), questions(ParsedItem("chicken salad", null, 1.0, "cup")))
        assertEquals(emptyList<Question>(), questions(ParsedItem("green beans", null, 1.0, "cup")))
    }

    @Test
    fun aliasWithDefaultAmountNeedsNoQuestions() {
        assertEquals(emptyList<Question>(), questions(ParsedItem("my rice", null, null, null), AliasHit(hasGrams = true)))
        assertEquals(1, questions(ParsedItem("my rice", null, null, null), AliasHit(hasGrams = false)).size)
    }

    @Test
    fun answersAreAppliedToTheItem() {
        val item = ParsedItem("rice", null, null, null)
        assertEquals(item.copy(quantity = 1.0, unit = "cup"), ClarificationPlanner.apply(item, QuestionKind.AMOUNT, "1 cup"))
        assertEquals(item.copy(state = "raw"), ClarificationPlanner.apply(item, QuestionKind.STATE, "Raw"))
        assertNull(ClarificationPlanner.apply(item, QuestionKind.AMOUNT, "lots"))
    }
}

class AmountResolverTest {
    private val egg = listOf(PortionOption("1 large", 50.0), PortionOption("1 medium", 44.0))

    @Test
    fun unitQuantityUsesPortionsThenGenericTable() {
        assertEquals(100.0, AmountResolver.grams(ParsedItem("x", null, 2.0, "large"), egg, null)!!, 0.001)
        assertEquals(200.0, AmountResolver.grams(ParsedItem("x", null, 200.0, "g"), egg, null)!!, 0.001)
    }

    @Test
    fun bareCountUsesMediumPortionFirst() {
        assertEquals(88.0, AmountResolver.grams(ParsedItem("eggs", null, 2.0, null), egg, null)!!, 0.001)
    }

    @Test
    fun missingQuantityFallsBackToAliasGrams() {
        assertEquals(180.0, AmountResolver.grams(ParsedItem("my rice", null, null, null), emptyList(), 180.0)!!, 0.001)
    }

    @Test
    fun unresolvableAmountsAreNull() {
        assertNull(AmountResolver.grams(ParsedItem("x", null, 1.0, "handful"), egg, null))
        assertNull(AmountResolver.grams(ParsedItem("x", null, 2.0, null), emptyList(), null))
    }
}
