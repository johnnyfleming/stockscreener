package com.tradescreenerai.app.domain

import com.tradescreenerai.app.data.model.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * AI Price Prediction Engine — uses multi-indicator weighted scoring
 * to predict price direction with confidence levels.
 *
 * Combines: RSI, MACD, Supertrend, EMA Crossovers, Bollinger Bands,
 * Volume Profile, and ATR-based volatility modeling.
 */
object PricePredictionEngine {

    fun predict(
        entries: List<ChartEntry>,
        currentPrice: Double
    ): PredictionData {
        if (entries.size < 30 || currentPrice <= 0) return PredictionData()

        val closes = entries.map { it.close }
        val reasons = mutableListOf<String>()
        var bullScore = 0.0
        var bearScore = 0.0

        // 1. RSI Analysis (weight: 15%)
        val rsi = TechnicalAnalysis.calculateRSI(closes)
        when {
            rsi < 25 -> { bullScore += 15; reasons.add("RSI extremely oversold (${rsi.roundToInt()}) → bounce likely") }
            rsi < 35 -> { bullScore += 10; reasons.add("RSI oversold zone (${rsi.roundToInt()})") }
            rsi > 75 -> { bearScore += 15; reasons.add("RSI extremely overbought (${rsi.roundToInt()}) → pullback likely") }
            rsi > 65 -> { bearScore += 10; reasons.add("RSI overbought zone (${rsi.roundToInt()})") }
            rsi in 45.0..55.0 -> { /* neutral */ }
            rsi > 55 -> { bullScore += 5; reasons.add("RSI bullish momentum (${rsi.roundToInt()})") }
            else -> { bearScore += 5; reasons.add("RSI bearish momentum (${rsi.roundToInt()})") }
        }

        // 2. MACD Analysis (weight: 15%)
        val (macd, signal, histogram) = TechnicalAnalysis.calculateMACD(closes)
        when {
            macd > signal && histogram > 0 && histogram > abs(macd) * 0.1 -> {
                bullScore += 15; reasons.add("MACD strong bullish crossover")
            }
            macd > signal -> { bullScore += 8; reasons.add("MACD bullish") }
            macd < signal && histogram < 0 -> {
                bearScore += 15; reasons.add("MACD bearish crossover")
            }
            macd < signal -> { bearScore += 8; reasons.add("MACD bearish") }
        }

        // 3. EMA Trend (weight: 20%)
        val ema20 = TechnicalAnalysis.calculateEMA(closes, 20).lastOrNull() ?: currentPrice
        val ema50 = TechnicalAnalysis.calculateEMA(closes, 50).lastOrNull() ?: currentPrice
        when {
            currentPrice > ema20 && ema20 > ema50 -> {
                bullScore += 20; reasons.add("Strong uptrend (Price > EMA20 > EMA50)")
            }
            currentPrice > ema20 -> { bullScore += 12; reasons.add("Price above EMA20") }
            currentPrice < ema20 && ema20 < ema50 -> {
                bearScore += 20; reasons.add("Strong downtrend (Price < EMA20 < EMA50)")
            }
            currentPrice < ema20 -> { bearScore += 12; reasons.add("Price below EMA20") }
        }

        // 4. Supertrend (weight: 20%)
        val stSignals = TechnicalAnalysis.calculateSupertrend(entries)
        val lastSignal = stSignals.lastOrNull()
        if (lastSignal != null) {
            if (lastSignal.isBuy) {
                bullScore += 20; reasons.add("Supertrend BUY signal active")
            } else {
                bearScore += 20; reasons.add("Supertrend SELL signal active")
            }
        }

        // 5. Bollinger Bands (weight: 10%)
        val (bbUpper, bbMid, bbLower) = TechnicalAnalysis.calculateBollingerBands(closes)
        if (bbUpper > 0 && bbLower > 0) {
            val bbWidth = (bbUpper - bbLower) / bbMid
            when {
                currentPrice <= bbLower -> { bullScore += 10; reasons.add("Price at lower Bollinger Band → bounce likely") }
                currentPrice >= bbUpper -> { bearScore += 10; reasons.add("Price at upper Bollinger Band → pullback likely") }
                bbWidth < 0.04 -> { reasons.add("Bollinger squeeze → big move imminent") }
            }
        }

        // 6. Volume Analysis (weight: 10%)
        val recentVol = entries.takeLast(5).map { it.volume }.average()
        val avgVol = entries.takeLast(20).map { it.volume }.average()
        if (avgVol > 0) {
            val volRatio = recentVol / avgVol
            when {
                volRatio > 2.0 && currentPrice > ema20 -> {
                    bullScore += 10; reasons.add("Volume surge on upward trend (${String.format("%.1f", volRatio)}×)")
                }
                volRatio > 2.0 && currentPrice < ema20 -> {
                    bearScore += 10; reasons.add("Volume surge on downward trend (${String.format("%.1f", volRatio)}×)")
                }
                volRatio < 0.5 -> { reasons.add("Low volume — trend may be weakening") }
            }
        }

        // 7. Price momentum (weight: 10%)
        val priceMomentum5d = if (closes.size >= 5) {
            (closes.last() - closes[closes.size - 5]) / closes[closes.size - 5] * 100
        } else 0.0
        when {
            priceMomentum5d > 5 -> { bullScore += 10; reasons.add("Strong 5-day momentum (+${String.format("%.1f", priceMomentum5d)}%)") }
            priceMomentum5d > 2 -> { bullScore += 5; reasons.add("Positive 5-day momentum") }
            priceMomentum5d < -5 -> { bearScore += 10; reasons.add("Negative 5-day momentum (${String.format("%.1f", priceMomentum5d)}%)") }
            priceMomentum5d < -2 -> { bearScore += 5; reasons.add("Slight bearish momentum") }
        }

        // Calculate final scores
        val totalScore = bullScore - bearScore
        val maxPossible = 100.0
        val confidence = (abs(totalScore) / maxPossible * 100).coerceIn(0.0, 95.0)
        val direction = when {
            totalScore > 10 -> PredictionDirection.BULLISH
            totalScore < -10 -> PredictionDirection.BEARISH
            else -> PredictionDirection.NEUTRAL
        }

        // ATR-based price targets
        val atr = TechnicalAnalysis.calculateATR(entries)
        val multiplier = if (direction == PredictionDirection.BULLISH) 1.0 else -1.0
        val confidenceFactor = confidence / 100.0

        val target24h = currentPrice + (atr * 0.5 * multiplier * confidenceFactor)
        val target7d = currentPrice + (atr * 1.5 * multiplier * confidenceFactor)
        val target30d = currentPrice + (atr * 3.0 * multiplier * confidenceFactor)

        val riskLevel = when {
            atr / currentPrice > 0.05 -> "High"
            atr / currentPrice > 0.02 -> "Medium"
            else -> "Low"
        }

        return PredictionData(
            direction = direction,
            confidence = confidence,
            target24h = target24h,
            target7d = target7d,
            target30d = target30d,
            reasons = reasons.take(5),
            riskLevel = riskLevel
        )
    }
}

