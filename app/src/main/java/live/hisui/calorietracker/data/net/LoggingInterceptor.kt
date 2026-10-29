package live.hisui.calorietracker.data.net

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

class LoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.newBuilder().removeAllQueryParameters("api_key").build()
        val start = System.nanoTime()
        try {
            val response = chain.proceed(request)
            Timber.tag("Http").d("%s %s -> %d (%d ms)", request.method, url, response.code, (System.nanoTime() - start) / 1_000_000)
            return response
        } catch (e: java.io.IOException) {
            Timber.tag("Http").w(e, "%s %s failed", request.method, url)
            throw e
        }
    }
}
