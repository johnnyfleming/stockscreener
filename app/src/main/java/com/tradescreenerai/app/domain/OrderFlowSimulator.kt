package com.tradescreenerai.app.domain

import com.tradescreenerai.app.data.model.ChartEntry
import com.tradescreenerai.app.data.model.OrderFlowData
import kotlin.math.abs

/**
 * Order Flow Imbalance Simulator — analyses buy vs sell pressure
 * by examining volume delta, price action relative to OHLC, and
 * large-order detection via volume outlier analysis.
 *
 * This simulates what institutional traders see on Level 2 / DOM:
 *  • Buy pressure: bars closing near high with above-average volume
 *  • Sell pressure: bars closing near low with above-average volume
 *  • Large orders: volume spikes > 2× the 20-bar average
 *  • Net flow: cumulative buy pressure - sell pressure
 *
 * Only 1% of retail trading apps expose this kind of analysis.
 */
object OrderFlowSimulator {

    /**
     * Analyse the last N bars to determine order flow characteristics.
     * @param entries OHLC+Volume bars (at least 20 recommended)
     * @param lookback how many recent bars to weight (default 10)
     */
    fun analyze(entries: List<ChartEntry>, lookback: Int = 10): OrderFlowData {
        if (entries.size < 5) return OrderFlowData(symbol = "")

        val recent = entries.takeLast(lookback.coerceAtMost(entries.size))
        val avgVolume = entries.takeLast(20.coerceAtMost(entries.size)).map { it.volume }.average()

        var buyVol = 0.0
        var sellVol = 0.0
        var totalVol = 0.0
        var largeOrderVol = 0.0

        for (bar in recent) {
            val range = bar.high - bar.low
            if (range <= 0) continue

            // Close Location Value: where did price close within the bar?
            // 1.0 = closed at high (buyers won), 0.0 = closed at low (sellers won)
            val clv = (bar.close - bar.low) / range

            val vol = bar.volume.toDouble()
            totalVol += vol

            // Volume attributed to buyers vs sellers based on CLV
            buyVol += vol * clv
            sellVol += vol * (1.0 - clv)

            // Detect large orders (>2× average volume)
            if (vol > avgVolume * 2.0) {
                largeOrderVol += vol
            }
        }

        if (totalVol <= 0) return OrderFlowData(symbol = "")

        val buyPressure = (buyVol / totalVol * 100).coerceIn(0.0, 100.0)
        val sellPressure = (sellVol / totalVol * 100).coerceIn(0.0, 100.0)
        val netFlow = buyVol - sellVol
        val largeOrderRatio = (largeOrderVol / totalVol * 100).coerceIn(0.0, 100.0)

        // Delta volume: sum of (close > open ? +vol : -vol) for each bar
        var volumeDelta = 0L
        for (bar in recent) {
            volumeDelta += if (bar.close >= bar.open) bar.volume else -bar.volume
        }

        val imbalance = buyPressure - sellPressure
        val label = when {
            imbalance > 25 -> "🟢 Strong Buy Pressure"
            imbalance > 10 -> "🟢 Buy Dominant"
            imbalance > 3  -> "🟡 Slight Buy Lean"
            imbalance < -25 -> "🔴 Strong Sell Pressure"
            imbalance < -10 -> "🔴 Sell Dominant"
            imbalance < -3  -> "🟡 Slight Sell Lean"
            else -> "⚖️ Balanced"
        }

        return OrderFlowData(
            symbol = "",
            buyPressure = buyPressure,
            sellPressure = sellPressure,
            netFlow = netFlow,
            volumeDelta = volumeDelta,
            largeOrderRatio = largeOrderRatio,
            imbalanceLabel = label
        )
    }

    /**
     * Generate a time series of order flow snapshots for animated display.
     * Returns one OrderFlowData per bar (rolling window analysis).
     */
    fun analyzeTimeSeries(entries: List<ChartEntry>, windowSize: Int = 5): List<OrderFlowData> {
        if (entries.size < windowSize) return emptyList()
        return (windowSize..entries.size).map { end ->
            analyze(entries.subList(end - windowSize, end), windowSize)
        }
    }
}

