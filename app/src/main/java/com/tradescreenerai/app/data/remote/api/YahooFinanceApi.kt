package com.tradescreenerai.app.data.remote.api

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Yahoo Finance API  –  https://query2.finance.yahoo.com
 *
 * No API key required. Free, real-time (15-min delayed on some exchanges).
 * The OkHttp client injected for this interface must include a browser
 * User-Agent header to avoid 401/429 responses from Yahoo's CDN.
 *
 * Screener IDs used for penny-stock discovery:
 *   aggressive_small_caps  – micro/small-cap stocks with high momentum
 *   small_cap_gainers      – today's top small-cap gainers
 *   day_gainers            – all exchanges, top % gainers (includes cheap stocks)
 *   day_losers             – all exchanges, top % losers
 *   most_actives           – highest volume (filter client-side by price)
 */
interface YahooFinanceApi {

    /**
     * Predefined stock screener.
     * GET /v1/finance/screener/predefined/saved
     *
     * @param scrId  one of: aggressive_small_caps | small_cap_gainers | day_gainers |
     *               day_losers | most_actives | undervalued_growth_stocks
     * @param count  max results per page (up to 250)
     */
    @GET("v1/finance/screener/predefined/saved")
    suspend fun getScreener(
        @Query("formatted") formatted: Boolean = false,
        @Query("lang")      lang:      String  = "en-US",
        @Query("region")    region:    String  = "US",
        @Query("scrIds")    scrId:     String,
        @Query("start")     start:     Int     = 0,
        @Query("count")     count:     Int     = 100
    ): JsonObject

    /**
     * Real-time quote for one or more symbols (supports up to 200+ at once).
     * GET /v7/finance/quote?symbols=AAPL,TSLA,MSFT
     */
    @GET("v7/finance/quote")
    suspend fun getQuotes(
        @Query("symbols")  symbols: String,
        @Query("lang")     lang:    String = "en-US",
        @Query("region")   region:  String = "US",
        @Query("fields")   fields:  String =
            "symbol,shortName,regularMarketPrice,regularMarketChange," +
            "regularMarketChangePercent,regularMarketVolume,regularMarketOpen," +
            "regularMarketDayHigh,regularMarketDayLow,regularMarketPreviousClose," +
            "marketCap,fiftyTwoWeekHigh,fiftyTwoWeekLow"
    ): JsonObject

    /**
     * Historical OHLCV chart data for a single symbol.
     * GET /v8/finance/chart/{symbol}?interval=1d&range=1y
     *
     * @param symbol    e.g. "AAPL"
     * @param interval  "1d" | "1wk" | "1mo" | "5m" | "15m" | "1h"
     * @param range     "1d" | "5d" | "1mo" | "3mo" | "6mo" | "1y" | "2y" | "5y" | "max"
     */
    @GET("v8/finance/chart/{symbol}")
    suspend fun getChart(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String = "1d",
        @Query("range")    range:    String = "1y",
        @Query("includePrePost") includePrePost: Boolean = false
    ): JsonObject
}

