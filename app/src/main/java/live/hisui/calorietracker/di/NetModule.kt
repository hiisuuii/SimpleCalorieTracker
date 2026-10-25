package live.hisui.calorietracker.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import live.hisui.calorietracker.BuildConfig
import live.hisui.calorietracker.data.net.OffApi
import live.hisui.calorietracker.data.net.RetryTransientInterceptor
import live.hisui.calorietracker.data.net.UsdaApi
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetModule {
    private const val USDA_BASE_URL = "https://api.nal.usda.gov/fdc/v1/"
    private const val OFF_BASE_URL = "https://world.openfoodfacts.org/api/v2/"
    private const val CACHE_BYTES = 10L * 1024 * 1024
    private const val CACHE_SECONDS = 24 * 60 * 60

    @Provides
    @Singleton
    fun json() = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun okHttp(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), CACHE_BYTES))
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "CalorieTracker/${BuildConfig.VERSION_NAME} (Android)")
                    .build(),
            )
        }
        .addInterceptor(RetryTransientInterceptor())
        // USDA sends no caching headers, so repeated searches would always hit the network.
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).newBuilder()
                .header("Cache-Control", "public, max-age=$CACHE_SECONDS")
                .build()
        }
        .build()

    @Provides
    @Singleton
    fun usdaApi(client: OkHttpClient, json: Json): UsdaApi = retrofit(USDA_BASE_URL, client, json).create(UsdaApi::class.java)

    @Provides
    @Singleton
    fun offApi(client: OkHttpClient, json: Json): OffApi = retrofit(OFF_BASE_URL, client, json).create(OffApi::class.java)

    private fun retrofit(baseUrl: String, client: OkHttpClient, json: Json) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
