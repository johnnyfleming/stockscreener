package com.tradescreenerai.app.domain

import com.tradescreenerai.app.data.model.ChartEntry
import com.tradescreenerai.app.data.model.RiskScoreData
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Per-asset risk calculator using ATR-based volatility, max drawdown,
 * liquidity scoring, and price deviation metrics.
 */
object RiskCalculator {

    fun calculate(entries: List<ChartEntry>, currentPrice: Double, volume24h: Long): RiskScoreData {
        if (entries.size < 14 || currentPrice <= 0) return RiskScoreData()

        val closes = entries.map { it.close }

        // 1. Volatility Score (0–100): ATR as % of price
        val atr = TechnicalAnalysis.calculateATR(entries)
        val atrPct = (atr / currentPrice * 100).coerceIn(0.0, 50.0)
        val volatilityScore = (atrPct / 50.0 * 100).coerceIn(0.0, 100.0)

        // 2. Max Drawdown Score (0–100)
        var peak = closes[0]
        var maxDrawdown = 0.0
        for (p in closes) {
            if (p > peak) peak = p
            val drawdown = (peak - p) / peak * 100
            if (drawdown > maxDrawdown) maxDrawdown = drawdown
        }
        val drawdownScore = (maxDrawdown / 80.0 * 100).coerceIn(0.0, 100.0)

        // 3. Liquidity Score (inverse — low volume = high risk)
        val liquidityScore = when {
            volume24h > 1_000_000_000 -> 10.0
            volume24h > 100_000_000 -> 25.0
            volume24h > 10_000_000 -> 45.0
            volume24h > 1_000_000 -> 65.0
            volume24h > 100_000 -> 80.0
            else -> 95.0
        }

        // 4. Price deviation from mean (tail risk)
        val mean = closes.average()
        val stdDev = sqrt(closes.map { (it - mean) * (it - mean) }.average())
        val zScore = abs(currentPrice - mean) / stdDev.coerceAtLeast(0.001)
        val correlationScore = (zScore / 3.0 * 100).coerceIn(0.0, 100.0)

        // Weighted average
        val score = (
            volatilityScore * 0.35 +
            drawdownScore * 0.25 +
            liquidityScore * 0.20 +
            correlationScore * 0.20
        ).toInt().coerceIn(0, 100)

        val label = when {
            score >= 80 -> "Extreme"
            score >= 60 -> "High"
            score >= 40 -> "Medium"
            score >= 20 -> "Low"
            else -> "Very Low"
        }

        return RiskScoreData(
            score = score,
            label = label,
            volatilityScore = volatilityScore,
            drawdownScore = drawdownScore,
            correlationScore = correlationScore,
            liquidityScore = liquidityScore
        )
    }
}

