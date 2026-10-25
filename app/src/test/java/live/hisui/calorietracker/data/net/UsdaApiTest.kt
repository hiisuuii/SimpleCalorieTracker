package live.hisui.calorietracker.data.net

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import live.hisui.calorietracker.domain.FoodSource
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class UsdaApiTest {
    private val server = MockWebServer()
    private lateinit var api: UsdaApi

    @Before
    fun setUp() {
        server.start()
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        api = Retrofit.Builder()
            .baseUrl(server.url("/fdc/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(UsdaApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun fixture(name: String) = javaClass.getResource("/usda/$name")!!.readText()

    @Test
    fun searchSendsKeyAndTypesAndMapsFoods() = runTest {
        server.enqueue(MockResponse().setBody(fixture("search.json")))

        val response = api.search("chicken", "Foundation,SR Legacy", 25, "KEY")
        val request = server.takeRequest()

        assertEquals("/fdc/v1/foods/search", request.requestUrl!!.encodedPath)
        assertEquals("chicken", request.requestUrl!!.queryParameter("query"))
        assertEquals("Foundation,SR Legacy", request.requestUrl!!.queryParameter("dataType"))
        assertEquals("KEY", request.requestUrl!!.queryParameter("api_key"))

        val candidates = response.foods.mapNotNull { it.toCandidate() }
        assertEquals(listOf("171077", "2646170", "1105904"), candidates.map { it.food.externalId })

        val chicken = candidates[0].food
        assertEquals(FoodSource.USDA, chicken.source)
        assertEquals("SR Legacy", chicken.dataType)
        assertEquals(165.0, chicken.kcalPer100g, 0.001)
        assertEquals(31.02, chicken.proteinPer100g!!, 0.001)
        assertEquals(3.57, chicken.fatPer100g!!, 0.001)
        assertEquals(0.0, chicken.carbsPer100g!!, 0.001)
    }

    @Test
    fun foodsWithoutEnergyAreDropped() = runTest {
        server.enqueue(MockResponse().setBody(fixture("search.json")))
        val foods = api.search("x", "Branded", 25, "KEY").foods
        assertNull(foods.first { it.fdcId == 999L }.toCandidate())
    }

    @Test
    fun foundationEnergyFallsBackToAtwaterFactor() = runTest {
        server.enqueue(MockResponse().setBody(fixture("search.json")))
        val rice = api.search("rice", "Foundation", 25, "KEY").foods.first { it.fdcId == 2646170L }.toCandidate()!!
        assertEquals(130.0, rice.food.kcalPer100g, 0.001)
    }

    @Test
    fun brandedFoodGetsServingPortionAndReadableName() = runTest {
        server.enqueue(MockResponse().setBody(fixture("search.json")))
        val cheese = api.search("cheese", "Branded", 25, "KEY").foods.first { it.fdcId == 1105904L }.toCandidate()!!

        assertEquals("Cheddar cheese", cheese.food.name)
        assertEquals("Acme Dairy", cheese.food.brand)
        assertEquals("0012345678905", cheese.food.barcode)
        assertEquals(listOf("1 serving (1 slice)" to 28.0), cheese.portions.map { it.label to it.grams })
    }

    @Test
    fun detailsPortionsAreNormalizedToOneUnit() = runTest {
        server.enqueue(MockResponse().setBody(fixture("details.json")))

        val portions = api.details(171077, "KEY").toPortions()

        assertEquals(
            listOf(
                "1 cup chopped or diced" to 172.0,
                "1 slices" to 28.0,
                "1 breast" to 172.0,
                "1 large" to 100.0,
            ),
            portions.map { it.label to it.grams },
        )
    }
}
