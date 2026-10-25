package live.hisui.calorietracker.data

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import live.hisui.calorietracker.data.db.DayTotal
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.db.FoodDao
import live.hisui.calorietracker.data.db.LogDao
import live.hisui.calorietracker.data.db.LogEntry
import live.hisui.calorietracker.domain.DayBoundary
import live.hisui.calorietracker.domain.EntrySource
import live.hisui.calorietracker.domain.Meal
import live.hisui.calorietracker.domain.MealTagger
import live.hisui.calorietracker.domain.Nutrition

@Singleton
class LogRepository @Inject constructor(
    private val log: LogDao,
    private val foods: FoodDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    val dailyTotals: Flow<List<DayTotal>> = log.dailyTotals()
    val recentFoodEntries: Flow<List<LogEntry>> = log.recentFoodEntries()

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentLogDate: Flow<LocalDate> = settings.settings
        .map { it.rolloverHour }
        .distinctUntilChanged()
        .flatMapLatest { hour ->
            flow {
                while (true) {
                    emit(DayBoundary.logDate(clock.instant(), clock.zone, hour))
                    delay(CLOCK_POLL_MS)
                }
            }
        }
        .distinctUntilChanged()

    fun entriesForDate(date: LocalDate): Flow<List<LogEntry>> = log.entriesForDate(date.toString())

    fun totalForDate(date: LocalDate): Flow<Double> = log.totalForDate(date.toString())

    suspend fun mealNow(): Meal =
        MealTagger.mealFor(clock.instant().atZone(clock.zone).toLocalTime(), settings.current())

    suspend fun logFood(food: Food, grams: Double, meal: Meal, source: EntrySource = EntrySource.MANUAL): Long {
        val now = clock.instant()
        val id = log.insert(foodEntry(food, grams, meal, source, now))
        foods.touch(food.id, now.toEpochMilli())
        return id
    }

    suspend fun logGroup(name: String, items: List<Pair<Food, Double>>, meal: Meal) {
        val now = clock.instant()
        val groupId = UUID.randomUUID().toString()
        log.insertAll(items.map { (food, grams) -> foodEntry(food, grams, meal, EntrySource.MANUAL, now).copy(groupId = groupId, groupName = name) })
        items.forEach { (food, _) -> foods.touch(food.id, now.toEpochMilli()) }
    }

    suspend fun deleteGroup(groupId: String) = log.deleteGroup(groupId)

    suspend fun renameGroup(groupId: String, name: String) = log.renameGroup(groupId, name)

    private suspend fun foodEntry(food: Food, grams: Double, meal: Meal, source: EntrySource, now: Instant): LogEntry {
        val n = Nutrition(food.kcalPer100g, food.proteinPer100g, food.carbsPer100g, food.fatPer100g).scaledTo(grams)
        return newEntry(now, meal, food.name, n.kcal, source).copy(
            grams = grams,
            proteinG = n.proteinG,
            carbsG = n.carbsG,
            fatG = n.fatG,
            foodId = food.id,
        )
    }

    suspend fun quickAdd(label: String, kcal: Double, meal: Meal): Long =
        log.insert(newEntry(clock.instant(), meal, label, kcal, EntrySource.QUICK_ADD))

    suspend fun relog(entry: LogEntry, meal: Meal): Long {
        val now = clock.instant()
        val copy = newEntry(now, meal, entry.name, entry.kcal, entry.source).copy(
            grams = entry.grams,
            proteinG = entry.proteinG,
            carbsG = entry.carbsG,
            fatG = entry.fatG,
            foodId = entry.foodId,
        )
        entry.foodId?.let { foods.touch(it, now.toEpochMilli()) }
        return log.insert(copy)
    }

    suspend fun update(entry: LogEntry) = log.update(entry)

    suspend fun delete(entry: LogEntry) = log.delete(entry)

    suspend fun get(id: Long): LogEntry? = log.byId(id)

    private suspend fun newEntry(now: Instant, meal: Meal, name: String, kcal: Double, source: EntrySource): LogEntry {
        val rollover = settings.current().rolloverHour
        return LogEntry(
            loggedAt = now.toEpochMilli(),
            tzId = clock.zone.id,
            logDate = DayBoundary.logDate(now, clock.zone, rollover).toString(),
            meal = meal,
            name = name,
            kcal = kcal,
            source = source,
        )
    }

    private companion object {
        const val CLOCK_POLL_MS = 60_000L
    }
}
