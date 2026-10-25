package live.hisui.calorietracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryAndNutritionTest {
    @Test
    fun ftsQueryBecomesPrefixTerms() {
        assertEquals("chick* breast*", FtsQuery.build("Chick  breast"))
        assertEquals("mac* cheese*", FtsQuery.build("mac & cheese!"))
    }

    @Test
    fun ftsQueryRejectsEmptyInput() {
        assertNull(FtsQuery.build("  "))
        assertNull(FtsQuery.build("\"()*"))
    }

    @Test
    fun nutritionScalesFromPer100g() {
        val scaled = Nutrition(kcal = 130.0, proteinG = 2.7, carbsG = 28.0, fatG = null).scaledTo(158.0)
        assertEquals(205.4, scaled.kcal, 0.001)
        assertEquals(4.266, scaled.proteinG!!, 0.001)
        assertEquals(44.24, scaled.carbsG!!, 0.001)
        assertNull(scaled.fatG)
    }

    @Test
    fun totalsConvertToPer100g() {
        val per100 = Nutrition(kcal = 2400.0, proteinG = 90.0).per100g(totalWeightG = 1800.0)
        assertEquals(133.333, per100.kcal, 0.001)
        assertEquals(5.0, per100.proteinG!!, 0.001)
    }
}
