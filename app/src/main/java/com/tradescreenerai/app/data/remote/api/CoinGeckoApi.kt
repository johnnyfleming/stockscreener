package com.tradescreenerai.app.data.remote.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CoinGeckoApi {

    @GET("coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = true,
        @Query("price_change_percentage") priceChangePercentage: String = "24h,7d"
    ): JsonArray

    @GET("coins/{id}")
    suspend fun getCoinDetail(
        @Path("id") id: String,
        @Query("localization") localization: Boolean = false,
        @Query("tickers") tickers: Boolean = false,
        @Query("market_data") marketData: Boolean = true,
        @Query("community_data") communityData: Boolean = true,
        @Query("sparkline") sparkline: Boolean = true
    ): JsonObject

    @GET("coins/{id}/market_chart")
    suspend fun getMarketChart(
        @Path("id") id: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: String = "30"
    ): JsonObject

    /** Returns [[timestamp, open, high, low, close], ...] */
    @GET("coins/{id}/ohlc")
    suspend fun getCoinOhlc(
        @Path("id") id: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: String = "30"
    ): JsonArray

    @GET("search/trending")
    suspend fun getTrending(): JsonObject

    @GET("search")
    suspend fun search(
        @Query("query") query: String
    ): JsonObject

    @GET("coins/markets")
    suspend fun getTopGainers(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("order") order: String = "percent_change_24h_desc",
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = true
    ): JsonArray

    @GET("coins/markets")
    suspend fun getTopLosers(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("order") order: String = "percent_change_24h_asc",
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = true
    ): JsonArray

    /** Lightweight price check — {"bitcoin":{"usd":43000.0}, ...} */
    @GET("simple/price")
    suspend fun getSimplePrice(
        @Query("ids") ids: String,
        @Query("vs_currencies") vsCurrencies: String = "usd"
    ): JsonObject
}

