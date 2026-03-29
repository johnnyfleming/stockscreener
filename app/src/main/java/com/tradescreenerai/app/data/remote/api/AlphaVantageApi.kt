package com.tradescreenerai.app.data.remote.api

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface AlphaVantageApi {

    @GET("query")
    suspend fun getQuote(
        @Query("function") function: String = "GLOBAL_QUOTE",
        @Query("symbol") symbol: String,
        @Query("apikey") apiKey: String
    ): JsonObject

    @GET("query")
    suspend fun getTimeSeries(
        @Query("function") function: String = "TIME_SERIES_DAILY",
        @Query("symbol") symbol: String,
        @Query("outputsize") outputSize: String = "compact",
        @Query("apikey") apiKey: String
    ): JsonObject

    @GET("query")
    suspend fun getSearchResults(
        @Query("function") function: String = "SYMBOL_SEARCH",
        @Query("keywords") keywords: String,
        @Query("apikey") apiKey: String
    ): JsonObject

    @GET("query")
    suspend fun getCompanyOverview(
        @Query("function") function: String = "OVERVIEW",
        @Query("symbol") symbol: String,
        @Query("apikey") apiKey: String
    ): JsonObject

    @GET("query")
    suspend fun getSectorPerformance(
        @Query("function") function: String = "SECTOR",
        @Query("apikey") apiKey: String
    ): JsonObject

    @GET("query")
    suspend fun getTopGainersLosers(
        @Query("function") function: String = "TOP_GAINERS_LOSERS",
        @Query("apikey") apiKey: String
    ): JsonObject

    /** Commodity data (GOLD, SILVER, WTI, BRENT, NATURAL_GAS, COPPER, WHEAT, CORN, COFFEE, SUGAR) */
    @GET("query")
    suspend fun getCommodityData(
        @Query("function") function: String,         // e.g. "GOLD", "WTI", etc.
        @Query("interval") interval: String = "daily", // daily, weekly, monthly
        @Query("apikey") apiKey: String
    ): JsonObject

    /** Technical indicator (RSI, MACD, EMA, SMA, ADX, BBANDS, STOCH, etc.) */
    @GET("query")
    suspend fun getTechnicalIndicator(
        @Query("function") function: String,          // e.g. "RSI", "MACD", "EMA"
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "daily",
        @Query("time_period") timePeriod: Int = 14,
        @Query("series_type") seriesType: String = "close",
        @Query("apikey") apiKey: String
    ): JsonObject

    /** Intraday time series for stocks */
    @GET("query")
    suspend fun getIntradayTimeSeries(
        @Query("function") function: String = "TIME_SERIES_INTRADAY",
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "5min",
        @Query("outputsize") outputSize: String = "compact",
        @Query("apikey") apiKey: String
    ): JsonObject

    /** Market news & sentiment – free, works on Android (no localhost restriction) */
    @GET("query")
    suspend fun getNewsSentiment(
        @Query("function") function: String = "NEWS_SENTIMENT",
        @Query("topics") topics: String = "financial_markets",
        @Query("limit") limit: Int = 50,
        @Query("sort") sort: String = "LATEST",
        @Query("apikey") apiKey: String
    ): JsonObject

    /** Company-specific news & sentiment */
    @GET("query")
    suspend fun getCompanyNewsSentiment(
        @Query("function") function: String = "NEWS_SENTIMENT",
        @Query("tickers") tickers: String,
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String = "LATEST",
        @Query("apikey") apiKey: String
    ): JsonObject
}

