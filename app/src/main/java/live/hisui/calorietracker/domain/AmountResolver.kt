package live.hisui.calorietracker.domain

object AmountResolver {
    fun grams(item: ParsedItem, portions: List<PortionOption>, aliasGrams: Double?): Double? {
        val quantity = item.quantity ?: return aliasGrams
        val unit = item.unit
        return if (unit != null) {
            PortionConverter.toGrams(quantity, unit, portions)
        } else {
            PortionConverter.defaultCountPortion(portions)?.let { quantity * it.grams }
        }
    }
}
