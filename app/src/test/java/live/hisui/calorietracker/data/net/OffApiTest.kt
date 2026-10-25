package live.hisui.calorietracker.data.net

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import live.hisui.calorietracker.domain.FoodSource
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class OffApiTest {
    private val server = MockWebServer()
    private lateinit var api: OffApi

    @Before
    fun setUp() {
        server.start()
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/v2/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OffApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    private suspend fun candidate(fixture: String, barcode: String): FoodCandidate? {
        server.enqueue(MockResponse().setBody(javaClass.getResource("/off/$fixture")!!.readText()))
        return api.product(barcode).product?.toCandidate(barcode)
    }

    @Test
    fun requestsProductByBarcodeWithFieldFilter() = runTest {
        candidate("product_ok.json", "3017620422003")
        val request = server.takeRequest()
        assertEquals("/api/v2/product/3017620422003", request.requestUrl!!.encodedPath)
        assertEquals(OffApi.FIELDS, request.requestUrl!!.queryParameter("fields"))
    }

    @Test
    fun completeProductMapsPerHundredGramsAndServing() = runTest {
        val c = candidate("product_ok.json", "3017620422003")!!
        assertEquals(FoodSource.OFF, c.food.source)
        assertEquals("Nutella", c.food.name)
        assertEquals("Ferrero", c.food.brand)
        assertEquals("3017620422003", c.food.barcode)
        assertEquals(539.0, c.food.kcalPer100g, 0.001)
        assertEquals(30.9, c.food.fatPer100g!!, 0.001)
        assertEquals(listOf("1 serving (15 g)" to 15.0), c.portions.map { it.label to it.grams })
    }

    @Test
    fun kilojouleOnlyProductWithStringNumbersIsConverted() = runTest {
        val c = candidate("product_kj_only_strings.json", "5000000000017")!!
        assertEquals(250.0, c.food.kcalPer100g, 0.5)
        assertEquals(20.0, c.portions.single().grams, 0.001)
        assertNull(c.food.brand)
    }

    @Test
    fun perServingCaloriesAreScaledToPerHundredGrams() = runTest {
        val c = candidate("product_serving_only.json", "5000000000024")!!
        assertEquals(400.0, c.food.kcalPer100g, 0.001)
    }

    @Test
    fun servingGramsAreReadFromServingTextWhenQuantityIsMissing() {
        val product = OffProduct(
            productName = "Spread",
            servingSize = "2 tbsp (30,5 g)",
            nutriments = mapOf("energy-kcal_100g" to JsonPrimitive(400)),
        )
        val portion = product.toCandidate("5000000000055")!!.portions.single()
        assertEquals(30.5, portion.grams, 0.001)
        assertEquals("1 serving (2 tbsp (30,5 g))", portion.label)
    }

    @Test
    fun productWithoutCaloriesIsRejectedSoCallerFallsBack() = runTest {
        assertNull(candidate("product_no_energy.json", "5000000000031"))
    }

    @Test
    fun caloriesContradictingMacrosAreRejected() = runTest {
        assertNull(candidate("product_bad_macros.json", "5000000000048"))
    }

    @Test
    fun unknownProductIsA404() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"status":0,"status_verbose":"product not found"}"""))
        val error = runCatching { api.product("0000000000000") }.exceptionOrNull()
        assertNotNull(error)
        assertEquals(404, (error as HttpException).code())
    }
}
