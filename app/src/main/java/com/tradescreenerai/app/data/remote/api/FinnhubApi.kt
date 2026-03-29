package com.tradescreenerai.app.data.remote.api

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface FinnhubApi {

    @GET("quote")
    suspend fun getQuote(
        @Query("symbol") symbol: String,
        @Query("token") token: String
    ): JsonObject

    @GET("stock/candle")
    suspend fun getCandles(
        @Query("symbol") symbol: String,
        @Query("resolution") resolution: String, // 1, 5, 15, 30, 60, D, W, M
        @Query("from") from: Long,
        @Query("to") to: Long,
        @Query("token") token: String
    ): JsonObject

    @GET("stock/social-sentiment")
    suspend fun getSocialSentiment(
        @Query("symbol") symbol: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("token") token: String
    ): JsonObject

    @GET("news")
    suspend fun getMarketNews(
        @Query("category") category: String = "general",
        @Query("token") token: String
    ): com.google.gson.JsonArray

    @GET("company-news")
    suspend fun getCompanyNews(
        @Query("symbol") symbol: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("token") token: String
    ): com.google.gson.JsonArray
}

