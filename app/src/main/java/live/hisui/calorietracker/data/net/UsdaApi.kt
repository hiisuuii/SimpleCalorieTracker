package live.hisui.calorietracker.data.net

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface UsdaApi {
    @GET("foods/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("dataType") dataTypes: String,
        @Query("pageSize") pageSize: Int,
        @Query("api_key") apiKey: String,
    ): UsdaSearchResponse

    @GET("food/{fdcId}")
    suspend fun details(
        @Path("fdcId") fdcId: Long,
        @Query("api_key") apiKey: String,
    ): UsdaDetails
}

@Serializable
data class UsdaSearchResponse(val foods: List<UsdaFood> = emptyList())

@Serializable
data class UsdaFood(
    val fdcId: Long,
    val description: String,
    val dataType: String? = null,
    val brandOwner: String? = null,
    val brandName: String? = null,
    val gtinUpc: String? = null,
    val servingSize: Double? = null,
    val servingSizeUnit: String? = null,
    val householdServingFullText: String? = null,
    val foodNutrients: List<UsdaNutrient> = emptyList(),
)

@Serializable
data class UsdaNutrient(
    val nutrientId: Int? = null,
    val value: Double? = null,
)

@Serializable
data class UsdaDetails(val foodPortions: List<UsdaPortion> = emptyList())

@Serializable
data class UsdaPortion(
    val gramWeight: Double? = null,
    val amount: Double? = null,
    val modifier: String? = null,
    val portionDescription: String? = null,
    val measureUnit: UsdaMeasureUnit? = null,
)

@Serializable
data class UsdaMeasureUnit(val name: String? = null)
