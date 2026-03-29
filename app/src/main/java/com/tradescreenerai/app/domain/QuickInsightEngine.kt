package com.tradescreenerai.app.domain

import com.tradescreenerai.app.data.model.ChartEntry
import com.tradescreenerai.app.data.model.QuickInsightData
import com.tradescreenerai.app.data.model.TechnicalData

/**
 * Produces a beginner-friendly 3-line "Quick Insight" summary for the
 * detail screen panel, covering Trend / Momentum / Volume.
 */
object QuickInsightEngine {

    fun generate(td: TechnicalData, currentPrice: Double, entries: List<ChartEntry> = emptyList()): QuickInsightData {

        // ── Trend line ────────────────────────────────────────────────────────
        val trend = when {
            td.ema50 > 0 && td.ema200 > 0 && currentPrice > td.ema50 && currentPrice > td.ema200 ->
                "📈 Bullish — above 50 & 200 EMA"
            td.ema50 > 0 && td.ema200 > 0 && currentPrice < td.ema50 && currentPrice < td.ema200 ->
                "📉 Bearish — below 50 & 200 EMA"
            td.ema50 > 0 && currentPrice > td.ema50 ->
                "↗ Mildly Bullish — above 50 EMA"
            td.ema50 > 0 && currentPrice < td.ema50 ->
                "↘ Mildly Bearish — below 50 EMA"
            else -> "➡ Neutral — not enough trend data"
        }

        // ── Momentum line ─────────────────────────────────────────────────────
        val momentum = when {
            td.macd > td.macdSignal && td.rsi in 50.0..70.0 ->
                "⚡ Strong — MACD bullish, RSI healthy (${td.rsi.toInt()})"
            td.macd > td.macdSignal && td.rsi > 70.0 ->
                "⚠ Overbought — RSI ${td.rsi.toInt()}, consider caution"
            td.macd > td.macdSignal ->
                "🔼 Improving — MACD above signal line"
            td.rsi < 30.0 ->
                "🔻 Oversold — RSI ${td.rsi.toInt()}, bounce possible"
            td.rsi > 70.0 ->
                "🔺 Overbought — RSI ${td.rsi.toInt()}, watch for reversal"
            td.macd < td.macdSignal && td.rsi in 30.0..50.0 ->
                "⬇ Weak — MACD bearish, RSI below 50"
            else ->
                "〰 Mixed — MACD and RSI not aligned"
        }

        // ── Volume line ───────────────────────────────────────────────────────
        val currentVol = entries.lastOrNull()?.volume ?: 0L
        val volume = when {
            td.volumeAvg20 > 0 && currentVol > td.volumeAvg20 * 2 ->
                "🔊 High — volume ${formatVolRatio(currentVol, td.volumeAvg20)}× above average"
            td.volumeAvg20 > 0 && currentVol > td.volumeAvg20 ->
                "📊 Active — volume above 20-bar average"
            td.volumeAvg20 > 0 && currentVol < td.volumeAvg20 / 2 ->
                "🔇 Very low — weak participation"
            td.volumeAvg20 > 0 ->
                "📉 Low participation — below average volume"
            else -> "📊 Volume data unavailable"
        }

        // ── Optional alert ────────────────────────────────────────────────────
        val alert: String? = when {
            td.adx > 30 && td.ema50 > 0 && currentPrice > td.ema50 ->
                "⚡ Strong trend (ADX ${td.adx.toInt()}) — momentum likely to continue"
            td.bollingerUpper > 0 && currentPrice >= td.bollingerUpper ->
                "⚠ Price at upper Bollinger Band — potential resistance"
            td.bollingerLower > 0 && currentPrice <= td.bollingerLower ->
                "💡 Price at lower Bollinger Band — potential support"
            else -> null
        }

        return QuickInsightData(trend = trend, momentum = momentum, volume = volume, alert = alert)
    }

    private fun formatVolRatio(current: Long, avg: Long): String {
        if (avg == 0L) return "N/A"
        return String.format("%.1f", current.toDouble() / avg.toDouble())
    }
}

