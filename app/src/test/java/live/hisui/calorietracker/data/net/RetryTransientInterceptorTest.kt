package live.hisui.calorietracker.data.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RetryTransientInterceptorTest {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().addInterceptor(RetryTransientInterceptor(maxAttempts = 3, backoffMs = 0)).build()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun html400() = MockResponse().setResponseCode(400).setHeader("Content-Type", "text/html").setBody("<h1>400 Bad Request</h1>")

    private fun get(): Int = client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { it.code }

    @Test
    fun htmlBadRequestIsRetriedUntilItSucceeds() {
        server.enqueue(html400())
        server.enqueue(html400())
        server.enqueue(MockResponse().setBody("{}"))
        assertEquals(200, get())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun serverErrorsAreRetried() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("{}"))
        assertEquals(200, get())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun jsonApiErrorsAreNotRetried() {
        server.enqueue(MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json").setBody("""{"error":{"code":"BAD"}}"""))
        assertEquals(400, get())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun otherClientErrorsAreNotRetried() {
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(403, get())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun givesUpAfterTheAttemptLimitAndReturnsTheLastResponse() {
        repeat(5) { server.enqueue(html400()) }
        assertEquals(400, get())
        assertEquals(3, server.requestCount)
    }
}
