package live.hisui.calorietracker.data

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import live.hisui.calorietracker.data.db.Food
import live.hisui.calorietracker.data.net.FoodCandidate
import live.hisui.calorietracker.data.net.OffApi
import live.hisui.calorietracker.data.net.toCandidate
import live.hisui.calorietracker.domain.BarcodeNormalizer
import retrofit2.HttpException

sealed interface BarcodeResult {
    data class Found(val food: Food) : BarcodeResult
    data class NotFound(val barcode: String) : BarcodeResult
    data class Failed(val message: String) : BarcodeResult
    data object Invalid : BarcodeResult
}

@Singleton
class BarcodeRepository @Inject constructor(
    private val foods: FoodRepository,
    private val off: OffApi,
) {
    suspend fun lookup(raw: String): BarcodeResult {
        val code = BarcodeNormalizer.parseScan(raw) ?: return BarcodeResult.Invalid
        foods.findByBarcode(code)?.let { return BarcodeResult.Found(it) }

        var failure: Throwable? = null
        val offCandidate = try {
            off.product(code).product?.toCandidate(code)
        } catch (e: HttpException) {
            if (e.code() != HTTP_NOT_FOUND) failure = e
            null
        } catch (e: IOException) {
            failure = e
            null
        } catch (e: SerializationException) {
            failure = e
            null
        }
        offCandidate?.let { return BarcodeResult.Found(foods.saveOffCandidate(it)) }

        val branded = foods.searchUsda(BarcodeNormalizer.upcA(code), BRANDED_ONLY, PAGE_SIZE)
        val match: FoodCandidate? = branded.getOrNull()?.firstOrNull { it.food.barcode == code }
        if (match != null) return BarcodeResult.Found(foods.saveUsdaCandidate(match))

        failure = failure ?: branded.exceptionOrNull()
        return if (failure != null) BarcodeResult.Failed(failure.toSearchMessage()) else BarcodeResult.NotFound(code)
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val BRANDED_ONLY = "Branded"
        const val PAGE_SIZE = 10
    }
}
