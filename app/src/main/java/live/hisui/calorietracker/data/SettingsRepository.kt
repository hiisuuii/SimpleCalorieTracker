package live.hisui.calorietracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import live.hisui.calorietracker.domain.Settings

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {
    private val target = intPreferencesKey("daily_target")
    private val ounces = booleanPreferencesKey("use_ounces")
    private val rollover = intPreferencesKey("rollover_hour")
    private val breakfast = intPreferencesKey("breakfast_minute")
    private val lunch = intPreferencesKey("lunch_minute")
    private val dinner = intPreferencesKey("dinner_minute")
    private val usdaKey = stringPreferencesKey("usda_api_key")

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
        }
    }
}

private fun LocalTime.toMinutes() = hour * 60 + minute

private fun Int.toTime(): LocalTime = LocalTime.of(this / 60, this % 60)
