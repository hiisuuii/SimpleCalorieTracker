package live.hisui.calorietracker.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OffApi {
    @GET("product/{barcode}")
    suspend fun product(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = FIELDS,
    ): OffResponse

    companion object {
        const val FIELDS = "code,product_name,brands,serving_size,serving_quantity,nutriments"
    }
}

@Serializable
data class OffResponse(val status: Int = 0, val product: OffProduct? = null)

// Open Food Facts sends numbers as either JSON numbers or strings depending on the product, hence JsonElement.
@Serializable
data class OffProduct(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    @SerialName("serving_quantity") val servingQuantity: JsonElement? = null,
    val nutriments: Map<String, JsonElement> = emptyMap(),
)
