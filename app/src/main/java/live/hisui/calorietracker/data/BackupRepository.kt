package live.hisui.calorietracker.data

import androidx.room.withTransaction
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import live.hisui.calorietracker.data.db.Alias
import live.hisui.calorietracker.data.db.AliasDao
import live.hisui.calorietracker.data.db.AppDatabase
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.db.FoodDao
import live.hisui.calorietracker.data.db.LogDao
import live.hisui.calorietracker.data.db.LogEntry
import live.hisui.calorietracker.data.db.Portion
import live.hisui.calorietracker.data.db.SavedMeal
import live.hisui.calorietracker.data.db.SavedMealDao
import live.hisui.calorietracker.data.db.SavedMealItem
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter

@Serializable
data class BackupSettings(
    val dailyTarget: Int,
    val useOunces: Boolean,
    val rolloverHour: Int,
    val breakfastMinute: Int,
    val lunchMinute: Int,
    val dinnerMinute: Int,
)

@Serializable
data class BackupFile(
    val version: Int,
    val exportedAt: Long,
    val settings: BackupSettings,
    val foods: List<Food>,
    val portions: List<Portion>,
    val aliases: List<Alias>,
    val savedMeals: List<SavedMeal>,
    val savedMealItems: List<SavedMealItem>,
    val logEntries: List<LogEntry>,
)

class BackupFormatException(message: String) : Exception(message)

@Singleton
class BackupRepository @Inject constructor(
    private val db: AppDatabase,
    private val logDao: LogDao,
    private val foodDao: FoodDao,
    private val aliasDao: AliasDao,
    private val savedMealDao: SavedMealDao,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
    private val clock: Clock,
) {
    suspend fun exportJson(out: OutputStream) {
        val s = settingsRepository.current()
        val file = BackupFile(
            version = BACKUP_VERSION,
            exportedAt = clock.millis(),
            settings = BackupSettings(
                s.dailyTarget, s.useOunces, s.rolloverHour,
                s.breakfast.toMinuteOfDay(), s.lunch.toMinuteOfDay(), s.dinner.toMinuteOfDay(),
            ),
            foods = foodDao.all(),
            portions = foodDao.allPortions(),
            aliases = aliasDao.all(),
            savedMeals = savedMealDao.allMeals(),
            savedMealItems = savedMealDao.allItems(),
            logEntries = logDao.all(),
        )
        out.write(json.encodeToString(BackupFile.serializer(), file).toByteArray(Charsets.UTF_8))
    }

    // Replaces everything on the phone; the USDA key and reminders are device-specific and kept.
    suspend fun importJson(input: InputStream): Int {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), input.readBytes().toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw BackupFormatException("That file isn't a Calorie Tracker backup.")
        }
        if (file.version != BACKUP_VERSION) throw BackupFormatException("Unsupported backup version ${file.version}.")

        db.withTransaction {
            logDao.deleteAll()
            aliasDao.deleteAll()
            savedMealDao.deleteAll()
            foodDao.deleteAll()
            foodDao.insertAll(file.foods)
            foodDao.insertPortions(file.portions)
            aliasDao.insertAll(file.aliases)
            savedMealDao.insertMeals(file.savedMeals)
            savedMealDao.insertItems(file.savedMealItems)
            logDao.insertAll(file.logEntries)
        }
        val b = file.settings
        settingsRepository.update(
            settingsRepository.current().copy(
                dailyTarget = b.dailyTarget,
                useOunces = b.useOunces,
                rolloverHour = b.rolloverHour,
                breakfast = b.breakfastMinute.toLocalTime(),
                lunch = b.lunchMinute.toLocalTime(),
                dinner = b.dinnerMinute.toLocalTime(),
            ),
        )
        return file.logEntries.size
    }

    suspend fun exportCsv(out: OutputStream) {
        val writer = out.writer(Charsets.UTF_8)
        CSVPrinter(writer, CSVFormat.DEFAULT).use { csv ->
            csv.printRecord("date", "time", "meal", "name", "grams", "kcal", "protein_g", "carbs_g", "fat_g", "source")
            logDao.all().forEach { e ->
                val time = Instant.ofEpochMilli(e.loggedAt).atZone(ZoneId.of(e.tzId)).toLocalTime().format(TIME)
                csv.printRecord(e.logDate, time, e.meal, e.name, e.grams, e.kcal, e.proteinG, e.carbsG, e.fatG, e.source)
            }
        }
    }

    private companion object {
        const val BACKUP_VERSION = 1
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

private fun LocalTime.toMinuteOfDay() = hour * 60 + minute

private fun Int.toLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)
