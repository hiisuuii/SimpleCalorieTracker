package live.hisui.calorietracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortionConverterTest {
    private val rice = listOf(
        PortionOption("1 cup", 158.0),
        PortionOption("1 tbsp", 10.0),
    )
    private val chicken = listOf(
        PortionOption("1 medium breast", 172.0),
        PortionOption("1 slice", 28.0),
    )

    @Test
    fun foodPortionBeatsGenericTable() {
        assertEquals(79.0, PortionConverter.toGrams(0.5, "cup", rice)!!, 0.001)
    }

    @Test
    fun fallsBackToGenericTableWhenNoPortionMatches() {
        assertEquals(28.3495 * 2, PortionConverter.toGrams(2.0, "oz", rice)!!, 0.001)
        assertEquals(15.0, PortionConverter.toGrams(15.0, "g", emptyList())!!, 0.001)
        assertEquals(236.588, PortionConverter.toGrams(1.0, "cup", emptyList())!!, 0.001)
    }

    @Test
    fun pluralAndAbbreviatedUnitsMatch() {
        assertEquals(316.0, PortionConverter.toGrams(2.0, "cups", rice)!!, 0.001)
        assertEquals(56.0, PortionConverter.toGrams(2.0, "slices", chicken)!!, 0.001)
        assertEquals(14.7868, PortionConverter.toGrams(1.0, "Tbsp.", emptyList())!!, 0.001)
    }

    @Test
    fun matchesAnyWordOfPortionLabel() {
        assertEquals(172.0, PortionConverter.toGrams(1.0, "breast", chicken)!!, 0.001)
    }

    @Test
    fun unknownUnitReturnsNull() {
        assertNull(PortionConverter.toGrams(1.0, "handful", rice))
        assertNull(PortionConverter.toGrams(1.0, "", rice))
    }
}
