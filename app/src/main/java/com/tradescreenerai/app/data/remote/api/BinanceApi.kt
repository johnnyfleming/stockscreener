package com.tradescreenerai.app.data.remote.api

import com.google.gson.JsonArray
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Binance public REST API – completely free, no API key required.
 * Used as the final fallback for crypto chart data when CoinGecko,
 * CoinPaprika, and CoinCap all fail or are rate-limited.
 *
 * Base URL: https://api.binance.com/api/v3/
 *
 * Kline response: [[openTime, open, high, low, close, volume,
 *                   closeTime, quoteVolume, trades, ...], ...]
 */
interface BinanceApi {

    /**
     * Kline/Candlestick chart data.
     *
     * @param symbol   Trading pair, e.g. "BTCUSDT", "ETHUSDT"
     * @param interval Candle interval: 1m 3m 5m 15m 30m 1h 2h 4h 6h 8h 12h
     *                 1d 3d 1w 1M
     * @param startTime  Start time in milliseconds (optional)
     * @param endTime    End time in milliseconds (optional)
     * @param limit      Max candles to return (default 500, max 1000)
     */
    @GET("klines")
    suspend fun getKlines(
        @Query("symbol")    symbol: String,
        @Query("interval")  interval: String,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime")   endTime: Long? = null,
        @Query("limit")     limit: Int = 500
    ): JsonArray
}

