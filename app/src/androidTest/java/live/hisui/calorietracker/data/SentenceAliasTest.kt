package live.hisui.calorietracker.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import live.hisui.calorietracker.data.db.AppDatabase
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.net.UsdaApi
import live.hisui.calorietracker.data.net.UsdaDetails
import live.hisui.calorietracker.data.net.UsdaSearchResponse
import live.hisui.calorietracker.domain.AliasHit
import live.hisui.calorietracker.domain.ClarificationPlanner
import live.hisui.calorietracker.domain.FoodSource
import live.hisui.calorietracker.domain.Meal
import live.hisui.calorietracker.domain.QuestionKind
import live.hisui.calorietracker.domain.RuleBasedParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

internal object OfflineUsda : UsdaApi {
    override suspend fun search(query: String, dataTypes: String, pageSize: Int, apiKey: String): UsdaSearchResponse =
        throw IOException("offline")

    override suspend fun details(fdcId: Long, apiKey: String): UsdaDetails = throw IOException("offline")
}

@RunWith(AndroidJUnit4::class)
class SentenceAliasTest {
    private lateinit var db: AppDatabase
    private lateinit var service: SentenceService
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { context.cacheDir.resolve("alias-test-${System.nanoTime()}.preferences_pb") },
            Json,
        )
        val foods = FoodRepository(db.foodDao(), OfflineUsda, settings)
        val log = LogRepository(db.logDao(), db.foodDao(), settings, Clock.systemDefaultZone(), LogTarget())
        service = SentenceService(RuleBasedParser(), db.aliasDao(), foods, log)
    }

    @After
    fun tearDown() {
        db.close()
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun aGrainOrMeatPhraseNeedsNoQuestionsTheSecondTime() = runTest {
        db.foodDao().insertWithPortions(Food(name = "Chicken breast, cooked", source = FoodSource.CUSTOM, kcalPer100g = 165.0), emptyList())

        val first = service.parse("chicken breast").single()
        assertEquals(listOf(QuestionKind.AMOUNT, QuestionKind.STATE), ClarificationPlanner.questionsFor(0, first.item, null).map { it.kind })

        val answered = ClarificationPlanner.apply(first.item, QuestionKind.STATE, "Cooked")!!
            .let { ClarificationPlanner.apply(it, QuestionKind.AMOUNT, "150 g")!! }
        val resolved = service.resolve(first.copy(item = answered))
        assertEquals("Chicken breast, cooked", resolved.food!!.name)
        assertEquals(150.0, resolved.grams!!, 0.001)
        service.logAll(listOf(resolved to 150.0), Meal.LUNCH)

        listOf("chicken breast", "Chicken breasts").forEach { typed ->
            val again = service.parse(typed).single()
            val alias = again.alias ?: error("no alias for '$typed'")
            assertTrue(ClarificationPlanner.questionsFor(0, again.item, AliasHit(alias.defaultGrams != null)).isEmpty())
            assertEquals(150.0, service.resolve(again).grams!!, 0.001)
        }
    }
}
