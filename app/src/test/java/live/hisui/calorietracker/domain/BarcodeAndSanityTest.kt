package live.hisui.calorietracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeNormalizerTest {
    @Test
    fun upcAAndEanFormsAreTheSameProduct() {
        assertEquals("0012345678905", BarcodeNormalizer.parseScan("012345678905"))
        assertEquals("0012345678905", BarcodeNormalizer.parseScan("0012345678905"))
        assertEquals("0012345678905", BarcodeNormalizer.parseScan("00012345678905"))
    }

    @Test
    fun ean13AndEan8PassThrough() {
        assertEquals("3017620422003", BarcodeNormalizer.parseScan("3017620422003"))
        assertEquals("96385074", BarcodeNormalizer.parseScan("96385074"))
    }

    @Test
    fun ignoresSpacesAndDashes() {
        assertEquals("3017620422003", BarcodeNormalizer.parseScan("3017 6204-22003"))
    }

    @Test
    fun rejectsBadCheckDigitAndWrongLength() {
        assertNull(BarcodeNormalizer.parseScan("3017620422004"))
        assertNull(BarcodeNormalizer.parseScan("12345"))
        assertNull(BarcodeNormalizer.parseScan(""))
    }

    @Test
    fun canonicalSkipsCheckDigitForStoredData() {
        assertEquals("0000000000017", BarcodeNormalizer.canonical("000000000017"))
        assertNull(BarcodeNormalizer.canonical("12345"))
        assertNull(BarcodeNormalizer.canonical("13017620422003"))
    }

    @Test
    fun upcAFormDropsTheLeadingZero() {
        assertEquals("012345678905", BarcodeNormalizer.upcA("0012345678905"))
        assertEquals("3017620422003", BarcodeNormalizer.upcA("3017620422003"))
    }
}

class NutritionSanityTest {
    @Test
    fun realisticFoodsPass() {
        assertTrue(NutritionSanity.plausiblePer100g(Nutrition(539.0, 6.3, 57.5, 30.9)))
        assertTrue(NutritionSanity.plausiblePer100g(Nutrition(165.0, 31.0, 0.0, 3.6)))
        assertTrue(NutritionSanity.plausiblePer100g(Nutrition(0.0, 0.0, 0.0, 0.0)))
    }

    @Test
    fun partialMacrosCannotBeChecked() {
        assertTrue(NutritionSanity.plausiblePer100g(Nutrition(400.0, 10.0, null, null)))
    }

    @Test
    fun caloriesFarFromMacrosFail() {
        assertFalse(NutritionSanity.plausiblePer100g(Nutrition(50.0, 0.0, 0.0, 30.0)))
        assertFalse(NutritionSanity.kcalMatchesMacros(230.0, 5.0, 30.0, 2.0))
    }

    @Test
    fun impossibleValuesFail() {
        assertFalse(NutritionSanity.plausiblePer100g(Nutrition(1200.0)))
        assertFalse(NutritionSanity.plausiblePer100g(Nutrition(100.0, proteinG = 150.0)))
        assertFalse(NutritionSanity.plausiblePer100g(Nutrition(-5.0)))
    }

    @Test
    fun smallAbsoluteDifferencesAreTolerated() {
        assertTrue(NutritionSanity.kcalMatchesMacros(12.0, 1.0, 1.0, 0.2))
    }
}
