package live.hisui.calorietracker.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import live.hisui.calorietracker.data.db.Alias
import live.hisui.calorietracker.data.db.AppDatabase
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.db.LogEntry
import live.hisui.calorietracker.data.db.Portion
import live.hisui.calorietracker.domain.EntrySource
import live.hisui.calorietracker.domain.FoodSource
import live.hisui.calorietracker.domain.Meal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupTest {
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var backup: BackupRepository
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val json = Json { ignoreUnknownKeys = true }
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            context.cacheDir.resolve("backup-test-${System.nanoTime()}.preferences_pb")
        }
        settings = SettingsRepository(store, json)
        backup = BackupRepository(db, db.logDao(), db.foodDao(), db.aliasDao(), db.savedMealDao(), settings, json, Clock.systemUTC())
    }

    @After
    fun tearDown() {
        db.close()
        scope.coroutineContext[Job]?.cancel()
    }

    private suspend fun seed(): LogEntry {
        val foodId = db.foodDao().insertWithPortions(
            Food(name = "Chicken, roasted", source = FoodSource.USDA, externalId = "1", kcalPer100g = 165.0, barcode = "0012345678905"),
            listOf(Portion(foodId = 0, label = "1 cup", grams = 140.0)),
        )
        db.aliasDao().upsert(Alias(phrase = "my chicken", foodId = foodId, defaultGrams = 150.0))
        val entry = LogEntry(
            loggedAt = 1_700_000_000_000, tzId = "America/Denver", logDate = "2026-10-20", meal = Meal.DINNER,
            name = "Chicken, roasted", grams = 150.0, kcal = 247.5, source = EntrySource.TEXT, foodId = foodId,
        )
        return entry.copy(id = db.logDao().insert(entry))
    }

    @Test
    fun jsonBackupRestoresEverythingOverUnrelatedData() = runTest {
        val original = seed()
        val file = ByteArrayOutputStream().also { backup.exportJson(it) }.toByteArray()

        db.logDao().deleteAll()
        db.foodDao().deleteAll()
        db.foodDao().insert(Food(name = "Unrelated", source = FoodSource.CUSTOM, kcalPer100g = 1.0))

        assertEquals(1, backup.importJson(ByteArrayInputStream(file)))

        assertEquals(listOf(original), db.logDao().all())
        assertEquals(listOf("Chicken, roasted"), db.foodDao().all().map { it.name })
        assertEquals(listOf(140.0), db.foodDao().allPortions().map { it.grams })
        assertEquals("my chicken", db.aliasDao().all().single().phrase)
        assertEquals(listOf("Chicken, roasted"), db.foodDao().search("chick*").map { it.name })
        assertEquals("0012345678905", db.foodDao().byBarcode("0012345678905")?.barcode)
    }

    @Test
    fun importRejectsFilesThatAreNotBackups() = runTest {
        try {
            backup.importJson(ByteArrayInputStream("not json".toByteArray()))
            fail("expected BackupFormatException")
        } catch (e: BackupFormatException) {
            assertTrue(e.message!!.contains("backup"))
        }
    }

    @Test
    fun csvQuotesCommasAndWritesOneRowPerEntry() = runTest {
        seed()
        val csv = ByteArrayOutputStream().also { backup.exportCsv(it) }.toString(Charsets.UTF_8)
        val lines = csv.trim().lines()
        assertEquals("date,time,meal,name,grams,kcal,protein_g,carbs_g,fat_g,source", lines[0])
        assertEquals("2026-10-20,15:13,DINNER,\"Chicken, roasted\",150.0,247.5,,,,TEXT", lines[1])
    }
}
