package com.tradescreenerai.app.data.remote.api

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Polygon.io REST API  – https://polygon.io
 * Free tier: unlimited calls, 5 req/min, delayed data (15 min).
 * Paid tiers: real-time data.
 *
 * Primary use-case here: penny-stock screener (US stocks priced < $5).
 */
interface PolygonApi {

    /**
     * Reference ticker list – returns all active US stock tickers.
     * Use to build a curated list of penny-stock candidates.
     *
     * GET /v3/reference/tickers
     *  market=stocks  – equities only (excludes crypto/forex)
     *  type=CS        – Common Stock only
     *  active=true
     *  limit=1000     – max per page
     */
    @GET("v3/reference/tickers")
    suspend fun getTickers(
        @Query("market") market: String = "stocks",
        @Query("type") type: String = "CS",
        @Query("active") active: Boolean = true,
        @Query("limit") limit: Int = 1000,
        @Query("sort") sort: String = "ticker",
        @Query("order") order: String = "asc",
        @Query("cursor") cursor: String? = null,
        @Query("apiKey") apiKey: String
    ): JsonObject

    /**
     * Snapshot – previous day's OHLCV for up to 250 tickers at once.
     *
     * GET /v2/snapshot/locale/us/markets/stocks/tickers
     *  tickers = comma-separated list (e.g. "AAPL,TSLA,…")
     */
    @GET("v2/snapshot/locale/us/markets/stocks/tickers")
    suspend fun getSnapshots(
        @Query("tickers") tickers: String,
        @Query("apiKey") apiKey: String
    ): JsonObject

    /**
     * Grouped daily bar – ALL tickers for a given date.
     * Useful to bulk-load a day's price + volume to find penny stocks.
     *
     * GET /v2/aggs/grouped/locale/us/market/stocks/{date}
     *  adjusted=true
     */
    @GET("v2/aggs/grouped/locale/us/market/stocks/{date}")
    suspend fun getGroupedDailyBars(
        @retrofit2.http.Path("date") date: String,       // "YYYY-MM-DD"
        @Query("adjusted") adjusted: Boolean = true,
        @Query("apiKey") apiKey: String
    ): JsonObject

    /**
     * Previous-day bar for a single ticker.
     *
     * GET /v2/aggs/ticker/{ticker}/prev
     */
    @GET("v2/aggs/ticker/{ticker}/prev")
    suspend fun getPreviousDayBar(
        @retrofit2.http.Path("ticker") ticker: String,
        @Query("adjusted") adjusted: Boolean = true,
        @Query("apiKey") apiKey: String
    ): JsonObject

    /**
     * Aggregate bars (candles) for a ticker.
     *
     * GET /v2/aggs/ticker/{ticker}/range/{multiplier}/{timespan}/{from}/{to}
     *  e.g. /v2/aggs/ticker/AAPL/range/1/day/2024-01-01/2024-12-31
     */
    @GET("v2/aggs/ticker/{ticker}/range/{multiplier}/{timespan}/{from}/{to}")
    suspend fun getAggBars(
        @retrofit2.http.Path("ticker") ticker: String,
        @retrofit2.http.Path("multiplier") multiplier: Int,
        @retrofit2.http.Path("timespan") timespan: String,  // minute/hour/day/week/month
        @retrofit2.http.Path("from") from: String,          // YYYY-MM-DD
        @retrofit2.http.Path("to") to: String,              // YYYY-MM-DD
        @Query("adjusted") adjusted: Boolean = true,
        @Query("sort") sort: String = "asc",
        @Query("limit") limit: Int = 120,
        @Query("apiKey") apiKey: String
    ): JsonObject

    /**
     * Ticker details – company name, exchange, description, etc.
     *
     * GET /v3/reference/tickers/{ticker}
     */
    @GET("v3/reference/tickers/{ticker}")
    suspend fun getTickerDetails(
        @retrofit2.http.Path("ticker") ticker: String,
        @Query("apiKey") apiKey: String
    ): JsonObject

    /**
     * Top movers snapshot (gainers or losers).
     *
     * GET /v2/snapshot/locale/us/markets/stocks/{direction}
     *  direction = "gainers" | "losers"
     */
    @GET("v2/snapshot/locale/us/markets/stocks/{direction}")
    suspend fun getStockMovers(
        @retrofit2.http.Path("direction") direction: String,
        @Query("apiKey") apiKey: String
    ): JsonObject
}

