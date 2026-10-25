package live.hisui.calorietracker.domain

object BarcodeNormalizer {
    // A 12-digit UPC-A is the same product as its 13-digit EAN form with a leading zero, so 13 digits is canonical.
    fun canonical(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when (digits.length) {
            EAN8 -> digits
            UPC_A -> "0$digits"
            EAN13 -> digits
            GTIN14 -> digits.takeIf { it.startsWith("0") }?.drop(1)
            else -> null
        }
    }

    fun parseScan(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return if (hasValidCheckDigit(digits)) canonical(digits) else null
    }

    // USDA indexes most branded products by the 12-digit form.
    fun upcA(canonical: String): String = if (canonical.length == EAN13 && canonical.startsWith("0")) canonical.drop(1) else canonical

    private fun hasValidCheckDigit(digits: String): Boolean {
        if (digits.length !in setOf(EAN8, UPC_A, EAN13, GTIN14)) return false
        val sum = digits.dropLast(1).reversed().mapIndexed { i, c -> c.digitToInt() * if (i % 2 == 0) 3 else 1 }.sum()
        return (10 - sum % 10) % 10 == digits.last().digitToInt()
    }

    private const val EAN8 = 8
    private const val UPC_A = 12
    private const val EAN13 = 13
    private const val GTIN14 = 14
}
