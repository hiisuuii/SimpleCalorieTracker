package live.hisui.calorietracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import live.hisui.calorietracker.domain.Meal
import live.hisui.calorietracker.domain.Reminder
import live.hisui.calorietracker.domain.Settings

@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    private val json: Json,
) {
    private val target = intPreferencesKey("daily_target")
    private val ounces = booleanPreferencesKey("use_ounces")
    private val rollover = intPreferencesKey("rollover_hour")
    private val breakfast = intPreferencesKey("breakfast_minute")
    private val lunch = intPreferencesKey("lunch_minute")
    private val dinner = intPreferencesKey("dinner_minute")
    private val usdaKey = stringPreferencesKey("usda_api_key")
    private val reminders = stringPreferencesKey("reminders")
    private val middayFraction = doublePreferencesKey("midday_fraction")
    private val notifyOver = booleanPreferencesKey("notify_over")
    private val reminderSerializer = ListSerializer(Reminder.serializer())

    private val collapsed = stringSetPreferencesKey("collapsed_meals")

    val collapsedMeals: Flow<Set<Meal>> = store.data.map { p ->
        p[collapsed].orEmpty().mapNotNull { name -> Meal.entries.firstOrNull { it.name == name } }.toSet()
    }

    suspend fun setMealCollapsed(meal: Meal, isCollapsed: Boolean) {
        store.edit { p -> p[collapsed] = p[collapsed].orEmpty().let { if (isCollapsed) it + meal.name else it - meal.name } }
    }

    val settings: Flow<Settings> = store.data.map { p ->
        val d = Settings()
        Settings(
            dailyTarget = p[target] ?: d.dailyTarget,
            useOunces = p[ounces] ?: d.useOunces,
            rolloverHour = p[rollover] ?: d.rolloverHour,
            breakfast = p[breakfast]?.toTime() ?: d.breakfast,
            lunch = p[lunch]?.toTime() ?: d.lunch,
            dinner = p[dinner]?.toTime() ?: d.dinner,
            usdaApiKey = p[usdaKey] ?: d.usdaApiKey,
            reminders = p[reminders]?.let { json.decodeFromString(reminderSerializer, it) } ?: d.reminders,
            middayFraction = p[middayFraction] ?: d.middayFraction,
            notifyWhenOver = p[notifyOver] ?: d.notifyWhenOver,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun update(settings: Settings) {
        store.edit { p ->
            p[target] = settings.dailyTarget
            p[ounces] = settings.useOunces
            p[rollover] = settings.rolloverHour
            p[breakfast] = settings.breakfast.toMinutes()
            p[lunch] = settings.lunch.toMinutes()
            p[dinner] = settings.dinner.toMinutes()
            p[usdaKey] = settings.usdaApiKey
            p[reminders] = json.encodeToString(reminderSerializer, settings.reminders)
            p[middayFraction] = settings.middayFraction
            p[notifyOver] = settings.notifyWhenOver
        }
    }
}

private fun LocalTime.toMinutes() = hour * 60 + minute

private fun Int.toTime(): LocalTime = LocalTime.of(this / 60, this % 60)
