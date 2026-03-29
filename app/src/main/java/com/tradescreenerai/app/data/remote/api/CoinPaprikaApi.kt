package com.tradescreenerai.app.data.remote.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CoinPaprika public API – completely free, no API key required.
 * Used as a fallback when CoinGecko returns HTTP 429 (rate-limited).
 *
 * Base URL: https://api.coinpaprika.com/v1/
 */
interface CoinPaprikaApi {

    /**
     * Returns an array of ALL tickers sorted by rank (ascending).
     * We take only the first [limit] items client-side because the free tier
     * does not support a server-side limit parameter.
     *
     * Sample item:
     * {
     *   "id":"btc-bitcoin","name":"Bitcoin","symbol":"BTC","rank":1,
     *   "circulating_supply":17909275,"total_supply":17909275,"max_supply":21000000,
     *   "quotes":{"USD":{
     *     "price":9259.9,"volume_24h":7558860113.98,
     *     "market_cap":165519888125,"percent_change_24h":1.09,
     *     "ath_price":20089
     *   }}
     * }
     */
    @GET("tickers")
    suspend fun getTickers(
        @Query("quotes") quotes: String = "USD"
    ): JsonArray

    /** Single coin ticker – used as fallback for getCoinDetail(). */
    @GET("tickers/{coinId}")
    suspend fun getTicker(
        @Path("coinId") coinId: String,
        @Query("quotes") quotes: String = "USD"
    ): JsonObject

    /** Coin search – useful as a fallback for searchCrypto(). */
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): JsonObject

    /**
     * Daily OHLCV historical data (free tier, no API key needed).
     * Used as chart fallback when CoinGecko is rate-limited.
     * Response: [{"time_open":"2022-01-01T00:00:00Z","open":46311,"high":47954,
     *             "low":45678,"close":47345,"volume":24820685831,...}, ...]
     */
    @GET("coins/{coinId}/ohlcv/historical")
    suspend fun getOhlcvHistorical(
        @Path("coinId") coinId: String,
        @Query("start") start: String,
        @Query("end") end: String
    ): JsonArray
}
