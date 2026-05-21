package com.orderpackager.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class ScaleResponse(
    val weight: Float,
    val raw:    String,
    val unit:   String
)

interface ScaleApi {
    @GET("/")
    suspend fun getWeight(): ScaleResponse
}

object ScaleApiProvider {
    // Кэш по IP чтобы не создавать Retrofit на каждый запрос
    private val cache = mutableMapOf<String, ScaleApi>()

    fun getApi(ip: String): ScaleApi = cache.getOrPut(ip) {
        Retrofit.Builder()
            .baseUrl("http://$ip/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ScaleApi::class.java)
    }
}
