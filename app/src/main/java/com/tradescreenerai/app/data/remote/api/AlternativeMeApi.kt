package com.tradescreenerai.app.data.remote.api

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Alternative.me API — completely free, no API key required.
 * Provides the Crypto Fear & Greed Index.
 * Base URL: https://api.alternative.me/
 */
interface AlternativeMeApi {

    /**
     * Returns Fear & Greed Index data.
     * Response: { "data": [{ "value": "25", "value_classification": "Extreme Fear", "timestamp": "..." }] }
     */
    @GET("fng/")
    suspend fun getFearGreedIndex(
        @Query("limit") limit: Int = 7,
        @Query("format") format: String = "json"
    ): JsonObject
}

