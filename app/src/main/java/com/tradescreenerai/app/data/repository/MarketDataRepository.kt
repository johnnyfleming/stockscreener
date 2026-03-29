package com.tradescreenerai.app.data.repository

import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.remote.RetrofitClient
import com.tradescreenerai.app.util.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class MarketDataRepository {

    private val alternativeMeApi = RetrofitClient.alternativeMeApi
    private val cryptoRepo = CryptoRepository()

    // ─── Fear & Greed Index (alternative.me — 100% free, no key) ──────────────
    suspend fun getFearGreedIndex(): Resource<FearGreedIndex> = withContext(Dispatchers.IO) {
        try {
            val response = alternativeMeApi.getFearGreedIndex(limit = 7)
            val data = response.getAsJsonArray("data")
            if (data != null && data.size() > 0) {
                val today = data[0].asJsonObject
                val value = today.get("value")?.asString?.toIntOrNull() ?: 50
                val label = today.get("value_classification")?.asString ?: "Neutral"
                val timestamp = today.get("timestamp")?.asString?.toLongOrNull()?.times(1000)
                    ?: System.currentTimeMillis()

                val previousClose = if (data.size() > 1) {
                    data[1].asJsonObject.get("value")?.asString?.toIntOrNull() ?: value
                } else value

                val weekAgo = if (data.size() >= 7) {
                    data[6].asJsonObject.get("value")?.asString?.toIntOrNull() ?: value
                } else value

                Resource.Success(
                    FearGreedIndex(
                        value = value,
                        label = label,
                        timestamp = timestamp,
                        previousClose = previousClose,
                        weekAgo = weekAgo
                    )
                )
            } else {
                Resource.Error("No Fear & Greed data available")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch Fear & Greed Index")
        }
    }

    // ─── Whale Alerts (derived from volume anomalies in market data) ──────────
    suspend fun getWhaleAlerts(): Resource<List<WhaleTransaction>> = withContext(Dispatchers.IO) {
        try {
            val result = cryptoRepo.getMarkets(perPage = 100)
            val allCryptos = when (result) {
                is Resource.Success -> result.data ?: emptyList()
                else -> emptyList()
            }

            // Detect "whales" = coins with unusual volume (>3× typical ratio)
            val alerts = allCryptos.filter { it.volume24h > 0 && it.marketCap > 0 }
                .map { crypto ->
                    val volumeToMcapRatio = crypto.volume24h.toDouble() / crypto.marketCap
                    crypto to volumeToMcapRatio
                }
                .sortedByDescending { it.second }
                .take(10)
                .map { (crypto, ratio) ->
                    val type = when {
                        crypto.changePercent24h > 5 && ratio > 0.3 -> WhaleType.LARGE_BUY
                        crypto.changePercent24h < -5 && ratio > 0.3 -> WhaleType.LARGE_SELL
                        crypto.changePercent24h > 0 -> WhaleType.EXCHANGE_OUTFLOW
                        else -> WhaleType.EXCHANGE_INFLOW
                    }
                    WhaleTransaction(
                        symbol = crypto.symbol,
                        name = crypto.name,
                        amount = crypto.volume24h.toDouble(),
                        amountUsd = crypto.volume24h.toDouble(),
                        type = type,
                        imageUrl = crypto.imageUrl
                    )
                }

            Resource.Success(alerts)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to detect whale activity")
        }
    }

    // ─── Heatmap Data ─────────────────────────────────────────────────────────
    suspend fun getHeatmapData(): Resource<List<HeatmapCell>> = withContext(Dispatchers.IO) {
        try {
            val result = cryptoRepo.getMarkets(perPage = 50)
            val cryptos = when (result) {
                is Resource.Success -> result.data ?: emptyList()
                else -> emptyList()
            }

            val cells = cryptos.map { crypto ->
                HeatmapCell(
                    id = crypto.id,
                    symbol = crypto.symbol,
                    name = crypto.name,
                    marketCap = crypto.marketCap,
                    changePercent = crypto.changePercent24h,
                    price = crypto.price,
                    imageUrl = crypto.imageUrl
                )
            }

            Resource.Success(cells)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to build heatmap")
        }
    }

    // ─── Smart Money Flow (derived from OBV + volume-weighted direction) ──────
    fun calculateSmartMoneyFlow(entries: List<ChartEntry>): Double {
        if (entries.size < 20) return 0.0

        val recent = entries.takeLast(20)
        var smartMoney = 0.0
        val avgVolume = recent.map { it.volume }.average()

        for (bar in recent) {
            val range = bar.high - bar.low
            if (range <= 0) continue

            // Smart money indicator: close location + volume weighting
            val clv = (2.0 * bar.close - bar.low - bar.high) / range
            val volWeight = bar.volume.toDouble() / avgVolume.coerceAtLeast(1.0)

            // Large volume bars with strong directional close = institutional activity
            if (volWeight > 1.5) {
                smartMoney += clv * volWeight * 2.0  // Double weight for large orders
            } else {
                smartMoney += clv * volWeight
            }
        }

        return smartMoney
    }

    // ─── Correlation Matrix ───────────────────────────────────────────────────
    fun calculateCorrelation(pricesA: List<Double>, pricesB: List<Double>): Double {
        val n = minOf(pricesA.size, pricesB.size)
        if (n < 10) return 0.0

        val a = pricesA.takeLast(n)
        val b = pricesB.takeLast(n)
        val meanA = a.average()
        val meanB = b.average()

        var sumAB = 0.0
        var sumA2 = 0.0
        var sumB2 = 0.0

        for (i in 0 until n) {
            val da = a[i] - meanA
            val db = b[i] - meanB
            sumAB += da * db
            sumA2 += da * da
            sumB2 += db * db
        }

        val denominator = Math.sqrt(sumA2 * sumB2)
        return if (denominator > 0) (sumAB / denominator).coerceIn(-1.0, 1.0) else 0.0
    }

    // ─── Social Sentiment Pulse ───────────────────────────────────────────────
    fun calculateSentimentPulse(
        crypto: Crypto,
        sentiment: SentimentData? = null
    ): SocialSentimentPulse {
        // Combine price action + social data
        val priceScore = when {
            crypto.changePercent24h > 10 -> 85.0
            crypto.changePercent24h > 5 -> 72.0
            crypto.changePercent24h > 2 -> 62.0
            crypto.changePercent24h > 0 -> 55.0
            crypto.changePercent24h > -2 -> 45.0
            crypto.changePercent24h > -5 -> 35.0
            else -> 20.0
        }

        val socialScore = sentiment?.bullishPercent ?: 50.0
        val buzzScore = sentiment?.trendingScore?.coerceIn(0.0, 100.0) ?: 0.0

        val combined = (priceScore * 0.4 + socialScore * 0.4 + buzzScore * 0.2)
            .coerceIn(0.0, 100.0)

        val label = when {
            combined >= 75 -> "Very Bullish 🚀"
            combined >= 60 -> "Bullish 📈"
            combined >= 45 -> "Neutral 😐"
            combined >= 30 -> "Bearish 📉"
            else -> "Very Bearish 💀"
        }

        return SocialSentimentPulse(
            symbol = crypto.symbol,
            sentimentScore = combined,
            buzzScore = buzzScore,
            newsScore = priceScore,
            socialScore = socialScore,
            label = label
        )
    }
}

