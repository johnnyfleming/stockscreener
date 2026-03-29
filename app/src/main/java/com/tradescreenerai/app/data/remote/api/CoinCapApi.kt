package com.tradescreenerai.app.data.remote.api

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CoinCap.io public API v2 – free, no API key required.
 * Used as a second fallback for historical chart data when both
 * CoinGecko (rate-limited) and CoinPaprika (OHLCV 402 on free plan) fail.
 *
 * Base URL: https://api.coincap.io/v2/
 */
interface CoinCapApi {

    /**
     * Price history for a given asset.
     *
     * @param id       CoinCap asset id (e.g. "bitcoin", "ethereum")
     * @param interval Data interval: m1, m5, m15, m30, h1, h2, h6, h12, d1
     * @param start    Start time in Unix milliseconds
     * @param end      End time in Unix milliseconds
     *
     * Response example:
     * {
     *   "data": [
     *     { "priceUsd": "65877.23", "time": 1711555200000, "date": "2026-03-27T12:00:00.000Z" },
     *     ...
     *   ]
     * }
     */
    @GET("assets/{id}/history")
    suspend fun getAssetHistory(
        @Path("id") id: String,
        @Query("interval") interval: String,
        @Query("start") start: Long,
        @Query("end") end: Long
    ): JsonObject
}

