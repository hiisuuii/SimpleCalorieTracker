package live.hisui.calorietracker.data.net

import java.io.InterruptedIOException
import okhttp3.Interceptor
import okhttp3.Response

// USDA's front end intermittently answers valid requests with an HTML "400 Bad Request" page. Real API errors come
// back as JSON, so only the HTML 400s and server errors are retried.
class RetryTransientInterceptor(
    private val maxAttempts: Int = 3,
    private val backoffMs: Long = 400,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 1
        var response = chain.proceed(chain.request())
        while (attempt < maxAttempts && isTransient(response)) {
            response.close()
            try {
                Thread.sleep(backoffMs * attempt)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Interrupted while retrying")
            }
            if (chain.call().isCanceled()) throw InterruptedIOException("Canceled")
            attempt++
            response = chain.proceed(chain.request())
        }
        return response
    }

    private fun isTransient(response: Response): Boolean =
        response.code >= SERVER_ERROR || (response.code == BAD_REQUEST && response.body.contentType()?.subtype != "json")

    private companion object {
        const val BAD_REQUEST = 400
        const val SERVER_ERROR = 500
    }
}
