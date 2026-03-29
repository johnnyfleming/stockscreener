package com.tradescreenerai.app.domain

import com.tradescreenerai.app.data.model.*

/**
 * Commodity-specific analysis engine that reuses TechnicalAnalysis
 * calculations and adds commodity-specific features:
 *  - Seasonal trend detection
 *  - Support/resistance levels
 *  - Multi-timeframe performance
 *  - Trading signals optimized for commodity behavior
 */
object CommodityAnalysisEngine {

    /**
     * Generate buy/sell signals for commodity chart data.
     * Commodities tend to be trend-following, so we weight trend indicators more heavily.
     */
    fun generateCommoditySignals(entries: List<ChartEntry>): List<BuySellSignal> {
        if (entries.size < 26) return emptyList()

        val signals = mutableListOf<BuySellSignal>()
        val closePrices = entries.map { it.close }

        val ema9 = TechnicalAnalysis.calculateEMA(closePrices, 9)
        val ema20 = TechnicalAnalysis.calculateEMA(closePrices, 20)
        val ema50 = TechnicalAnalysis.calculateEMA(closePrices, 50)

        // Align arrays
        val minLen = minOf(ema9.size, ema20.size)
        if (minLen < 2) return emptyList()

        val offset9 = entries.size - ema9.size
        val offset20 = entries.size - ema20.size

        for (i in 1 until minLen) {
            val idx9 = ema9.size - minLen + i
            val idx20 = ema20.size - minLen + i
            val entryIdx = entries.size - minLen + i

            if (idx9 < 1 || idx20 < 1) continue

            // EMA 9/20 crossover signals
            val prevAbove = ema9[idx9 - 1] > ema20[idx20 - 1]
            val currAbove = ema9[idx9] > ema20[idx20]

            if (!prevAbove && currAbove) {
                // Bullish crossover
                val strength = if (ema50.isNotEmpty() && closePrices[entryIdx] > ema50.last()) 3 else 2
                signals.add(BuySellSignal(entryIdx, isBuy = true, strength = strength, label = "EMA"))
            } else if (prevAbove && !currAbove) {
                // Bearish crossover
                signals.add(BuySellSignal(entryIdx, isBuy = false, strength = 2, label = "EMA"))
            }
        }

        // RSI-based signals for commodity extremes
        for (i in 26 until entries.size) {
            val window = closePrices.subList(0, i + 1)
            val rsi = TechnicalAnalysis.calculateRSI(window)

            if (rsi < 28) {
                signals.add(BuySellSignal(i, isBuy = true, strength = 2, label = "RSI"))
            } else if (rsi > 72) {
                signals.add(BuySellSignal(i, isBuy = false, strength = 2, label = "RSI"))
            }
        }

        return signals.distinctBy { it.index }.takeLast(20)
    }

    /**
     * Calculate support and resistance levels for a commodity.
     */
    fun calculateSupportResistance(entries: List<ChartEntry>, levels: Int = 3): Pair<List<Double>, List<Double>> {
        if (entries.size < 20) return Pair(emptyList(), emptyList())

        val recent = entries.takeLast(60)
        val highs = recent.map { it.high }
        val lows = recent.map { it.low }

        // Simple pivot-point based S/R
        val pivotHigh = highs.max()
        val pivotLow = lows.min()
        val pivot = (pivotHigh + pivotLow + recent.last().close) / 3.0

        val supports = listOf(
            2 * pivot - pivotHigh,
            pivot - (pivotHigh - pivotLow),
            pivotLow
        ).sorted().take(levels)

        val resistances = listOf(
            2 * pivot - pivotLow,
            pivot + (pivotHigh - pivotLow),
            pivotHigh
        ).sortedDescending().take(levels)

        return Pair(supports, resistances)
    }

    /**
     * Detect basic seasonal patterns for a commodity type.
     */
    fun getSeasonalContext(type: CommodityType): String {
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
        return when (type) {
            CommodityType.GOLD -> when (month) {
                in 1..2 -> "Gold often sees strength in Jan-Feb from holiday-driven Asian demand."
                in 8..9 -> "Gold tends to rally Aug-Sep ahead of Indian wedding season demand."
                else -> "No strong seasonal pattern this month."
            }
            CommodityType.NATURAL_GAS -> when (month) {
                in 10..12, 1 -> "Natural gas demand rises in winter months for heating."
                in 6..8 -> "Summer cooling demand can support natural gas prices."
                else -> "Shoulder season — typically lower demand."
            }
            CommodityType.WHEAT, CommodityType.CORN -> when (month) {
                in 4..6 -> "Spring planting season — weather concerns can create volatility."
                in 9..11 -> "Harvest season — increased supply may pressure prices."
                else -> "Between growing seasons — prices driven by exports and reserves."
            }
            CommodityType.COFFEE -> when (month) {
                in 5..8 -> "Brazilian frost risk season — potential supply disruptions."
                else -> "No dominant seasonal pattern."
            }
            CommodityType.WTI, CommodityType.BRENT -> when (month) {
                in 4..6 -> "Summer driving season ahead — demand expectations rise."
                in 10..12 -> "Heating oil demand and OPEC meetings may drive volatility."
                else -> "No strong seasonal pattern this month."
            }
            else -> "No strong seasonal pattern identified for this month."
        }
    }

    /**
     * Generate a commodity risk-reward assessment.
     */
    fun assessCommodityRisk(
        entries: List<ChartEntry>,
        td: TechnicalData
    ): RiskOverlay {
        if (entries.isEmpty()) return RiskOverlay()
        val current = entries.last().close
        val atr = td.atr.takeIf { it > 0 } ?: TechnicalAnalysis.calculateATR(entries)

        val stopLoss = current - (atr * 2.0)
        val takeProfit = current + (atr * 3.0)
        val riskPct = if (current > 0) ((current - stopLoss) / current) * 100.0 else 0.0
        val rr = if (current - stopLoss > 0) (takeProfit - current) / (current - stopLoss) else 0.0

        return RiskOverlay(
            entryPrice = current,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            riskRewardRatio = rr,
            atrValue = atr,
            riskPct = riskPct
        )
    }
}

