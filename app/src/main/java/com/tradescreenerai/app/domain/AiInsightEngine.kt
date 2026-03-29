package com.tradescreenerai.app.domain

import com.tradescreenerai.app.data.model.SmartSignalScore
import com.tradescreenerai.app.data.model.TechnicalData

/**
 * Generates a short, beginner-friendly AI insight summary (3–4 lines)
 * based on computed technical indicators and the smart signal score.
 */
object AiInsightEngine {

    fun generateInsight(td: TechnicalData, smartScore: SmartSignalScore, currentPrice: Double): String {
        val lines = mutableListOf<String>()

        // Trend line
        lines += when {
            td.ema50 > 0 && td.ema200 > 0 && currentPrice > td.ema50 && currentPrice > td.ema200 ->
                "Trend is bullish — price is above key moving averages."
            td.ema50 > 0 && currentPrice > td.ema50 ->
                "Trend is mildly bullish — price is holding above the 50-period EMA."
            td.ema50 > 0 && currentPrice < td.ema50 ->
                "Trend is bearish — price has fallen below the 50-period EMA."
            else -> "Trend is unclear — not enough data for moving average signals."
        }

        // Momentum line
        lines += when {
            td.macd > td.macdSignal && td.rsi in 50.0..70.0 ->
                "Momentum is strong — both MACD and RSI support the upside."
            td.macd > td.macdSignal ->
                "Momentum is improving — MACD has crossed above its signal line."
            td.rsi < 30.0 ->
                "Momentum is oversold (RSI ${String.format("%.1f", td.rsi)}) — a bounce may be near."
            td.rsi > 70.0 ->
                "Momentum is stretched (RSI ${String.format("%.1f", td.rsi)}) — overbought conditions."
            else ->
                "Momentum is mixed — MACD and RSI do not confirm a clear direction."
        }

        // Volume/confirmation line
        lines += if (td.volumeAvg20 > 0L && smartScore.volumeScore >= 10.0)
            "Volume confirms the move — buyers are active above the 20-period average."
        else
            "Volume is average — wait for a surge to confirm any breakout."

        // Optional ADX/volatility line
        if (td.adx > 25) {
            lines += "Trend strength is high (ADX ${String.format("%.1f", td.adx)}) — the current direction is likely to continue."
        } else if (td.bollingerUpper > 0 && currentPrice < td.bollingerMiddle) {
            lines += "Price is in the lower half of the Bollinger Bands — watch for a mean-reversion bounce."
        }

        return lines.take(4).joinToString("\n")
    }
}

