package com.tradescreenerai.app.domain

import com.tradescreenerai.app.data.model.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Dynamic stock ranking engine — scores stocks on a 0–100 composite.
 * Does NOT hardcode picks; ranks dynamically based on technical data.
 *
 * Short-term weights: Momentum 30% | Volume 25% | Breakout 20% | Trend 15% | Sentiment 10%
 * Long-term weights:  Trend 30% | Consistency 25% | Momentum 15% | Volatility(low) 15% | Sentiment 15%
 */
object StockRankingEngine {

    // ─── Short-Term Ranking ───────────────────────────────────────────────────
    fun scoreShortTerm(
        stock: Stock,
        entries: List<ChartEntry>,
        td: TechnicalData,
        sentimentScore: Double = 50.0
    ): RankedStock {
        if (entries.size < 20) return RankedStock(stock = stock, score = 0, horizon = StockHorizon.SHORT_TERM)

        val reasons = mutableListOf<String>()
        val closePrices = entries.map { it.close }
        val currentPrice = closePrices.last()

        // ── Momentum Score (max 30) ───────────────────────────────────────────
        var momentumPts = 0.0

        // RSI: healthy range 40–65 is ideal for short-term buy
        when {
            td.rsi in 40.0..65.0 -> {
                momentumPts += 15.0
                reasons.add("RSI ${f(td.rsi)} — healthy momentum, not overbought")
            }
            td.rsi in 30.0..40.0 -> {
                momentumPts += 10.0
                reasons.add("RSI ${f(td.rsi)} — potentially bouncing from oversold")
            }
            td.rsi > 65.0 && td.rsi < 75.0 -> {
                momentumPts += 8.0
                reasons.add("RSI ${f(td.rsi)} — strong momentum, watch for pullback")
            }
            td.rsi >= 75.0 -> {
                momentumPts += 3.0
                reasons.add("RSI ${f(td.rsi)} — overbought, caution")
            }
            else -> momentumPts += 2.0
        }

        // MACD bullish
        if (td.macd > td.macdSignal) {
            momentumPts += if (td.macdHistogram > 0) 15.0 else 10.0
            reasons.add("MACD bullish crossover — momentum is building")
        } else {
            momentumPts += 2.0
        }

        // ── Volume Score (max 25) ─────────────────────────────────────────────
        var volumePts = 0.0
        val lastVolume = entries.last().volume
        if (td.volumeAvg20 > 0 && lastVolume > 0) {
            val ratio = lastVolume.toDouble() / td.volumeAvg20
            when {
                ratio >= 2.5 -> { volumePts = 25.0; reasons.add("Volume ${f(ratio)}× above average — strong conviction") }
                ratio >= 1.8 -> { volumePts = 20.0; reasons.add("Volume ${f(ratio)}× above average") }
                ratio >= 1.2 -> { volumePts = 15.0 }
                ratio >= 0.8 -> { volumePts = 8.0 }
                else -> volumePts = 3.0
            }
        } else {
            volumePts = 10.0 // neutral
        }

        // ── Breakout Score (max 20) ───────────────────────────────────────────
        var breakoutPts = 0.0

        // Price above key short-term EMAs
        if (td.ema20 > 0 && currentPrice > td.ema20) {
            breakoutPts += 7.0
            reasons.add("Price above 20 EMA — short-term uptrend confirmed")
        }
        if (td.ema50 > 0 && currentPrice > td.ema50) {
            breakoutPts += 5.0
        }

        // Bollinger Band breakout
        if (td.bollingerUpper > 0 && currentPrice > td.bollingerUpper * 0.98) {
            breakoutPts += 8.0
            reasons.add("Price near Bollinger upper band — potential breakout")
        }

        // Recent strong move (5-day return > 3%)
        if (entries.size >= 5) {
            val fiveDayReturn = (currentPrice - entries[entries.size - 5].close) / entries[entries.size - 5].close * 100
            if (fiveDayReturn > 3.0) {
                breakoutPts = (breakoutPts + 5.0).coerceAtMost(20.0)
                reasons.add("Strong 5-day move: +${f(fiveDayReturn)}%")
            }
        }

        // ── Trend Score (max 15) ──────────────────────────────────────────────
        var trendPts = 0.0
        if (td.ema20 > 0 && td.ema50 > 0 && td.ema20 > td.ema50) {
            trendPts += 10.0
            reasons.add("Short-term EMAs aligned bullish (20 > 50)")
        }
        if (td.adx > 25) {
            trendPts += 5.0
        }

        // ── Sentiment Score (max 10) ──────────────────────────────────────────
        val sentPts = (sentimentScore / 100.0 * 10.0).coerceIn(0.0, 10.0)
        if (sentimentScore > 65) reasons.add("Positive news sentiment")

        val total = (momentumPts.coerceAtMost(30.0) +
                volumePts.coerceAtMost(25.0) +
                breakoutPts.coerceAtMost(20.0) +
                trendPts.coerceAtMost(15.0) +
                sentPts).toInt().coerceIn(0, 100)

        return RankedStock(
            stock = stock,
            score = total,
            horizon = StockHorizon.SHORT_TERM,
            reasons = reasons.take(5),
            technicalData = td,
            momentumScore = momentumPts,
            trendScore = trendPts,
            volumeScore = volumePts,
            volatilityScore = breakoutPts,
            sentimentScore = sentPts
        )
    }

    // ─── Long-Term Ranking ────────────────────────────────────────────────────
    fun scoreLongTerm(
        stock: Stock,
        entries: List<ChartEntry>,
        td: TechnicalData,
        sentimentScore: Double = 50.0
    ): RankedStock {
        if (entries.size < 50) return RankedStock(stock = stock, score = 0, horizon = StockHorizon.LONG_TERM)

        val reasons = mutableListOf<String>()
        val closePrices = entries.map { it.close }
        val currentPrice = closePrices.last()

        // ── Trend Score (max 30) ──────────────────────────────────────────────
        var trendPts = 0.0

        // Price above 200 EMA — primary long-term signal
        if (td.ema200 > 0 && currentPrice > td.ema200) {
            trendPts += 15.0
            reasons.add("Price above 200 EMA — long-term uptrend intact")
        }

        // Golden cross (50 EMA > 200 EMA)
        if (td.ema50 > 0 && td.ema200 > 0 && td.ema50 > td.ema200) {
            trendPts += 10.0
            reasons.add("Golden Cross — 50 EMA above 200 EMA")
        }

        // Steady uptrend: price above all major EMAs
        if (td.ema20 > 0 && td.ema50 > 0 && td.ema200 > 0 &&
            currentPrice > td.ema20 && currentPrice > td.ema50 && currentPrice > td.ema200
        ) {
            trendPts += 5.0
            reasons.add("Price above all major EMAs — strong trend structure")
        }

        // ── Trend Consistency (max 25) ────────────────────────────────────────
        var consistencyPts = 0.0

        // ADX > 25 indicates strong trending
        if (td.adx > 25) {
            consistencyPts += 12.0
            reasons.add("ADX ${f(td.adx)} — confirmed strong trend")
        } else if (td.adx > 15) {
            consistencyPts += 6.0
        }

        // Medium-term momentum: 3-month return positive
        if (entries.size >= 66) {
            val threeMonthReturn = (currentPrice - entries[entries.size - 66].close) / entries[entries.size - 66].close * 100
            if (threeMonthReturn > 5.0) {
                consistencyPts += 8.0
                reasons.add("Steady 3-month gain: +${f(threeMonthReturn)}%")
            } else if (threeMonthReturn > 0) {
                consistencyPts += 4.0
            }
        }

        // Price staying above SMA200 consistently
        val above200Count = closePrices.takeLast(50).count { td.sma200 > 0 && it > td.sma200 }
        if (above200Count > 40) {
            consistencyPts += 5.0
        } else if (above200Count > 25) {
            consistencyPts += 2.0
        }

        // ── Momentum (max 15) ─────────────────────────────────────────────────
        var momentumPts = 0.0
        if (td.macd > td.macdSignal && td.macd > 0) {
            momentumPts += 10.0
            reasons.add("MACD positive and above signal — bullish momentum")
        } else if (td.macd > td.macdSignal) {
            momentumPts += 5.0
        }
        if (td.rsi in 45.0..65.0) {
            momentumPts += 5.0
        }

        // ── Low Volatility (max 15) ──────────────────────────────────────────
        var volPts = 0.0
        if (entries.size >= 20) {
            val returns = closePrices.zipWithNext { a, b -> (b - a) / a }
            val stdDev = if (returns.isNotEmpty()) {
                val mean = returns.average()
                sqrt(returns.map { (it - mean) * (it - mean) }.average())
            } else 0.0

            // Lower volatility = better for long-term
            when {
                stdDev < 0.015 -> { volPts = 15.0; reasons.add("Low volatility — stable uptrend") }
                stdDev < 0.025 -> { volPts = 11.0 }
                stdDev < 0.04 -> { volPts = 7.0 }
                else -> { volPts = 3.0; reasons.add("Higher volatility — more risk") }
            }
        } else {
            volPts = 7.0
        }

        // ── Sentiment (max 15) ────────────────────────────────────────────────
        val sentPts = (sentimentScore / 100.0 * 15.0).coerceIn(0.0, 15.0)
        if (sentimentScore > 65) reasons.add("Positive long-term sentiment")

        // ── Optional fundamentals bonus ───────────────────────────────────────
        if (stock.pe > 0 && stock.pe < 25) {
            reasons.add("P/E ratio ${f(stock.pe)} — reasonable valuation")
        }
        if (stock.dividend > 0) {
            reasons.add("Pays ${f(stock.dividend * 100)}% dividend")
        }

        val total = (trendPts.coerceAtMost(30.0) +
                consistencyPts.coerceAtMost(25.0) +
                momentumPts.coerceAtMost(15.0) +
                volPts.coerceAtMost(15.0) +
                sentPts).toInt().coerceIn(0, 100)

        return RankedStock(
            stock = stock,
            score = total,
            horizon = StockHorizon.LONG_TERM,
            reasons = reasons.take(5),
            technicalData = td,
            momentumScore = momentumPts,
            trendScore = trendPts + consistencyPts,
            volumeScore = 0.0,
            volatilityScore = volPts,
            sentimentScore = sentPts
        )
    }

    // ─── Breakout Detection ───────────────────────────────────────────────────
    fun isBreakout(entries: List<ChartEntry>, td: TechnicalData): Boolean {
        if (entries.size < 20) return false
        val current = entries.last().close
        val volume = entries.last().volume

        // Price above Bollinger upper band with volume spike
        val bbBreakout = td.bollingerUpper > 0 && current > td.bollingerUpper
        val volumeSpike = td.volumeAvg20 > 0 && volume > td.volumeAvg20 * 1.5

        // 20-day high breakout
        val high20 = entries.takeLast(20).maxOf { it.high }
        val newHigh = current >= high20 * 0.99

        return (bbBreakout && volumeSpike) || (newHigh && volumeSpike)
    }

    // ─── Reversal Detection ───────────────────────────────────────────────────
    fun isReversal(entries: List<ChartEntry>, td: TechnicalData): Boolean {
        if (entries.size < 20) return false

        // Oversold RSI + MACD bullish crossover = potential reversal
        val oversold = td.rsi < 35
        val macdCross = td.macd > td.macdSignal && td.macdHistogram > 0

        // Price was declining but now showing recovery
        val fiveDayReturn = if (entries.size >= 5) {
            (entries.last().close - entries[entries.size - 5].close) / entries[entries.size - 5].close * 100
        } else 0.0
        val tenDayReturn = if (entries.size >= 10) {
            (entries[entries.size - 5].close - entries[entries.size - 10].close) / entries[entries.size - 10].close * 100
        } else 0.0

        // Was declining, now recovering
        val recovery = tenDayReturn < -3.0 && fiveDayReturn > 0

        return (oversold && macdCross) || (recovery && macdCross)
    }

    private fun f(v: Double) = String.format(java.util.Locale.US, "%.1f", v)
}

