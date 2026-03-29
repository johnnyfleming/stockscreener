package com.tradescreenerai.app.domain

import com.tradescreenerai.app.data.model.ChartEntry
import com.tradescreenerai.app.data.model.SmartSignalScore
import com.tradescreenerai.app.data.model.TechnicalData

/**
 * Weighted scoring system (0–100) that combines multiple indicator groups:
 *  Trend 35% | Momentum 25% | Volume 15% | Volatility 15% | Trend Strength 10%
 */
object SmartSignalEngine {

    fun calculate(entries: List<ChartEntry>, td: TechnicalData): SmartSignalScore {
        val currentPrice = entries.lastOrNull()?.close ?: return SmartSignalScore()
        val insights = mutableListOf<String>()

        // ── Trend (max 35 pts) ─────────────────────────────────────────────────
        var trendScore = 0.0
        if (td.ema50 > 0 && currentPrice > td.ema50) {
            trendScore += 12.0
            insights.add("Price is above 50 EMA — short-term uptrend in play.")
        }
        if (td.ema200 > 0 && currentPrice > td.ema200) {
            trendScore += 11.0
            insights.add("Price is above 200 EMA — long-term bullish structure.")
        }
        if (td.ema50 > 0 && td.ema200 > 0) {
            if (td.ema50 > td.ema200) {
                trendScore += 12.0
                insights.add("Golden Cross: 50 EMA is above 200 EMA — bullish alignment.")
            }
            // Partial credit even in death-cross for a consistent baseline
        }
        val trendContrib = trendScore.coerceIn(0.0, 35.0)

        // ── Momentum (max 25 pts) ──────────────────────────────────────────────
        var momentumScore = 0.0
        if (td.macd > td.macdSignal) {
            momentumScore += if (td.macd > 0) 15.0 else 8.0
            insights.add("MACD is above signal line — bullish momentum building.")
        }
        when {
            td.rsi > 50 && td.rsi < 70 -> {
                momentumScore += 10.0
                insights.add("RSI ${String.format("%.1f", td.rsi)} — momentum is positive.")
            }
            td.rsi >= 70 -> {
                momentumScore += 5.0
                insights.add("RSI ${String.format("%.1f", td.rsi)} — overbought, watch for reversal.")
            }
            td.rsi < 30 -> {
                momentumScore += 3.0
                insights.add("RSI ${String.format("%.1f", td.rsi)} — oversold, potential bounce.")
            }
            else -> momentumScore += 3.0 // below 50 but not extreme
        }
        val momentumContrib = momentumScore.coerceIn(0.0, 25.0)

        // ── Volume (max 15 pts) ────────────────────────────────────────────────
        val lastVolume = entries.lastOrNull()?.volume ?: 0L
        val volumeContrib: Double
        if (td.volumeAvg20 > 0 && lastVolume > 0) {
            val ratio = lastVolume.toDouble() / td.volumeAvg20
            volumeContrib = when {
                ratio >= 2.0 -> { insights.add("Volume is ${String.format("%.1f", ratio)}× above average — strong conviction."); 15.0 }
                ratio >= 1.5 -> { insights.add("Volume is ${String.format("%.1f", ratio)}× above average — confirms the move."); 11.0 }
                ratio >= 1.0 -> 7.0
                else -> 3.0
            }
        } else {
            volumeContrib = 7.5 // neutral if no volume data
        }

        // ── Volatility (max 15 pts) ────────────────────────────────────────────
        var volatilityScore = 0.0
        if (td.bollingerLower > 0 && td.bollingerUpper > 0) {
            val bbRange = td.bollingerUpper - td.bollingerLower
            val pos = if (bbRange > 0) (currentPrice - td.bollingerLower) / bbRange else 0.5
            volatilityScore += when {
                pos < 0.2  -> { insights.add("Price near lower Bollinger Band — potential bounce zone."); 8.0 }
                pos > 0.85 -> 3.0
                else       -> 6.0
            }
        } else {
            volatilityScore += 6.0
        }
        if (td.atr > 0) {
            val atrPct = if (currentPrice > 0) td.atr / currentPrice else 0.0
            volatilityScore += if (atrPct > 0.015) 9.0 else 5.0
        } else {
            volatilityScore += 5.0
        }
        val volatilityContrib = volatilityScore.coerceIn(0.0, 15.0)

        // ── Trend Strength / ADX (max 10 pts) ─────────────────────────────────
        val strengthContrib = when {
            td.adx > 40 -> { insights.add("ADX ${String.format("%.1f", td.adx)} — very strong trend."); 10.0 }
            td.adx > 25 -> { insights.add("ADX ${String.format("%.1f", td.adx)} — trend is confirmed."); 7.0 }
            td.adx > 0  -> 3.0
            else        -> 5.0 // not computed, stay neutral
        }

        val total = (trendContrib + momentumContrib + volumeContrib + volatilityContrib + strengthContrib)
            .toInt().coerceIn(0, 100)

        val label = when {
            total >= 80 -> "Strong Bullish"
            total >= 60 -> "Bullish"
            total >= 40 -> "Neutral"
            total >= 20 -> "Bearish"
            else        -> "Strong Bearish"
        }

        return SmartSignalScore(
            score = total,
            label = label,
            trendScore = trendContrib,
            momentumScore = momentumContrib,
            volumeScore = volumeContrib,
            volatilityScore = volatilityContrib,
            trendStrengthScore = strengthContrib,
            insights = insights
        )
    }
}

