package live.hisui.calorietracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelParserTest {
    private fun parse(fixture: String) = LabelParser.parse(javaClass.getResource("/labels/$fixture")!!.readText())

    private fun assertLabel(
        label: ParsedLabel,
        kcal: Double,
        fat: Double,
        carbs: Double,
        protein: Double,
        grams: Double?,
    ) {
        assertEquals(kcal, label.kcal!!, 0.5)
        assertEquals(fat, label.fatG!!, 0.001)
        assertEquals(carbs, label.carbsG!!, 0.001)
        assertEquals(protein, label.proteinG!!, 0.001)
        assertEquals(grams, label.servingGrams)
    }

    @Test
    fun modernPanelWithCaloriesOnTheNextLine() {
        val label = parse("us_modern.txt")
        assertLabel(label, kcal = 230.0, fat = 8.0, carbs = 37.0, protein = 3.0, grams = 55.0)
        assertEquals("2/3 cup (55g)", label.servingText)
    }

    @Test
    fun realMlKitOutputWithDetachedValueColumn() {
        val label = parse("ml_kit_split_columns.txt")
        assertLabel(label, kcal = 230.0, fat = 8.0, carbs = 37.0, protein = 3.0, grams = 55.0)
        assertEquals("2/3 cup (55g)", label.servingText)
    }

    @Test
    fun oldStylePanelIgnoresCaloriesFromFatAndFooter() {
        assertLabel(parse("us_old_style.txt"), kcal = 250.0, fat = 12.0, carbs = 31.0, protein = 5.0, grams = 228.0)
    }

    @Test
    fun twoColumnPanelTakesThePerServingColumn() {
        assertLabel(parse("per_serving_per_container.txt"), kcal = 180.0, fat = 6.0, carbs = 24.0, protein = 10.0, grams = 40.0)
    }

    @Test
    fun ocrLookalikeLettersAreReadAsDigits() {
        assertLabel(parse("ocr_typos.txt"), kcal = 200.0, fat = 2.5, carbs = 40.0, protein = 0.0, grams = 30.0)
    }

    @Test
    fun metricPanelUsesTheKcalFigure() {
        assertLabel(parse("metric_kj_kcal.txt"), kcal = 230.0, fat = 8.0, carbs = 37.0, protein = 3.0, grams = 30.0)
    }

    @Test
    fun kilojouleOnlyEnergyIsConverted() {
        assertEquals(250.0, LabelParser.parse("Energy 1046 kJ").kcal!!, 0.5)
    }

    @Test
    fun textWithoutANutritionPanelParsesToNothing() {
        val label = parse("no_panel.txt")
        assertTrue(label.isEmpty)
        assertNull(label.kcal)
    }

    @Test
    fun valueOnTheLineBelowItsLabelIsFound() {
        val label = LabelParser.parse("Calories 100\nTotal Fat\n5g 7%\nProtein\n2g")
        assertEquals(5.0, label.fatG!!, 0.001)
        assertEquals(2.0, label.proteinG!!, 0.001)
    }

    @Test
    fun misreadDigitTripsTheSanityCheck() {
        val good = parse("us_modern.txt")
        assertTrue(NutritionSanity.kcalMatchesMacros(good.kcal!!, good.proteinG, good.carbsG, good.fatG))
        val misread = LabelParser.parse("Calories 230\nTotal Fat 89\nTotal Carbohydrate 37g\nProtein 3g")
        assertEquals(89.0, misread.fatG!!, 0.001)
        assertFalse(NutritionSanity.kcalMatchesMacros(misread.kcal!!, misread.proteinG, misread.carbsG, misread.fatG))
    }
}
