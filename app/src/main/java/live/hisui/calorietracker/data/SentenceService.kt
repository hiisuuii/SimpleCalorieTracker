package live.hisui.calorietracker.data

import javax.inject.Inject
import javax.inject.Singleton
import live.hisui.calorietracker.data.db.Alias
import live.hisui.calorietracker.data.db.AliasDao
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.db.Portion
import live.hisui.calorietracker.data.net.FoodCandidate
import live.hisui.calorietracker.domain.AmountResolver
import live.hisui.calorietracker.domain.EntrySource
import live.hisui.calorietracker.domain.Meal
import live.hisui.calorietracker.domain.MatchRanker
import live.hisui.calorietracker.domain.MealParser
import live.hisui.calorietracker.domain.ParsedItem
import live.hisui.calorietracker.domain.PortionOption
import live.hisui.calorietracker.domain.RankInput
import live.hisui.calorietracker.domain.Words

data class PendingItem(val item: ParsedItem, val alias: Alias?)

data class ResolvedItem(
    val pending: PendingItem,
    val options: List<FoodCandidate>,
    val selected: Int,
    val food: Food?,
    val portions: List<Portion>,
    val grams: Double?,
    val confident: Boolean,
    val searchError: String? = null,
)

@Singleton
class SentenceService @Inject constructor(
    private val parser: MealParser,
    private val aliases: AliasDao,
    private val foods: FoodRepository,
    private val log: LogRepository,
) {
    suspend fun parse(text: String): List<PendingItem> =
        parser.parse(text).map { PendingItem(it, aliases.byPhrase(it.phrase)) }

    suspend fun resolve(pending: PendingItem): ResolvedItem {
        pending.alias?.let { alias ->
            foods.get(alias.foodId)?.let { return build(pending, listOf(FoodCandidate(it, emptyList())), 0, true) }
        }
        val item = pending.item
        val local = foods.searchLocal(Words.tokens(item.name).joinToString(" "))
        val localIds = local.mapNotNull { it.externalId }.toSet()
        val usdaResult = foods.searchUsda(listOfNotNull(item.name, item.state).joinToString(" "), SEARCH_TYPES)
        val usda = usdaResult.getOrNull().orEmpty().filter { it.food.externalId !in localIds }

        val candidates = local.map { FoodCandidate(it, emptyList()) } + usda
        val inputs = candidates.map { c ->
            val bonus = if (c.food.id != 0L) LOCAL_BONUS + (if (c.food.isFavorite) FAVORITE_BONUS else 0.0) else 0.0
            RankInput(listOfNotNull(c.food.name, c.food.brand).joinToString(" "), c.food.dataType, bonus)
        }
        val result = MatchRanker.rank(item, inputs)
        val options = result.ranked.take(MAX_OPTIONS).map { candidates[it.index] }
        return build(pending, options, 0, result.confident)
            .copy(searchError = usdaResult.exceptionOrNull()?.toSearchMessage())
    }

    suspend fun select(resolved: ResolvedItem, index: Int): ResolvedItem =
        build(resolved.pending, resolved.options, index, true).copy(searchError = resolved.searchError)

    suspend fun logAll(items: List<Pair<ResolvedItem, Double>>, meal: Meal) {
        items.forEach { (resolved, grams) ->
            val food = resolved.food ?: return@forEach
            log.logFood(food, grams, meal, EntrySource.TEXT)
            aliases.upsert(Alias(phrase = resolved.pending.item.phrase, foodId = food.id, defaultGrams = grams))
        }
    }

    private suspend fun build(pending: PendingItem, options: List<FoodCandidate>, selected: Int, confident: Boolean): ResolvedItem {
        val chosen = options.getOrNull(selected) ?: return ResolvedItem(pending, options, selected, null, emptyList(), null, false)
        val food = foods.materialize(chosen)
        val portions = foods.portions(food.id)
        val grams = AmountResolver.grams(pending.item, portions.map { PortionOption(it.label, it.grams) }, pending.alias?.defaultGrams)
        val saved = options.toMutableList().also { it[selected] = FoodCandidate(food, emptyList()) }
        return ResolvedItem(pending, saved, selected, food, portions, grams, confident)
    }

    private companion object {
        const val SEARCH_TYPES = "Foundation,SR Legacy,Survey (FNDDS)"
        const val MAX_OPTIONS = 5
        const val LOCAL_BONUS = 0.2
        const val FAVORITE_BONUS = 0.05
    }
}
