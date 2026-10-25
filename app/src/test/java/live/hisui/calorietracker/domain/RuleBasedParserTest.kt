package live.hisui.calorietracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RuleBasedParserTest(private val text: String, private val expected: List<ParsedItem>) {
    @Test
    fun parses() {
        assertEquals(expected, RuleBasedParser().parseSync(text))
    }

    companion object {
        private fun item(name: String, quantity: Double? = null, unit: String? = null, state: String? = null) =
            ParsedItem(name, state, quantity, unit)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf("2 eggs and a slice of toast", listOf(item("eggs", 2.0), item("toast", 1.0, "slice"))),
            arrayOf("half a cup of rice", listOf(item("rice", 0.5, "cup"))),
            arrayOf("chicken, rice, broccoli", listOf(item("chicken"), item("rice"), item("broccoli"))),
            arrayOf("200g grilled chicken breast", listOf(item("chicken breast", 200.0, "g", "grilled"))),
            arrayOf("1 1/2 cups cooked pasta", listOf(item("pasta", 1.5, "cup", "cooked"))),
            arrayOf("I had a banana with peanut butter", listOf(item("banana", 1.0), item("peanut butter"))),
            arrayOf("3 tbsp olive oil plus 2 slices bread", listOf(item("olive oil", 3.0, "tbsp"), item("bread", 2.0, "slice"))),
            arrayOf("a handful of almonds", listOf(item("almonds", 1.0, "handful"))),
            arrayOf(
                "chicken breast with rice and broccoli",
                listOf(item("chicken breast"), item("rice"), item("broccoli")),
            ),
            arrayOf("two eggs", listOf(item("eggs", 2.0))),
            arrayOf("1/2 avocado", listOf(item("avocado", 0.5))),
            arrayOf("a couple of eggs", listOf(item("eggs", 2.0))),
            arrayOf("cup of rice", listOf(item("rice", 1.0, "cup"))),
            arrayOf("For lunch I ate 150 g raw spinach", listOf(item("spinach", 150.0, "g", "raw"))),
            arrayOf("uncooked rice", listOf(item("rice", state = "raw"))),
            arrayOf("my rice", listOf(item("my rice"))),
            arrayOf("hadley bar", listOf(item("hadley bar"))),
            arrayOf("  ", emptyList<ParsedItem>()),
        )
    }
}

class ParseAmountTest {
    @Test
    fun readsQuantityWithOptionalUnit() {
        assertEquals(150.0 to "g", RuleBasedParser.parseAmount("150 g"))
        assertEquals(1.0 to "cup", RuleBasedParser.parseAmount("a cup"))
        assertEquals(2.0 to null, RuleBasedParser.parseAmount("2"))
        assertEquals(3.0 to "slice", RuleBasedParser.parseAmount("3 slices"))
        assertEquals(2.0 to "large", RuleBasedParser.parseAmount("2 large"))
    }

    @Test
    fun rejectsTextWithoutNumber() {
        assertNull(RuleBasedParser.parseAmount("lots"))
    }
}
