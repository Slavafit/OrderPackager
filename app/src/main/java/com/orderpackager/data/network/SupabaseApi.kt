package com.orderpackager.data.network

import com.google.gson.annotations.SerializedName
import com.orderpackager.BuildConfig
import com.orderpackager.data.db.entity.OrderPosition
import com.orderpackager.data.db.entity.PackingOrder
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class CloudPosition(
    @SerializedName("cyclic_item_id") val cyclicItemId: Long,
    @SerializedName("cyclic_item_name") val cyclicItemName: String,
    @SerializedName("has_clothes") val hasClothes: Boolean,
    @SerializedName("has_shoes") val hasShoes: Boolean,
    @SerializedName("has_cosmetics") val hasCosmetics: Boolean,
    @SerializedName("has_accessories") val hasAccessories: Boolean,
    @SerializedName("has_bags") val hasBags: Boolean,
    @SerializedName("has_other") val hasOther: Boolean,
    @SerializedName("other_text") val otherText: String,
    @SerializedName("weight_kg") val weightKg: Float
) {
    fun toEntity(orderId: Long) = OrderPosition(
        orderId = orderId,
        cyclicItemId = cyclicItemId,
        cyclicItemName = cyclicItemName,
        hasClothes = hasClothes,
        hasShoes = hasShoes,
        hasCosmetics = hasCosmetics,
        hasAccessories = hasAccessories,
        hasBags = hasBags,
        hasOther = hasOther,
        otherText = otherText,
        weightKg = weightKg
    )

    companion object {
        fun fromEntity(position: OrderPosition) = CloudPosition(
            cyclicItemId = position.cyclicItemId,
            cyclicItemName = position.cyclicItemName,
            hasClothes = position.hasClothes,
            hasShoes = position.hasShoes,
            hasCosmetics = position.hasCosmetics,
            hasAccessories = position.hasAccessories,
            hasBags = position.hasBags,
            hasOther = position.hasOther,
            otherText = position.otherText,
            weightKg = position.weightKg
        )
    }
}

data class CloudOrder(
    @SerializedName("box_number") val boxNumber: String,
    @SerializedName("batch_number") val batchNumber: String,
    @SerializedName("client_last_name") val clientLastName: String,
    @SerializedName("created_at_ms") val createdAtMs: Long,
    @SerializedName("box_length") val boxLength: Float,
    @SerializedName("box_width") val boxWidth: Float,
    @SerializedName("box_height") val boxHeight: Float,
    @SerializedName("is_completed") val isCompleted: Boolean,
    val positions: List<CloudPosition>
) {
    companion object {
        fun fromEntities(order: PackingOrder, positions: List<OrderPosition>) = CloudOrder(
            boxNumber = order.boxNumber,
            batchNumber = order.batchNumber,
            clientLastName = order.clientLastName,
            createdAtMs = order.createdAt,
            boxLength = order.boxLength,
            boxWidth = order.boxWidth,
            boxHeight = order.boxHeight,
            isCompleted = order.isCompleted,
            positions = positions.map(CloudPosition::fromEntity)
        )
    }
}

data class CloudUpsertRequest(@SerializedName("p_order") val order: CloudOrder)

interface SupabaseApi {
    @POST("rest/v1/rpc/order_packer_upsert")
    suspend fun upsert(@Body request: CloudUpsertRequest): Response<Unit>

    @POST("rest/v1/rpc/order_packer_pull")
    suspend fun pull(@Body request: Map<String, String> = emptyMap()): Response<List<CloudOrder>>
}

object SupabaseApiProvider {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    fun api(accessToken: String): SupabaseApi {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/') + "/"
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                        .header("Authorization", "Bearer $accessToken")
                        .header("Content-Type", "application/json")
                        .build()
                )
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SupabaseApi::class.java)
    }
}
