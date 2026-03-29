package com.tradescreenerai.app.domain

import com.tradescreenerai.app.data.model.*

/**
 * Confluence Score Engine — combines 6 indicators into a 0-100 score
 * with human-readable "Why This Trade" reasons.
 *
 * Weights: EMA 20 | MACD 20 | RSI 20 | Volume 15 | ATR 10 | ADX 15
 */
object ConfluenceScoreEngine {

    fun calculate(entries: List<ChartEntry>, td: TechnicalData): ConfluenceResult {
        if (entries.size < 14) return ConfluenceResult()

        val price   = entries.last().close
        val reasons = mutableListOf<String>()

        // ── EMA (max 20) ──────────────────────────────────────────────────────
        var ema = 0
        if (td.ema20 > 0 && price > td.ema20) { ema += 5; reasons.add("Price above 20 EMA — short-term bullish") }
        if (td.ema50 > 0 && price > td.ema50) { ema += 5; reasons.add("Price above 50 EMA — uptrend intact") }
        if (td.ema200 > 0 && price > td.ema200) { ema += 5 }
        if (td.ema50 > 0 && td.ema200 > 0 && td.ema50 > td.ema200) {
            ema += 5; reasons.add("Golden Cross (50 EMA > 200 EMA)")
        }
        if (td.ema20 > 0 && price < td.ema20) { ema -= 5 }
        if (td.ema50 > 0 && price < td.ema50) { ema -= 5 }
        val emaClamped = ema.coerceIn(-20, 20)

        // ── MACD (max 20) ─────────────────────────────────────────────────────
        var macd = 0
        when {
            td.macd > td.macdSignal && td.macd > 0 -> {
                macd = 20; reasons.add("MACD bullish crossover — strong momentum")
            }
            td.macd > td.macdSignal -> {
                macd = 12; reasons.add("MACD crossed above signal line")
            }
            td.macd < td.macdSignal && td.macd < 0 -> {
                macd = -20; reasons.add("MACD bearish — momentum fading")
            }
            td.macd < td.macdSignal -> { macd = -10 }
        }
        if (td.macdHistogram > 0 && macd > 0) macd = minOf(macd + 3, 20)

        // ── RSI (max 20) ──────────────────────────────────────────────────────
        var rsi = 0
        when {
            td.rsi < 30 -> {
                rsi = 18; reasons.add("RSI oversold at ${String.format("%.0f", td.rsi)} — bounce likely")
            }
            td.rsi < 40 -> {
                rsi = 12; reasons.add("RSI approaching oversold (${String.format("%.0f", td.rsi)})")
            }
            td.rsi in 40.0..60.0 -> { rsi = 5 }
            td.rsi in 60.0..70.0 -> { rsi = 10 }
            td.rsi > 70 -> {
                rsi = -10; reasons.add("RSI overbought at ${String.format("%.0f", td.rsi)} — risk of pullback")
            }
        }

        // ── Volume (max 15) ───────────────────────────────────────────────────
        var vol = 7 // neutral
        val lastVol = entries.last().volume
        if (td.volumeAvg20 > 0 && lastVol > 0) {
            val ratio = lastVol.toDouble() / td.volumeAvg20
            when {
                ratio >= 2.0 -> { vol = 15; reasons.add("Volume ${String.format("%.1f", ratio)}× above average — strong conviction") }
                ratio >= 1.5 -> { vol = 12; reasons.add("Volume ${String.format("%.1f", ratio)}× average — confirms the move") }
                ratio >= 1.0 -> { vol = 8 }
                ratio < 0.5  -> { vol = 2; reasons.add("Low volume — wait for confirmation") }
                else -> { vol = 5 }
            }
        }

        // ── ATR / volatility (max 10) ─────────────────────────────────────────
        var atr = 5
        if (td.atr > 0 && price > 0) {
            val atrPct = td.atr / price * 100
            atr = when {
                atrPct > 5   -> 3   // very volatile = risky
                atrPct > 2   -> 7
                atrPct > 0.5 -> 10  // moderate volatility = good
                else         -> 5
            }
        }

        // ── ADX trend strength (max 15) ───────────────────────────────────────
        var adx = 5
        when {
            td.adx > 40 -> { adx = 15; reasons.add("Strong trend (ADX ${String.format("%.0f", td.adx)}) — direction likely to continue") }
            td.adx > 25 -> { adx = 12; reasons.add("Trend confirmed (ADX ${String.format("%.0f", td.adx)})") }
            td.adx > 20 -> { adx = 7 }
            td.adx > 0  -> { adx = 3 }
        }

        // ── Total ─────────────────────────────────────────────────────────────
        // Normalise to 0-100: add 20 (to offset possible negative EMA/MACD) then scale
        val raw = emaClamped + macd + rsi + vol + atr + adx
        val score = ((raw + 20.0) / 120.0 * 100.0).toInt().coerceIn(0, 100)

        val label = when {
            score >= 80 -> "Strong Bullish"
            score >= 60 -> "Bullish"
            score >= 40 -> "Neutral"
            score >= 20 -> "Bearish"
            else        -> "Strong Bearish"
        }

        return ConfluenceResult(
            score = score, label = label,
            reasons = reasons.take(5),
            emaScore = emaClamped, macdScore = macd, rsiScore = rsi,
            volumeScore = vol, atrScore = atr, adxScore = adx
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Strategy classification
    // ─────────────────────────────────────────────────────────────────────────
    fun classifyStrategy(entries: List<ChartEntry>, td: TechnicalData): StrategyType? {
        if (entries.size < 26) return null
        val price = entries.last().close

        // Breakout: price near upper BB + volume spike + ADX rising
        val bbWidth = if (td.bollingerUpper > 0 && td.bollingerLower > 0)
            (td.bollingerUpper - td.bollingerLower) / td.bollingerMiddle.coerceAtLeast(0.01) else 0.0
        val volRatio = if (td.volumeAvg20 > 0) entries.last().volume.toDouble() / td.volumeAvg20 else 1.0

        if (price >= td.bollingerUpper * 0.98 && volRatio >= 1.5 && bbWidth < 0.08)
            return StrategyType.BREAKOUT

        // Reversal: RSI extreme + near BB band
        if ((td.rsi < 30 && price <= td.bollingerLower * 1.02) ||
            (td.rsi > 70 && price >= td.bollingerUpper * 0.98))
            return StrategyType.REVERSAL

        // High Momentum: RSI 50-70 + MACD bullish + volume above avg
        if (td.rsi in 50.0..70.0 && td.macd > td.macdSignal && td.macd > 0 && volRatio >= 1.2)
            return StrategyType.HIGH_MOMENTUM

        // Trend Continuation: ADX > 25 + EMAs aligned + price above 50 EMA
        if (td.adx > 25 && td.ema20 > td.ema50 && price > td.ema50)
            return StrategyType.TREND_CONTINUATION

        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ATR-based risk overlay
    // ─────────────────────────────────────────────────────────────────────────
    fun calculateRiskOverlay(entries: List<ChartEntry>, isBuy: Boolean): RiskOverlay {
        if (entries.size < 14) return RiskOverlay()
        val price = entries.last().close
        val atr   = TechnicalAnalysis.calculateATR(entries)
        if (atr <= 0 || price <= 0) return RiskOverlay(entryPrice = price)

        val slDistance = atr * 1.5
        val tpDistance = atr * 3.0  // 2:1 R:R

        val (stop, target) = if (isBuy) {
            (price - slDistance) to (price + tpDistance)
        } else {
            (price + slDistance) to (price - tpDistance)
        }

        val rr = if (slDistance > 0) tpDistance / slDistance else 0.0
        val riskPct = slDistance / price * 100

        return RiskOverlay(
            entryPrice = price,
            stopLoss = stop,
            takeProfit = target,
            riskRewardRatio = rr,
            atrValue = atr,
            riskPct = riskPct
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Multi-timeframe trend from a single OHLC dataset
    //  Derives approximate higher timeframes by resampling
    // ─────────────────────────────────────────────────────────────────────────
    fun multiTimeframeTrends(entries: List<ChartEntry>): List<TimeframeTrend> {
        if (entries.size < 30) return emptyList()

        val trends = mutableListOf<TimeframeTrend>()

        // Short-term: last 7 bars
        trends.add(trendForSlice(entries.takeLast(minOf(7, entries.size)), "Short"))
        // Medium-term: last 30 bars
        trends.add(trendForSlice(entries.takeLast(minOf(30, entries.size)), "Medium"))
        // Long-term: last 90 bars (or all)
        trends.add(trendForSlice(entries.takeLast(minOf(90, entries.size)), "Long"))
        // Full dataset
        if (entries.size > 90) trends.add(trendForSlice(entries, "Full"))

        return trends
    }

    private fun trendForSlice(slice: List<ChartEntry>, label: String): TimeframeTrend {
        if (slice.size < 3) return TimeframeTrend(label, SignalType.HOLD, "Not enough data")

        val closes = slice.map { it.close }
        val first  = closes.first()
        val last   = closes.last()
        val pctChange = (last - first) / first * 100
        val mid    = closes[closes.size / 2]
        val ema    = closes.takeLast(minOf(20, closes.size)).average()
        val aboveEma = last > ema

        val direction = when {
            pctChange > 3.0 && aboveEma  -> SignalType.BUY
            pctChange < -3.0 && !aboveEma -> SignalType.SELL
            pctChange > 1.0              -> SignalType.BUY
            pctChange < -1.0             -> SignalType.SELL
            else                         -> SignalType.HOLD
        }

        val desc = when (direction) {
            SignalType.BUY  -> "↑ ${String.format("%.1f", pctChange)}%"
            SignalType.SELL -> "↓ ${String.format("%.1f", pctChange)}%"
            else            -> "Flat"
        }

        return TimeframeTrend(label, direction, desc)
    }
}

