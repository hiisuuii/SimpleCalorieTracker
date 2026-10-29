package live.hisui.calorietracker.data

import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.db.FoodDao
import live.hisui.calorietracker.data.db.Portion
import live.hisui.calorietracker.data.net.FoodCandidate
import live.hisui.calorietracker.data.net.UsdaApi
import live.hisui.calorietracker.data.net.toCandidate
import live.hisui.calorietracker.data.net.toPortions
import live.hisui.calorietracker.domain.FoodSource
import live.hisui.calorietracker.domain.FtsQuery
import retrofit2.HttpException

@Singleton
class FoodRepository @Inject constructor(
    private val foods: FoodDao,
    private val usda: UsdaApi,
    private val settings: SettingsRepository,
) {
    val recents: Flow<List<Food>> = foods.recents()
    val favorites: Flow<List<Food>> = foods.favorites()
    val customFoods: Flow<List<Food>> = foods.customFoods()

    // The check-then-insert below spans a network call; without this a double tap inserts the same external id twice.
    private val saveLock = Mutex()

    suspend fun get(id: Long): Food? = foods.byId(id)

    suspend fun portions(foodId: Long): List<Portion> = foods.portionsFor(foodId)

    suspend fun searchLocal(query: String): List<Food> {
        val fts = FtsQuery.build(query) ?: return emptyList()
        return foods.search(fts)
            .sortedWith(
                compareByDescending<Food> { it.isFavorite }
                    .thenByDescending { it.lastUsedAt ?: 0L }
                    .thenBy { it.name.length },
            )
    }

    suspend fun searchUsda(query: String, dataTypes: String = ALL_TYPES, pageSize: Int = 25): Result<List<FoodCandidate>> =
        try {
            val response = usda.search(query, dataTypes, pageSize, apiKey())
            val found = response.foods.mapNotNull { it.toCandidate() }
            Result.success(found.sortedBy { it.food.dataType == BRANDED })
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: SerializationException) {
            Result.failure(e)
        }

    suspend fun saveUsdaCandidate(candidate: FoodCandidate): Food = saveLock.withLock {
        val externalId = checkNotNull(candidate.food.externalId)
        foods.byExternalId(FoodSource.USDA, externalId)?.let { return@withLock it }
        val portions = if (candidate.food.dataType == BRANDED) candidate.portions else fetchPortions(externalId, candidate.portions)
        val id = foods.insertWithPortions(candidate.food, portions)
        candidate.food.copy(id = id)
    }

    suspend fun materialize(candidate: FoodCandidate): Food = when {
        candidate.food.id != 0L -> candidate.food
        candidate.food.source == FoodSource.OFF -> saveOffCandidate(candidate)
        else -> saveUsdaCandidate(candidate)
    }

    suspend fun saveOffCandidate(candidate: FoodCandidate): Food = saveLock.withLock {
        val externalId = checkNotNull(candidate.food.externalId)
        foods.byExternalId(FoodSource.OFF, externalId)?.let { return@withLock it }
        val id = foods.insertWithPortions(candidate.food, candidate.portions)
        candidate.food.copy(id = id)
    }

    suspend fun findByBarcode(barcode: String): Food? = foods.byBarcode(barcode)

    suspend fun saveCustom(food: Food, portions: List<Portion>): Long {
        if (food.id == 0L) return foods.insertWithPortions(food, portions)
        foods.update(food)
        foods.replacePortions(food.id, portions)
        return food.id
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = foods.setFavorite(id, favorite)

    suspend fun delete(id: Long) = foods.delete(id)

    private suspend fun fetchPortions(externalId: String, fallback: List<Portion>): List<Portion> =
        try {
            usda.details(externalId.toLong(), apiKey()).toPortions()
        } catch (e: IOException) {
            fallback
        } catch (e: HttpException) {
            fallback
        } catch (e: SerializationException) {
            fallback
        }

    private suspend fun apiKey() = settings.current().usdaApiKey.ifBlank { DEMO_KEY }

    companion object {
        const val DEMO_KEY = "DEMO_KEY"
        const val BRANDED = "Branded"
        const val ALL_TYPES = "Foundation,SR Legacy,Survey (FNDDS),Branded"
    }
}

fun Throwable.toSearchMessage(): String {
    Log.w("FoodLookup", "online lookup failed", this)
    return when {
        this is IOException -> "No connection. Saved foods still work offline."
        this is HttpException && code() == 429 -> "USDA rate limit reached. Add your own API key in Settings."
        this is HttpException -> {
            val apiCode = Regex(""""code"\s*:\s*"([A-Z_]+)"""").find(response()?.errorBody()?.string().orEmpty())?.groupValues?.get(1)
            val keyProblem = apiCode == "API_KEY_INVALID" || apiCode == "API_KEY_MISSING"
            val detail = listOfNotNull("HTTP ${code()}", apiCode).joinToString(", ")
            if (keyProblem) "USDA says the API key is invalid ($detail). Check it in Settings." else "USDA lookup failed ($detail)."
        }
        else -> "Online lookup failed. Saved foods still work offline."
    }
}
