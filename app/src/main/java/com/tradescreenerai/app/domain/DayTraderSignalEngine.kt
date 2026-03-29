package com.tradescreenerai.app.domain

import com.tradescreenerai.app.data.model.*
import kotlin.math.abs

/**
 * Professional Day Trader signal engine.
 *
 * Architecture:
 *  1. Candlestick pattern detection (engulfing, pin bar, doji, hammer, shooting star,
 *     morning star, evening star, marubozu, inverted hammer)
 *  2. Multi-layer confirmation scoring:
 *       • Trend alignment  (EMA9 vs EMA20, price vs EMA50)
 *       • Volume confirmation
 *       • RSI momentum
 *       • MACD histogram direction + crossover
 *       • Support / Resistance proximity
 *       • VWAP context
 *       • ATR volatility filter
 *       • Breakout vs fakeout check
 *  3. Quality gates: minimum score + minimum confirmations + at least trend or (vol+momentum)
 *  4. Latest-bar status: shows exactly why a signal did/didn't fire on the current bar
 *  5. Backtest: simulates signal performance against historical bars
 */
object DayTraderSignalEngine {

    // ── Crypto → Binance pair mapping ─────────────────────────────────────────
    private val cryptoToBinance = mapOf(
        "bitcoin" to "BTCUSDT", "ethereum" to "ETHUSDT", "binancecoin" to "BNBUSDT",
        "solana" to "SOLUSDT", "cardano" to "ADAUSDT", "ripple" to "XRPUSDT",
        "polkadot" to "DOTUSDT", "dogecoin" to "DOGEUSDT", "avalanche-2" to "AVAXUSDT",
        "chainlink" to "LINKUSDT", "polygon" to "MATICUSDT", "litecoin" to "LTCUSDT",
        "uniswap" to "UNIUSDT", "stellar" to "XLMUSDT", "cosmos" to "ATOMUSDT",
        "near" to "NEARUSDT", "algorand" to "ALGOUSDT", "fantom" to "FTMUSDT",
        "aave" to "AAVEUSDT", "maker" to "MKRUSDT",
        "tron" to "TRXUSDT", "shiba-inu" to "SHIBUSDT", "pepe" to "PEPEUSDT",
        "arbitrum" to "ARBUSDT", "optimism" to "OPUSDT", "sui" to "SUIUSDT",
        "aptos" to "APTUSDT", "injective-protocol" to "INJUSDT",
        "render-token" to "RENDERUSDT", "filecoin" to "FILUSDT"
    )

    fun getBinanceSymbol(coinId: String): String? {
        val normalized = coinId.lowercase().trim()
        cryptoToBinance[normalized]?.let { return it }
        return normalized.replace("-", "").uppercase() + "USDT"
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  PART 1 — CANDLESTICK PATTERN DETECTION
    // ──────────────────────────────────────────────────────────────────────────

    private data class PatternResult(
        val name: String,
        val isBullish: Boolean,
        val strength: Int,   // 1 = weak, 2 = medium, 3 = strong
        val emoji: String
    )

    /**
     * Detects the most significant candlestick pattern at bar [i].
     * Requires i >= 2 to allow 3-candle patterns.
     * Returns null when no pattern is found (or pattern is a plain doji).
     */
    private fun detectPattern(entries: List<ChartEntry>, i: Int): PatternResult? {
        if (i < 2) return null

        val c  = entries[i]      // current candle
        val p  = entries[i - 1]  // previous candle
        val pp = entries[i - 2]  // two candles back

        val cBody  = abs(c.close  - c.open)
        val pBody  = abs(p.close  - p.open)
        val ppBody = abs(pp.close - pp.open)

        val cRange  = (c.high  - c.low).coerceAtLeast(1e-10)
        val pRange  = (p.high  - p.low).coerceAtLeast(1e-10)
        val ppRange = (pp.high - pp.low).coerceAtLeast(1e-10)

        val cBodyLo = minOf(c.open,  c.close)
        val cBodyHi = maxOf(c.open,  c.close)
        val pBodyLo = minOf(p.open,  p.close)
        val pBodyHi = maxOf(p.open,  p.close)

        val cLower = cBodyLo - c.low
        val cUpper = c.high  - cBodyHi

        val isCBull  = c.close  > c.open
        val isCBear  = c.close  < c.open
        val isPBull  = p.close  > p.open
        val isPBear  = p.close  < p.open
        val isPPBull = pp.close > pp.open
        val isPPBear = pp.close < pp.open

        // ── 3-candle patterns (checked first — higher precedence) ────────────

        // Morning Star: large bear → small star → large bull closing past midpoint
        if (isPPBear && ppBody / ppRange > 0.50 &&
            pBody / pRange < 0.35 &&
            isCBull && cBody / cRange > 0.45 &&
            c.close > (pp.open + pp.close) / 2) {
            return PatternResult("Morning Star", true, 3, "⭐")
        }

        // Evening Star: large bull → small star → large bear closing past midpoint
        if (isPPBull && ppBody / ppRange > 0.50 &&
            pBody / pRange < 0.35 &&
            isCBear && cBody / cRange > 0.45 &&
            c.close < (pp.open + pp.close) / 2) {
            return PatternResult("Evening Star", false, 3, "🌟")
        }

        // ── 2-candle patterns ────────────────────────────────────────────────

        // Bullish Engulfing: previous bearish candle fully engulfed by bullish candle
        if (isPBear && isCBull &&
            c.open  <= pBodyLo &&
            c.close >= pBodyHi &&
            cBody   >= pBody * 0.9) {
            val s = if (cBody > pBody * 1.3) 3 else 2
            return PatternResult("Bullish Engulfing", true, s, "🟢")
        }

        // Bearish Engulfing: previous bullish candle fully engulfed by bearish candle
        if (isPBull && isCBear &&
            c.open  >= pBodyHi &&
            c.close <= pBodyLo &&
            cBody   >= pBody * 0.9) {
            val s = if (cBody > pBody * 1.3) 3 else 2
            return PatternResult("Bearish Engulfing", false, s, "🔴")
        }

        // ── Single-candle patterns ───────────────────────────────────────────

        // Doji: negligible body — directional ambiguity, skip as standalone signal
        if (cBody / cRange < 0.05) return null

        // Hammer: long lower wick (≥2× body), tiny upper wick, close near top
        if (cLower >= cBody * 2.0 &&
            cUpper <= cBody * 0.5 &&
            cBody / cRange < 0.40 &&
            cBody > 0) {
            return PatternResult("Hammer", true, 2, "🔨")
        }

        // Shooting Star: long upper wick (≥2× body), tiny lower wick
        if (cUpper >= cBody * 2.0 &&
            cLower <= cBody * 0.5 &&
            cBody / cRange < 0.40 &&
            cBody > 0) {
            // Distinguish from inverted hammer by trend context — handled by scoring layer
            return PatternResult(
                if (isCBear) "Shooting Star" else "Inverted Hammer",
                isCBull,  // bullish if it closed up (inverted hammer), bearish if closed down
                if (isCBear) 2 else 1,
                if (isCBear) "💫" else "🔨"
            )
        }

        // Bullish Pin Bar: very long lower wick, close in upper 40% of range
        if (cLower >= cBody * 2.5 &&
            cLower >= cUpper * 2.0 &&
            cLower / cRange > 0.60) {
            return PatternResult("Bullish Pin Bar", true, 2, "📌")
        }

        // Bearish Pin Bar: very long upper wick, close in lower 40% of range
        if (cUpper >= cBody * 2.5 &&
            cUpper >= cLower * 2.0 &&
            cUpper / cRange > 0.60) {
            return PatternResult("Bearish Pin Bar", false, 2, "📌")
        }

        // Bullish Marubozu: strong bull candle with minimal wicks
        if (isCBull && cBody / cRange > 0.85 && cLower / cRange < 0.05) {
            return PatternResult("Bullish Marubozu", true, 2, "🟢")
        }

        // Bearish Marubozu: strong bear candle with minimal wicks
        if (isCBear && cBody / cRange > 0.85 && cUpper / cRange < 0.05) {
            return PatternResult("Bearish Marubozu", false, 2, "🔴")
        }

        return null
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  PART 2 — SUPPORT & RESISTANCE DETECTION
    // ──────────────────────────────────────────────────────────────────────────

    private data class SRLevel(val price: Double, val isSupport: Boolean, val tests: Int)

    /**
     * Identifies swing highs (resistance) and swing lows (support) over the last
     * [lookback] candles using a 3-candle pivot rule, then merges nearby clusters.
     */
    private fun findSRLevels(entries: List<ChartEntry>, lookback: Int = 100): List<SRLevel> {
        val start = maxOf(0, entries.size - lookback)
        val sub   = entries.subList(start, entries.size)
        val n     = 3
        val raw   = mutableListOf<SRLevel>()

        for (i in n until sub.size - n) {
            val high = sub[i].high
            val low  = sub[i].low

            if ((1..n).all { sub[i - it].high < high && sub[i + it].high < high }) {
                val tests = sub.count { abs(it.high - high) / high < 0.005 }
                raw.add(SRLevel(high, false, tests))
            }
            if ((1..n).all { sub[i - it].low > low && sub[i + it].low > low }) {
                val tests = sub.count { abs(it.low - low) / low.coerceAtLeast(1e-10) < 0.005 }
                raw.add(SRLevel(low, true, tests))
            }
        }

        // Merge price-nearby levels (within 0.5% of each other)
        val merged  = mutableListOf<SRLevel>()
        for (lvl in raw.sortedBy { it.price }) {
            val prev = merged.lastOrNull()
            if (prev != null && abs(lvl.price - prev.price) / lvl.price < 0.005) {
                if (lvl.tests > prev.tests) merged[merged.lastIndex] = lvl
            } else {
                merged.add(lvl)
            }
        }
        return merged
    }

    /** Returns (isNear, descriptionString) for a given level type near [price]. */
    private fun nearLevel(
        price: Double,
        atr: Double,
        levels: List<SRLevel>,
        isSupport: Boolean
    ): Pair<Boolean, String> {
        val tolerance = atr * 0.6
        val nearest   = levels
            .filter { it.isSupport == isSupport && abs(it.price - price) <= tolerance }
            .minByOrNull { abs(it.price - price) }
        return if (nearest != null) {
            val label = if (isSupport) "Support" else "Resistance"
            val tests = if (nearest.tests > 1) "(${nearest.tests}× tested)" else ""
            true to "$label @ ${formatPriceCompact(nearest.price)} $tests"
        } else false to ""
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  PART 3 — PRE-COMPUTED INDICATOR SERIES
    // ──────────────────────────────────────────────────────────────────────────

    private class IndicatorSeries(entries: List<ChartEntry>) {
        val closes: List<Double> = entries.map { it.close }
        val n: Int               = closes.size

        // EMA series — ema9[j] aligns with closes[j + 8]
        val ema9:  List<Double> = TechnicalAnalysis.calculateEMA(closes, 9)
        val ema20: List<Double> = TechnicalAnalysis.calculateEMA(closes, 20)
        val ema50: List<Double> = if (n >= 50) TechnicalAnalysis.calculateEMA(closes, 50) else emptyList()

        val atr:    Double = TechnicalAnalysis.calculateATR(entries)
        val vwap:   Double = TechnicalAnalysis.calculateVWAP(entries)
        val volAvg: Double = TechnicalAnalysis.calculateVolumeAvg20(entries).toDouble()

        // RSI per bar — rsiSeries[j] aligns with closes[j + 14]
        val rsiSeries: List<Double> = (14 until n).map { i ->
            TechnicalAnalysis.calculateRSI(closes.subList(maxOf(0, i - 27), i + 1))
        }

        // MACD histogram per bar, via full EMA series:
        //   ema12Full[j] → closes[j+11]
        //   ema26Full[j] → closes[j+25]
        //   macdLine[j]  → closes[j+25]   (after drop(14))
        //   sigLine[j]   → closes[j+33]
        //   histSeries[j]→ closes[j+33]
        val histOffset: Int
        val histSeries: List<Double>

        init {
            val ema12 = TechnicalAnalysis.calculateEMA(closes, 12)
            val ema26 = TechnicalAnalysis.calculateEMA(closes, 26)
            val macdLine = if (ema12.size > 14 && ema26.isNotEmpty())
                ema12.drop(14).zip(ema26) { a, b -> a - b }
            else emptyList()
            val sigLine = if (macdLine.size >= 9)
                TechnicalAnalysis.calculateEMA(macdLine, 9)
            else emptyList()
            histOffset = 33
            histSeries = sigLine.indices.map { j -> macdLine[j + 8] - sigLine[j] }
        }

        // Accessor helpers — return null/default if index out of range
        fun ema9At(i: Int)      = ema9.getOrNull(i - 8)
        fun ema20At(i: Int)     = ema20.getOrNull(i - 19)
        fun ema50At(i: Int)     = ema50.getOrNull(i - 49)
        fun rsiAt(i: Int)       = rsiSeries.getOrNull(i - 14) ?: 50.0
        fun prevRsiAt(i: Int)   = rsiSeries.getOrNull(i - 15) ?: rsiAt(i)
        fun histAt(i: Int)      = histSeries.getOrNull(i - histOffset) ?: 0.0
        fun prevHistAt(i: Int)  = histSeries.getOrNull(i - histOffset - 1) ?: histAt(i)
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  PART 4 — MULTI-LAYER SCORING ENGINE
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Scores a candidate signal at bar [i].
     * Returns (totalScore 0–100, listOfConfirmationLayers).
     *
     * Score breakdown (max 100):
     *   Pattern strength     : 0 – 25
     *   EMA9 vs EMA20 trend  : 0 – 10
     *   Price vs EMA50       : 0 – 10
     *   Volume confirmation  : 0 – 15
     *   RSI momentum         : 0 – 15
     *   MACD histogram       : 0 – 10
     *   Support / Resistance : 0 – 10
     *   VWAP context         : 0 – 5
     *   Breakout bonus/penalty         : ±5
     *   ATR volatility penalty (if extreme): -20%
     */
    private fun scoreCandidate(
        entries: List<ChartEntry>,
        i: Int,
        pattern: PatternResult,
        ind: IndicatorSeries,
        srLevels: List<SRLevel>
    ): Pair<Int, List<ConfirmationLayer>> {

        val e      = entries[i]
        val price  = e.close
        val isBull = pattern.isBullish
        val layers = mutableListOf<ConfirmationLayer>()
        var score  = 0

        // ── Pattern base (0..25) ──────────────────────────────────────────────
        score += when (pattern.strength) { 3 -> 25; 2 -> 18; else -> 10 }

        // ── 1. EMA9 vs EMA20 short-term trend (0..10) ────────────────────────
        val e9  = ind.ema9At(i)
        val e20 = ind.ema20At(i)
        if (e9 != null && e20 != null) {
            val aligned = if (isBull) e9 > e20 else e9 < e20
            if (aligned) {
                score += 10
                layers += ConfirmationLayer("Trend", true,
                    "EMA9 ${if (isBull) ">" else "<"} EMA20 — short-term trend aligned")
            } else {
                layers += ConfirmationLayer("Trend", false,
                    "EMA9 ${if (isBull) "<" else ">"} EMA20 — trend opposes signal")
            }
        }

        // ── 2. Price vs EMA50 medium-term trend (0..10) ───────────────────────
        val e50 = ind.ema50At(i)
        if (e50 != null) {
            val aligned = if (isBull) price > e50 else price < e50
            if (aligned) {
                score += 10
                layers += ConfirmationLayer("MA50", true,
                    "Price ${if (isBull) "above" else "below"} EMA50 — medium trend aligned")
            } else {
                layers += ConfirmationLayer("MA50", false,
                    "Price ${if (isBull) "below" else "above"} EMA50 — medium trend opposes")
            }
        }

        // ── 3. Volume confirmation (0..15) ───────────────────────────────────
        val volMult = if (ind.volAvg > 0) e.volume.toDouble() / ind.volAvg else 0.0
        val volScore = when {
            volMult >= 2.0 -> 15
            volMult >= 1.5 -> 10
            volMult >= 1.2 ->  5
            else           ->  0
        }
        score += volScore
        val volPassed = volMult >= 1.2
        layers += ConfirmationLayer("Volume", volPassed,
            if (volPassed) "Vol ${"%.1f".format(volMult)}× avg — confirms move"
            else           "Vol ${"%.1f".format(volMult)}× avg — low conviction")

        // ── 4. RSI momentum (0..15) ──────────────────────────────────────────
        val rsi     = ind.rsiAt(i)
        val prevRsi = ind.prevRsiAt(i)
        val rsiScore = when {
            isBull && prevRsi < 35 && rsi > 40 -> 15  // oversold recovery
            isBull && rsi in 38.0..58.0         ->  8  // healthy bullish zone
            isBull && rsi in 40.0..65.0         ->  5
            !isBull && prevRsi > 65 && rsi < 60 -> 15  // overbought rollover
            !isBull && rsi in 42.0..62.0        ->  8  // healthy bearish zone
            !isBull && rsi in 35.0..60.0        ->  5
            else                                ->  0
        }
        score += rsiScore
        val rsiPassed = rsiScore > 0
        val rsiDesc = when {
            isBull  && prevRsi < 35 && rsi > 40 -> "RSI ${rsi.toInt()} — recovering from oversold ✓"
            !isBull && prevRsi > 65 && rsi < 60 -> "RSI ${rsi.toInt()} — rolling over from overbought ✓"
            rsiPassed                           -> "RSI ${rsi.toInt()} — momentum aligned"
            isBull  && rsi > 70                 -> "RSI ${rsi.toInt()} — overbought, buying risky"
            !isBull && rsi < 30                 -> "RSI ${rsi.toInt()} — oversold, selling risky"
            else                                -> "RSI ${rsi.toInt()} — neutral"
        }
        layers += ConfirmationLayer("RSI", rsiPassed, rsiDesc)

        // ── 5. MACD histogram direction + crossover (0..10) ──────────────────
        val hist     = ind.histAt(i)
        val prevHist = ind.prevHistAt(i)
        val macdCross  = if (isBull) prevHist < 0 && hist >= 0 else prevHist > 0 && hist <= 0
        val macdTrend  = if (isBull) hist > 0 || hist > prevHist else hist < 0 || hist < prevHist
        val macdScore  = if (macdCross) 10 else if (macdTrend) 5 else 0
        score += macdScore
        val macdPassed = macdScore > 0
        layers += ConfirmationLayer("MACD", macdPassed,
            when {
                macdCross  -> "MACD crossing ${if (isBull) "positive" else "negative"} — strong signal"
                macdTrend  -> "MACD ${if (isBull) "bullish" else "bearish"} histogram"
                else       -> "MACD histogram against signal"
            })

        // ── 6. Support / Resistance proximity (0..10) ────────────────────────
        val (nearSup, supDesc) = nearLevel(price, ind.atr, srLevels, isSupport = true)
        val (nearRes, resDesc) = nearLevel(price, ind.atr, srLevels, isSupport = false)
        val srPassed = (isBull && nearSup) || (!isBull && nearRes)
        if (srPassed) score += 10
        layers += ConfirmationLayer("S/R", srPassed,
            when {
                isBull  && nearSup -> "Support bounce — $supDesc"
                !isBull && nearRes -> "Resistance rejection — $resDesc"
                isBull  && nearRes -> "Caution: near resistance $resDesc"
                !isBull && nearSup -> "Caution: near support $supDesc"
                else               -> "No clear S/R level nearby"
            })

        // ── 7. VWAP context (0..5) ───────────────────────────────────────────
        if (ind.vwap > 0) {
            val aligned = if (isBull) price > ind.vwap else price < ind.vwap
            if (aligned) {
                score += 5
                layers += ConfirmationLayer("VWAP", true,
                    "Price ${if (isBull) "above" else "below"} VWAP")
            } else {
                layers += ConfirmationLayer("VWAP", false,
                    "Price ${if (isBull) "below" else "above"} VWAP — counter-trend")
            }
        }

        // ── 8. Breakout vs fakeout (+5 / -5) ─────────────────────────────────
        if (isBull && nearRes) {
            val resPrice = srLevels
                .filter { !it.isSupport && abs(it.price - price) / price < 0.015 }
                .minByOrNull { abs(it.price - price) }?.price
            if (resPrice != null) {
                if (e.close > resPrice) {
                    score += 5
                    layers += ConfirmationLayer("Breakout", true,
                        "Clean breakout above resistance")
                } else if (e.high > resPrice) {
                    score -= 5
                    layers += ConfirmationLayer("Breakout", false,
                        "Fakeout risk — wick above resistance, close below")
                }
            }
        }

        // ── 9. ATR volatility filter ─────────────────────────────────────────
        val atrPct = if (price > 0) ind.atr / price else 0.0
        val volOk  = atrPct in 0.0008..0.09
        if (!volOk) {
            val penalty = (score * 0.20).toInt()
            score -= penalty
            layers += ConfirmationLayer("Volatility", false,
                if (atrPct > 0.09) "ATR ${"%.1f".format(atrPct * 100)}% — extreme volatility ⚠️"
                else               "ATR very low — possible consolidation zone")
        } else {
            layers += ConfirmationLayer("Volatility", true,
                "ATR ${"%.2f".format(atrPct * 100)}% — normal range")
        }

        return score.coerceIn(0, 100) to layers
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  PART 5 — SIGNAL GENERATION
    // ──────────────────────────────────────────────────────────────────────────

    fun generateSignals(
        entries: List<ChartEntry>,
        settings: DayTraderSettings
    ): List<DayTraderSignal> {
        if (entries.size < 35) return emptyList()

        val ind       = IndicatorSeries(entries)
        val srLevels  = findSRLevels(entries)
        val riskiness = settings.riskiness
        val isAggressive = riskiness == RiskinessLevel.HIGH

        val signals   = mutableListOf<DayTraderSignal>()

        // Exclude the very last (potentially live/incomplete) candle unless aggressive mode
        val scanEnd   = if (isAggressive) entries.size else entries.size - 1
        val scanStart = maxOf(35, entries.size - 90)

        for (i in scanStart until scanEnd) {
            val pattern = detectPattern(entries, i) ?: continue
            val (score, layers) = scoreCandidate(entries, i, pattern, ind, srLevels)

            // Quality gate 1: score threshold by mode
            if (score < riskiness.scoreThreshold) continue

            // Quality gate 2: minimum confirmed layers
            val passed = layers.count { it.passed }
            if (passed < riskiness.minConfirmations) continue

            // Quality gate 3: mode-specific core setup requirement
            // ── HIGH (Aggressive) logic is frozen and unchanged ───────────────
            val hasTrend    = layers.any { it.name in listOf("Trend", "MA50") && it.passed }
            val hasVol      = layers.any { it.name == "Volume"   && it.passed }
            val hasMom      = layers.any { it.name in listOf("RSI", "MACD") && it.passed }
            val hasSR       = layers.any { it.name == "S/R"      && it.passed }
            val hasBreakout = layers.any { it.name == "Breakout" && it.passed }
            val bothTrends  = layers.any { it.name == "Trend"    && it.passed } &&
                              layers.any { it.name == "MA50"     && it.passed }

            val gate3Passed = when (riskiness) {
                RiskinessLevel.HIGH ->
                    // ── AGGRESSIVE: unchanged ──────────────────────────────────
                    hasTrend || (hasVol && hasMom)

                RiskinessLevel.MEDIUM ->
                    // ── BALANCED: trend alone is sufficient, or vol+momentum,
                    //   or S/R + any one confirmation (relaxed from original) ──
                    hasTrend || (hasVol && hasMom) || (hasSR && (hasMom || hasVol))

                RiskinessLevel.LOW ->
                    // ── CONSERVATIVE: strong setups only — both EMAs, trend+S/R,
                    //   trend+vol+momentum, or trend+breakout+volume ─────────────
                    bothTrends ||
                    (hasTrend && hasSR) ||
                    (hasTrend && hasMom && hasVol) ||
                    (hasTrend && hasBreakout && hasVol)
            }
            if (!gate3Passed) continue

            val e        = entries[i]
            val price    = e.close
            val isBull   = pattern.isBullish
            val atrMult  = when (riskiness) {
                RiskinessLevel.LOW    -> 2.0
                RiskinessLevel.MEDIUM -> 1.5
                RiskinessLevel.HIGH   -> 1.0
            }
            val sl = if (isBull) price - ind.atr * atrMult else price + ind.atr * atrMult
            val tp = if (isBull) price + ind.atr * atrMult * 2.0 else price - ind.atr * atrMult * 2.0
            val rr = if (abs(price - sl) > 0) abs(tp - price) / abs(price - sl) else 0.0

            val strength = when {
                score >= 80 -> DayTraderSignalStrength.STRONG
                score >= 60 -> DayTraderSignalStrength.MEDIUM
                else        -> DayTraderSignalStrength.WEAK
            }

            // Human-readable reasons: pattern + each passed layer
            val reasons = mutableListOf(pattern.name)
            layers.filter { it.passed }.mapTo(reasons) { it.detail }

            // No duplicate signals within 5 bars in same direction
            val lastSig = signals.lastOrNull()
            if (lastSig != null && lastSig.isBuy == isBull && i - lastSig.index <= 5) continue

            signals += DayTraderSignal(
                index              = i,
                isBuy              = isBull,
                confidence         = score,
                entryPrice         = price,
                stopLoss           = sl,
                takeProfit         = tp,
                riskReward         = rr,
                reasons            = reasons,
                timestamp          = e.timestamp,
                patternName        = pattern.name,
                patternEmoji       = pattern.emoji,
                strengthLabel      = strength,
                confirmationLayers = layers,
                isEarlySignal      = isAggressive && i == entries.size - 1
            )
        }
        return signals
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  PART 6 — LATEST BAR STATUS
    //  Shows exactly why a signal did / didn't fire on the current bar
    // ──────────────────────────────────────────────────────────────────────────

    fun analyzeLatestBar(entries: List<ChartEntry>, settings: DayTraderSettings): LatestBarStatus {
        if (entries.size < 35) return LatestBarStatus()
        val i = entries.size - 1

        val pattern = detectPattern(entries, i)
            ?: return LatestBarStatus(
                hasPattern     = false,
                patternName    = "No Pattern",
                patternEmoji   = "⬜",
                blockedReasons = listOf("No actionable candlestick pattern on the current bar")
            )

        val ind      = IndicatorSeries(entries)
        val srLevels = findSRLevels(entries)
        val (score, layers) = scoreCandidate(entries, i, pattern, ind, srLevels)

        val riskiness = settings.riskiness
        val passed    = layers.count { it.passed }
        val blocked   = mutableListOf<String>()

        // ── Score check ───────────────────────────────────────────────────────
        val scoreGap = riskiness.scoreThreshold - score
        if (score < riskiness.scoreThreshold) {
            val nearMsg = if (scoreGap <= 8) " ← near miss!" else ""
            blocked += "Score $score/${riskiness.scoreThreshold} — $scoreGap pts short$nearMsg"
        }

        // ── Confirmation count ────────────────────────────────────────────────
        val confShort = riskiness.minConfirmations - passed
        if (passed < riskiness.minConfirmations)
            blocked += "$passed/${riskiness.minConfirmations} confirmations met — need $confShort more"

        // ── Mode-specific Gate 3 ──────────────────────────────────────────────
        val hasTrend    = layers.any { it.name in listOf("Trend", "MA50") && it.passed }
        val hasVol      = layers.any { it.name == "Volume"   && it.passed }
        val hasMom      = layers.any { it.name in listOf("RSI", "MACD") && it.passed }
        val hasSR       = layers.any { it.name == "S/R"      && it.passed }
        val hasBreakout = layers.any { it.name == "Breakout" && it.passed }
        val bothTrends  = layers.any { it.name == "Trend"    && it.passed } &&
                          layers.any { it.name == "MA50"     && it.passed }

        val gate3Passed = when (riskiness) {
            RiskinessLevel.HIGH   -> hasTrend || (hasVol && hasMom)
            RiskinessLevel.MEDIUM -> hasTrend || (hasVol && hasMom) || (hasSR && (hasMom || hasVol))
            RiskinessLevel.LOW    ->
                bothTrends ||
                (hasTrend && hasSR) ||
                (hasTrend && hasMom && hasVol) ||
                (hasTrend && hasBreakout && hasVol)
        }
        if (!gate3Passed) {
            val gateDesc = when (riskiness) {
                RiskinessLevel.HIGH   -> "trend OR (volume + momentum)"
                RiskinessLevel.MEDIUM -> "trend, OR (volume + momentum), OR (S/R + any confirmation)"
                RiskinessLevel.LOW    -> "both EMAs aligned, OR trend + S/R, OR trend + vol + momentum"
            }
            blocked += "Core setup not met — need: $gateDesc"
        }

        if (riskiness != RiskinessLevel.HIGH)
            blocked += "Live candle not confirmed yet — switch to Aggressive mode for early signals"

        // ── Near-miss hints (failed layers that were close) ───────────────────
        val nearMissList = mutableListOf<String>()
        layers.filter { !it.passed }.forEach { layer ->
            when (layer.name) {
                "Volume" -> {
                    val mult = Regex("""([\d.]+)×""").find(layer.detail)
                        ?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                    if (mult != null && mult >= 0.85)
                        nearMissList += "Volume ${"%,.1f".format(mult)}× avg — just below 1.2× threshold"
                }
                "RSI"  -> nearMissList += "RSI — ${layer.detail}"
                "MACD" -> nearMissList += "MACD — ${layer.detail}"
                "Trend" -> if (!hasTrend)
                    nearMissList += "EMA9/20 not yet aligned — ${layer.detail}"
                "MA50"  -> if (!hasTrend)
                    nearMissList += "Price vs EMA50 not aligned — ${layer.detail}"
                "S/R"  -> nearMissList += "No nearby S/R level (would add +10 pts if present)"
            }
        }
        if (scoreGap in 1..10)
            nearMissList.add(0, "Only $scoreGap pts short of threshold — one more confirmation could flip this")

        return LatestBarStatus(
            hasPattern       = true,
            patternName      = pattern.name,
            patternEmoji     = pattern.emoji,
            isBullishPattern = pattern.isBullish,
            layers           = layers,
            score            = score,
            scoreNeeded      = riskiness.scoreThreshold,
            confirmedSignal  = blocked.isEmpty(),
            blockedReasons   = blocked,
            scoreGap         = scoreGap.coerceAtLeast(0),
            nearMissLayers   = nearMissList
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  PART 7 — BACKTESTING
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Simulates signal performance on [entries].
     * For each signal, checks up to 20 forward bars to see if TP or SL is hit first.
     */
    fun runBacktest(entries: List<ChartEntry>, settings: DayTraderSettings): DayTraderBacktestResult {
        if (entries.size < 40) return DayTraderBacktestResult()
        val testEntries = entries.dropLast(5)
        val allSignals  = generateSignals(testEntries, settings)
        if (allSignals.isEmpty()) return DayTraderBacktestResult()

        var wins   = 0
        var losses = 0
        val gainPcts = mutableListOf<Double>()
        val lossPcts = mutableListOf<Double>()

        for (sig in allSignals) {
            val barIdx = sig.index
            if (barIdx + 2 >= entries.size) continue
            var outcome = 0
            for (j in barIdx + 1 until minOf(barIdx + 21, entries.size)) {
                val bar = entries[j]
                if (sig.isBuy) {
                    if (bar.low  <= sig.stopLoss)  { outcome = -1; break }
                    if (bar.high >= sig.takeProfit) { outcome =  1; break }
                } else {
                    if (bar.high >= sig.stopLoss)  { outcome = -1; break }
                    if (bar.low  <= sig.takeProfit) { outcome =  1; break }
                }
            }
            when (outcome) {
                1  -> { wins++;   gainPcts += abs(sig.takeProfit - sig.entryPrice) / sig.entryPrice * 100 }
                -1 -> { losses++; lossPcts += abs(sig.stopLoss  - sig.entryPrice) / sig.entryPrice * 100 }
            }
        }

        val decided = wins + losses
        if (decided == 0) return DayTraderBacktestResult(totalSignals = allSignals.size)
        val wr  = wins.toDouble() / decided
        val avgGain = if (gainPcts.isNotEmpty()) gainPcts.average() else 0.0
        val avgLoss = if (lossPcts.isNotEmpty()) lossPcts.average() else 0.0
        return DayTraderBacktestResult(
            totalSignals = allSignals.size,
            wins         = wins,
            losses       = losses,
            winRate      = wr,
            avgGainPct   = avgGain,
            avgLossPct   = avgLoss,
            expectancy   = wr * avgGain - (1 - wr) * avgLoss
        )
    }

    // ── Alert detection on latest bar ─────────────────────────────────────────
    fun detectAlerts(
        entries: List<ChartEntry>,
        settings: DayTraderSettings,
        previousVwapSide: Boolean?
    ): List<DayTraderAlert> {
        if (entries.size < 26) return emptyList()
        val alerts = mutableListOf<DayTraderAlert>()
        val price  = entries.last().close
        val vwap   = TechnicalAnalysis.calculateVWAP(entries)
        val volAvg = TechnicalAnalysis.calculateVolumeAvg20(entries)
        val vol    = entries.last().volume
        val rsi    = TechnicalAnalysis.calculateRSI(entries.map { it.close })

        if (settings.alertVWAPCross && vwap > 0 && previousVwapSide != null) {
            val nowAbove = price > vwap
            if (nowAbove != previousVwapSide) {
                alerts += DayTraderAlert(DayTraderAlertType.VWAP_CROSS,
                    if (nowAbove) "Price crossed above VWAP at ${formatPriceCompact(price)}"
                    else          "Price crossed below VWAP at ${formatPriceCompact(price)}")
            }
        }
        if (settings.alertVolumeSpike && volAvg > 0 && vol > volAvg * 2) {
            alerts += DayTraderAlert(DayTraderAlertType.VOLUME_SPIKE,
                "Volume spike — ${"%.1f".format(vol.toDouble() / volAvg)}× average")
        }
        if (settings.alertRSIExtreme) {
            if (rsi > 70) alerts += DayTraderAlert(DayTraderAlertType.RSI_OVERBOUGHT,
                "RSI overbought at ${rsi.toInt()}")
            if (rsi < 30) alerts += DayTraderAlert(DayTraderAlertType.RSI_OVERSOLD,
                "RSI oversold at ${rsi.toInt()}")
        }
        return alerts
    }

    // ── Market session detection ──────────────────────────────────────────────
    fun detectMarketSession(assetType: AssetType, exchange: String = "NYSE"): MarketSessionInfo {
        if (assetType == AssetType.CRYPTO) {
            return MarketSessionInfo(
                type = MarketSessionType.CRYPTO_24H, label = "24/7 Market",
                openTime = "Always", closeTime = "Never",
                countdownLabel = "Market is always open", exchangeName = "Crypto"
            )
        }
        val et  = java.util.TimeZone.getTimeZone("America/New_York")
        val cal = java.util.Calendar.getInstance(et)
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

        if (dow == java.util.Calendar.SATURDAY || dow == java.util.Calendar.SUNDAY) {
            val toMon = when (dow) {
                java.util.Calendar.SATURDAY -> (2 * 24 * 60) - now + 4 * 60
                else -> (1 * 24 * 60) - now + 4 * 60
            }
            return MarketSessionInfo(MarketSessionType.CLOSED, "Market Closed",
                "Mon 4:00 AM ET", "—",
                "Opens in ${formatCountdown(toMon.toLong() * 60_000)}",
                toMon.toLong() * 60_000, exchangeName = exchange)
        }

        val preOpen    = 4 * 60
        val regOpen    = 9 * 60 + 30
        val regClose   = 16 * 60
        val afterClose = 20 * 60

        return when {
            now < preOpen -> {
                val cd = (preOpen - now).toLong() * 60_000
                MarketSessionInfo(MarketSessionType.CLOSED, "Market Closed",
                    "4:00 AM ET", "4:00 PM ET",
                    "Pre-market opens in ${formatCountdown(cd)}", cd, exchangeName = exchange)
            }
            now < regOpen -> {
                val cd = (regOpen - now).toLong() * 60_000
                MarketSessionInfo(MarketSessionType.PRE_MARKET, "Pre-Market",
                    "4:00 AM ET", "9:30 AM ET",
                    "Regular opens in ${formatCountdown(cd)}", cd,
                    isExtendedHours = true, exchangeName = exchange)
            }
            now < regClose -> {
                val cd   = (regClose - now).toLong() * 60_000
                val warn = if (cd <= 15 * 60_000) " ⚠️" else ""
                MarketSessionInfo(MarketSessionType.REGULAR, "Market Open$warn",
                    "9:30 AM ET", "4:00 PM ET",
                    "Closes in ${formatCountdown(cd)}", cd, exchangeName = exchange)
            }
            now < afterClose -> {
                val cd = (afterClose - now).toLong() * 60_000
                MarketSessionInfo(MarketSessionType.AFTER_HOURS, "After-Hours",
                    "4:00 PM ET", "8:00 PM ET",
                    "After-hours ends in ${formatCountdown(cd)}", cd,
                    isExtendedHours = true, exchangeName = exchange)
            }
            else -> {
                val cd = (24 * 60 - now + preOpen).toLong() * 60_000
                MarketSessionInfo(MarketSessionType.CLOSED, "Market Closed",
                    "Tomorrow 4:00 AM ET", "—",
                    "Opens in ${formatCountdown(cd)}", cd, exchangeName = exchange)
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────
    private fun formatCountdown(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    private fun formatPriceCompact(price: Double): String = when {
        price >= 1_000 -> "$${"%,.0f".format(price)}"
        price >= 1.0   -> "$${"%,.2f".format(price)}"
        price >= 0.01  -> "$${"%,.4f".format(price)}"
        else           -> "$${"%,.6f".format(price)}"
    }
}
