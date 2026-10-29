package live.hisui.calorietracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MealShareTest {
    private val waffles = SharedMeal(
        name = "Waffle plate",
        totalGrams = null,
        totalServings = 10.0,
        items = listOf(
            SharedFood("Eggs, Grade A, Large, egg whole", 148.0, 12.4, 0.96, 9.96, 150.0),
            SharedFood("Wheat flour, white, all-purpose, enriched, bleached", 364.0, 10.3, 76.3, 1.0, 250.0),
            SharedFood("Milk, whole, 3.25% milkfat", 61.0, 3.2, 4.8, 3.3, 300.0),
            SharedFood("Butter, salted", 717.0, 0.9, 0.1, 81.1, 60.0),
            SharedFood("Sugar, granulated", 387.0, null, 100.0, null, 25.0),
        ),
    )

    @Test
    fun roundTrips() {
        assertEquals(waffles, MealShare.decode(MealShare.encode(waffles)))
    }

    @Test
    fun reportsCodeLength() {
        val code = MealShare.encode(waffles)
        println("EMBEDDED-SIZE ${code.length} chars for ${waffles.items.size} foods: $code")
        val idsOnly = SharedMeal(waffles.name, null, 10.0, waffles.items.mapIndexed { i, f -> SharedFood("U${748967 + i * 1000}", 0.0, null, null, null, f.grams) })
        println("IDS-ONLY-SIZE ${MealShare.encode(idsOnly).length} chars")
    }

    @Test
    fun rejectsGarbage() {
        assertThrows(MealCodeException::class.java) { MealShare.decode("hello world") }
        assertThrows(MealCodeException::class.java) { MealShare.decode("") }
        assertThrows(MealCodeException::class.java) { MealShare.decode(MealShare.encode(waffles).dropLast(10)) }
    }
}
