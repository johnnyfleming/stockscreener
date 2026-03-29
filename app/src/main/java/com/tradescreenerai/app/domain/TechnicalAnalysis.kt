package com.tradescreenerai.app.domain

import com.tradescreenerai.app.data.model.*
import kotlin.math.abs
import kotlin.math.sqrt

object TechnicalAnalysis {

    fun calculateSMA(prices: List<Double>, period: Int): List<Double> {
        if (prices.size < period) return emptyList()
        return prices.windowed(period) { window -> window.average() }
    }

    fun calculateEMA(prices: List<Double>, period: Int): List<Double> {
        if (prices.isEmpty()) return emptyList()
        val multiplierEma = 2.0 / (period + 1)
        val ema = mutableListOf(prices.take(period).average())
        for (i in period until prices.size) {
            ema.add((prices[i] - ema.last()) * multiplierEma + ema.last())
        }
        return ema
    }

    fun calculateRSI(prices: List<Double>, period: Int = 14): Double {
        if (prices.size < period + 1) return 50.0
        val changes = prices.zipWithNext { a, b -> b - a }
        val recentChanges = changes.takeLast(period)
        val gains = recentChanges.filter { it > 0 }
        val losses = recentChanges.filter { it < 0 }.map { abs(it) }
        val avgGain = if (gains.isNotEmpty()) gains.average() else 0.001
        val avgLoss = if (losses.isNotEmpty()) losses.average() else 0.001
        val rs = avgGain / avgLoss
        return 100 - (100 / (1 + rs))
    }

    fun calculateMACD(prices: List<Double>): Triple<Double, Double, Double> {
        val ema12 = calculateEMA(prices, 12)
        val ema26 = calculateEMA(prices, 26)
        if (ema12.isEmpty() || ema26.isEmpty()) return Triple(0.0, 0.0, 0.0)
        val macdLine   = ema12.last() - ema26.last()
        val macdValues = ema12.takeLast(ema26.size).zip(ema26) { a, b -> a - b }
        val signalLine = calculateEMA(macdValues, 9).lastOrNull() ?: 0.0
        val histogram  = macdLine - signalLine
        return Triple(macdLine, signalLine, histogram)
    }

    fun calculateBollingerBands(prices: List<Double>, period: Int = 20): Triple<Double, Double, Double> {
        if (prices.size < period) return Triple(0.0, 0.0, 0.0)
        val recent = prices.takeLast(period)
        val middle = recent.average()
        val stdDev = sqrt(recent.map { (it - middle) * (it - middle) }.average())
        return Triple(middle + 2 * stdDev, middle, middle - 2 * stdDev)
    }

    fun calculateAllIndicators(entries: List<ChartEntry>): TechnicalData {
        val closePrices = entries.map { it.close }
        if (closePrices.size < 26) return TechnicalData()

        val rsi = calculateRSI(closePrices)
        val (macd, macdSignal, macdHist) = calculateMACD(closePrices)
        val sma20  = calculateSMA(closePrices, 20).lastOrNull() ?: 0.0
        val sma50  = calculateSMA(closePrices, 50).lastOrNull() ?: 0.0
        val sma200 = calculateSMA(closePrices, 200).lastOrNull() ?: 0.0
        val ema12  = calculateEMA(closePrices, 12).lastOrNull() ?: 0.0
        val ema26  = calculateEMA(closePrices, 26).lastOrNull() ?: 0.0
        val ema20  = calculateEMA(closePrices, 20).lastOrNull() ?: 0.0
        val ema50  = calculateEMA(closePrices, 50).lastOrNull() ?: 0.0
        val ema200 = calculateEMA(closePrices, 200).lastOrNull() ?: 0.0
        val (bbUpper, bbMiddle, bbLower) = calculateBollingerBands(closePrices)
        val vwap   = calculateVWAP(entries)
        val atr    = calculateATR(entries)
        val (adx, plusDI, minusDI) = calculateADX(entries)
        val (stochK, stochD) = calculateStochastic(entries)
        val obv    = calculateOBV(entries)
        val volAvg = calculateVolumeAvg20(entries)

        val signal = generateSignal(closePrices.last(), rsi, macd, macdSignal, sma20, sma50, sma200, bbUpper, bbLower)

        return TechnicalData(
            rsi = rsi, macd = macd, macdSignal = macdSignal, macdHistogram = macdHist,
            sma20 = sma20, sma50 = sma50, sma200 = sma200,
            ema12 = ema12, ema26 = ema26, ema20 = ema20, ema50 = ema50, ema200 = ema200,
            bollingerUpper = bbUpper, bollingerMiddle = bbMiddle, bollingerLower = bbLower,
            vwap = vwap, atr = atr, adx = adx, plusDI = plusDI, minusDI = minusDI,
            stochK = stochK, stochD = stochD, obv = obv, volumeAvg20 = volAvg,
            signal = signal
        )
    }

    // ── VWAP ──────────────────────────────────────────────────────────────────
    fun calculateVWAP(entries: List<ChartEntry>): Double {
        if (entries.isEmpty()) return 0.0
        var sumTPV = 0.0; var sumVolume = 0.0
        entries.forEach { e ->
            val tp = (e.high + e.low + e.close) / 3.0
            sumTPV += tp * e.volume; sumVolume += e.volume
        }
        return if (sumVolume > 0) sumTPV / sumVolume else 0.0
    }

    // ── ATR (Wilder) ──────────────────────────────────────────────────────────
    fun calculateATR(entries: List<ChartEntry>, period: Int = 14): Double {
        if (entries.size < 2) return 0.0
        val trs = entries.drop(1).mapIndexed { i, e ->
            val prev = entries[i]
            maxOf(e.high - e.low, abs(e.high - prev.close), abs(e.low - prev.close))
        }
        if (trs.size < period) return if (trs.isNotEmpty()) trs.average() else 0.0
        var atr = trs.take(period).average()
        for (i in period until trs.size) atr = (atr * (period - 1) + trs[i]) / period
        return atr
    }

    // ── ADX (+DI, -DI) ────────────────────────────────────────────────────────
    fun calculateADX(entries: List<ChartEntry>, period: Int = 14): Triple<Double, Double, Double> {
        if (entries.size < period * 2 + 1) return Triple(0.0, 0.0, 0.0)
        val n = entries.size
        val plusDM  = DoubleArray(n)
        val minusDM = DoubleArray(n)
        val tr      = DoubleArray(n)
        for (i in 1 until n) {
            val upMove   = entries[i].high - entries[i - 1].high
            val downMove = entries[i - 1].low - entries[i].low
            plusDM[i]  = if (upMove > downMove   && upMove > 0)   upMove   else 0.0
            minusDM[i] = if (downMove > upMove    && downMove > 0) downMove else 0.0
            tr[i] = maxOf(entries[i].high - entries[i].low,
                abs(entries[i].high - entries[i - 1].close),
                abs(entries[i].low  - entries[i - 1].close))
        }
        var smTR    = tr.slice(1..period).sum()
        var smPlus  = plusDM.slice(1..period).sum()
        var smMinus = minusDM.slice(1..period).sum()
        val dxList  = mutableListOf<Double>()
        var lastPlDI = 0.0; var lastMnDI = 0.0
        for (i in period + 1 until n) {
            smTR    = smTR    - smTR    / period + tr[i]
            smPlus  = smPlus  - smPlus  / period + plusDM[i]
            smMinus = smMinus - smMinus / period + minusDM[i]
            lastPlDI = if (smTR > 0) 100.0 * smPlus  / smTR else 0.0
            lastMnDI = if (smTR > 0) 100.0 * smMinus / smTR else 0.0
            val diSum = lastPlDI + lastMnDI
            if (diSum > 0) dxList.add(100.0 * abs(lastPlDI - lastMnDI) / diSum)
        }
        val adx = if (dxList.size >= period) {
            var avg = dxList.take(period).average()
            for (i in period until dxList.size) avg = (avg * (period - 1) + dxList[i]) / period
            avg
        } else if (dxList.isNotEmpty()) dxList.average() else 0.0
        return Triple(adx, lastPlDI, lastMnDI)
    }

    // ── Stochastic (Fast %K, %D) ──────────────────────────────────────────────
    fun calculateStochastic(entries: List<ChartEntry>, kPeriod: Int = 14, dPeriod: Int = 3): Pair<Double, Double> {
        if (entries.size < kPeriod) return Pair(50.0, 50.0)
        val kValues = mutableListOf<Double>()
        for (i in kPeriod - 1 until entries.size) {
            val window = entries.subList(i - kPeriod + 1, i + 1)
            val hi = window.maxOf { it.high }; val lo = window.minOf { it.low }
            kValues.add(if (hi != lo) 100.0 * (entries[i].close - lo) / (hi - lo) else 50.0)
        }
        val currentK = kValues.lastOrNull() ?: 50.0
        val currentD = if (kValues.size >= dPeriod) kValues.takeLast(dPeriod).average() else currentK
        return Pair(currentK, currentD)
    }

    // ── OBV ───────────────────────────────────────────────────────────────────
    fun calculateOBV(entries: List<ChartEntry>): Double {
        if (entries.isEmpty()) return 0.0
        var obv = 0.0
        for (i in 1 until entries.size) {
            obv += when {
                entries[i].close > entries[i - 1].close ->  entries[i].volume.toDouble()
                entries[i].close < entries[i - 1].close -> -entries[i].volume.toDouble()
                else -> 0.0
            }
        }
        return obv
    }

    // ── 20-Period Volume Average ───────────────────────────────────────────────
    fun calculateVolumeAvg20(entries: List<ChartEntry>): Long {
        if (entries.isEmpty()) return 0L
        return entries.takeLast(20).map { it.volume }.average().toLong()
    }

    fun generateSignal(
        currentPrice: Double, rsi: Double, macd: Double, macdSignal: Double,
        sma20: Double, sma50: Double, sma200: Double, bbUpper: Double, bbLower: Double
    ): Signal {
        val reasons = mutableListOf<String>()
        var buyScore = 0
        var sellScore = 0

        when {
            rsi < 30 -> { buyScore += 2; reasons.add("RSI oversold (${String.format(java.util.Locale.US, "%.1f", rsi)})") }
            rsi < 40 -> { buyScore += 1; reasons.add("RSI approaching oversold") }
            rsi > 70 -> { sellScore += 2; reasons.add("RSI overbought (${String.format(java.util.Locale.US, "%.1f", rsi)})") }
            rsi > 60 -> { sellScore += 1; reasons.add("RSI approaching overbought") }
        }
        when {
            macd > macdSignal && macd > 0 -> { buyScore += 2; reasons.add("MACD bullish crossover") }
            macd > macdSignal -> { buyScore += 1; reasons.add("MACD trending up") }
            macd < macdSignal && macd < 0 -> { sellScore += 2; reasons.add("MACD bearish crossover") }
            macd < macdSignal -> { sellScore += 1; reasons.add("MACD trending down") }
        }
        if (sma20 > 0 && sma50 > 0) {
            when {
                currentPrice > sma20 && sma20 > sma50 -> { buyScore += 2; reasons.add("Price above MAs (bullish trend)") }
                currentPrice > sma20 -> { buyScore += 1; reasons.add("Price above SMA20") }
                currentPrice < sma20 && sma20 < sma50 -> { sellScore += 2; reasons.add("Price below MAs (bearish trend)") }
                currentPrice < sma20 -> { sellScore += 1; reasons.add("Price below SMA20") }
            }
        }
        if (sma50 > 0 && sma200 > 0) {
            when {
                sma50 > sma200 -> { buyScore += 1; reasons.add("Golden cross (SMA50 > SMA200)") }
                sma50 < sma200 -> { sellScore += 1; reasons.add("Death cross (SMA50 < SMA200)") }
            }
        }
        if (bbUpper > 0 && bbLower > 0) {
            when {
                currentPrice <= bbLower -> { buyScore += 2; reasons.add("Price at lower Bollinger Band") }
                currentPrice >= bbUpper -> { sellScore += 2; reasons.add("Price at upper Bollinger Band") }
            }
        }

        val totalScore = buyScore - sellScore
        return when {
            totalScore >= 4  -> Signal(SignalType.BUY,  SignalStrength.STRONG,   reasons)
            totalScore >= 2  -> Signal(SignalType.BUY,  SignalStrength.MODERATE, reasons)
            totalScore >= 1  -> Signal(SignalType.BUY,  SignalStrength.WEAK,     reasons)
            totalScore <= -4 -> Signal(SignalType.SELL, SignalStrength.STRONG,   reasons)
            totalScore <= -2 -> Signal(SignalType.SELL, SignalStrength.MODERATE, reasons)
            totalScore <= -1 -> Signal(SignalType.SELL, SignalStrength.WEAK,     reasons)
            else             -> Signal(SignalType.HOLD, SignalStrength.MODERATE, reasons.ifEmpty { listOf("No strong signals") })
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Supertrend core – shared by both the signal list and the line renderer
    // ─────────────────────────────────────────────────────────────────────────
    private data class SupertrendArrays(
        val finalUpper: DoubleArray,
        val finalLower: DoubleArray,
        val isBullish: BooleanArray,
        val firstIdx: Int
    )

    private fun computeSupertrendArrays(
        entries: List<ChartEntry>,
        atrPeriod: Int,
        multiplier: Double
    ): SupertrendArrays? {
        if (entries.size < atrPeriod + 2) return null
        val n = entries.size

        // True Range
        val tr = DoubleArray(n)
        tr[0] = entries[0].high - entries[0].low
        for (i in 1 until n) {
            val e = entries[i]; val pc = entries[i - 1].close
            tr[i] = maxOf(e.high - e.low, abs(e.high - pc), abs(e.low - pc))
        }

        // ATR – Wilder smoothing (α = 1/atrPeriod)
        val atr = DoubleArray(n)
        atr[atrPeriod - 1] = tr.take(atrPeriod).average()
        for (i in atrPeriod until n) atr[i] = (atr[i - 1] * (atrPeriod - 1) + tr[i]) / atrPeriod

        val finalUpper = DoubleArray(n)
        val finalLower = DoubleArray(n)
        val isBullish  = BooleanArray(n)
        val firstIdx   = atrPeriod

        val hl2First = (entries[firstIdx].high + entries[firstIdx].low) / 2.0
        finalUpper[firstIdx] = hl2First + multiplier * atr[firstIdx]
        finalLower[firstIdx] = hl2First - multiplier * atr[firstIdx]
        isBullish[firstIdx]  = entries[firstIdx].close >= finalLower[firstIdx]

        for (i in firstIdx + 1 until n) {
            val e   = entries[i]
            val hl2 = (e.high + e.low) / 2.0
            val bu  = hl2 + multiplier * atr[i]
            val bl  = hl2 - multiplier * atr[i]

            finalUpper[i] = if (bu < finalUpper[i-1] || entries[i-1].close > finalUpper[i-1]) bu else finalUpper[i-1]
            finalLower[i] = if (bl > finalLower[i-1] || entries[i-1].close < finalLower[i-1]) bl else finalLower[i-1]

            isBullish[i] = if (!isBullish[i-1]) e.close > finalUpper[i]
                           else                  e.close >= finalLower[i]
        }
        return SupertrendArrays(finalUpper, finalLower, isBullish, firstIdx)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** Full Supertrend line — one point per candle from firstIdx onward.
     *  Value = finalLower (support, bullish) or finalUpper (resistance, bearish). */
    fun calculateSupertrendLine(
        entries: List<ChartEntry>,
        atrPeriod: Int = 7,
        multiplier: Double = 2.0
    ): List<SupertrendLinePoint> {
        val r = computeSupertrendArrays(entries, atrPeriod, multiplier) ?: return emptyList()
        return (r.firstIdx until entries.size).map { i ->
            SupertrendLinePoint(
                index     = i,
                value     = if (r.isBullish[i]) r.finalLower[i] else r.finalUpper[i],
                isBullish = r.isBullish[i]
            )
        }
    }

    /** Crossover signals only — a B or S badge at each direction flip.
     *  Default ATR=7 / mult=2.0 gives more frequent signals than the old 10/3.0. */
    fun calculateSupertrend(
        entries: List<ChartEntry>,
        atrPeriod: Int = 7,
        multiplier: Double = 2.0
    ): List<BuySellSignal> {
        val r = computeSupertrendArrays(entries, atrPeriod, multiplier) ?: return emptyList()
        val signals = mutableListOf<BuySellSignal>()
        for (i in r.firstIdx + 1 until entries.size) {
            if (r.isBullish[i] != r.isBullish[i - 1]) {
                signals.add(BuySellSignal(index = i, isBuy = r.isBullish[i]))
            }
        }
        return signals
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Confluence Zone Detection
    //  Returns a list of zones where EMA+MACD+RSI all agree on direction.
    //  Zone types: STRONG_BULLISH / WEAK_BULLISH / STRONG_BEARISH / WEAK_BEARISH
    // ─────────────────────────────────────────────────────────────────────────
    fun calculateConfluenceZones(entries: List<ChartEntry>): List<com.tradescreenerai.app.data.model.ConfluenceZone> {
        if (entries.size < 50) return emptyList()
        val closes   = entries.map { it.close }
        // EMA series: ema50[i] → entries[49+i]; ema200[i] → entries[199+i]
        val ema50List  = calculateEMA(closes, 50)
        val ema200List = if (closes.size >= 200) calculateEMA(closes, 200) else emptyList()
        val ema12List  = calculateEMA(closes, 12) // ema12[i] → entries[11+i]
        val ema26List  = calculateEMA(closes, 26) // ema26[i] → entries[25+i]

        val zones      = mutableListOf<com.tradescreenerai.app.data.model.ConfluenceZone>()
        var zoneStart  = -1
        var zoneType   : com.tradescreenerai.app.data.model.ConfluenceType? = null

        for (j in entries.indices) {
            val price   = closes[j]
            val ema50   = if (j >= 49) ema50List[j - 49] else null
            val ema200  = if (ema200List.isNotEmpty() && j >= 199) ema200List[j - 199] else null
            // MACD line at bar j
            val macd    = if (j >= 11 && j >= 25) {
                val i12 = j - 11; val i26 = j - 25
                if (i12 < ema12List.size && i26 < ema26List.size) ema12List[i12] - ema26List[i26] else null
            } else null
            // Simplified rolling RSI
            val rsi     = if (j >= 14) calculateRSI(closes.subList(j - 14, j + 1), 14) else 50.0

            val emaBull = ema50?.let { price > it } == true
            val emaBear = ema50?.let { price < it } == true

            val currentType: com.tradescreenerai.app.data.model.ConfluenceType? = when {
                emaBull && macd != null && macd > 0 && rsi > 50 ->
                    if (ema200 != null && price > ema200)
                        com.tradescreenerai.app.data.model.ConfluenceType.STRONG_BULLISH
                    else com.tradescreenerai.app.data.model.ConfluenceType.WEAK_BULLISH
                emaBear && macd != null && macd < 0 && rsi < 50 ->
                    if (ema200 != null && price < ema200)
                        com.tradescreenerai.app.data.model.ConfluenceType.STRONG_BEARISH
                    else com.tradescreenerai.app.data.model.ConfluenceType.WEAK_BEARISH
                else -> null
            }

            when {
                currentType == null -> {
                    if (zoneStart >= 0 && zoneType != null && j - zoneStart >= 3)
                        zones.add(com.tradescreenerai.app.data.model.ConfluenceZone(zoneStart, j - 1, zoneType!!))
                    zoneStart = -1; zoneType = null
                }
                currentType == zoneType -> { /* continue zone */ }
                else -> {
                    if (zoneStart >= 0 && zoneType != null && j - zoneStart >= 3)
                        zones.add(com.tradescreenerai.app.data.model.ConfluenceZone(zoneStart, j - 1, zoneType!!))
                    zoneStart = j; zoneType = currentType
                }
            }
        }
        // Close last zone
        if (zoneStart >= 0 && zoneType != null && entries.size - zoneStart >= 3)
            zones.add(com.tradescreenerai.app.data.model.ConfluenceZone(zoneStart, entries.size - 1, zoneType!!))
        return zones
    }

    /** Check current Supertrend direction from a raw sparkline price list.
     *  Builds synthetic OHLC by grouping prices into [groupSize]-point candles. */
    fun isCurrentlyBullish(
        prices: List<Double>,
        atrPeriod: Int = 7,
        multiplier: Double = 2.0,
        groupSize: Int = 4
    ): Boolean {
        if (prices.isEmpty()) return false
        val entries = prices.chunked(groupSize).mapIndexed { i, chunk ->
            ChartEntry(
                timestamp = i.toLong(),
                open      = chunk.first(),
                high      = chunk.max(),
                low       = chunk.min(),
                close     = chunk.last(),
                volume    = 0L
            )
        }
        val r = computeSupertrendArrays(entries, atrPeriod, multiplier) ?: return false
        return r.isBullish[entries.size - 1]
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Multi-Signal Engine
    //  Combines Supertrend + MACD + RSI + EMA + Bollinger + Stochastic
    //  Returns buy/sell badges with strength (1=weak, 2=moderate, 3=strong).
    // ─────────────────────────────────────────────────────────────────────────

    /** EMA aligned to the full price array (index 0..n-1, NaN where not yet valid). */
    private fun fullEMA(prices: List<Double>, period: Int): DoubleArray {
        val n = prices.size
        val r = DoubleArray(n) { Double.NaN }
        if (n < period) return r
        r[period - 1] = prices.take(period).average()
        val k = 2.0 / (period + 1)
        for (i in period until n) r[i] = (prices[i] - r[i - 1]) * k + r[i - 1]
        return r
    }

    /** MACD signal line (EMA-9 of macdLine), full-index. */
    private fun macdSignalLine(macdLine: DoubleArray): DoubleArray {
        val n = macdLine.size
        val r = DoubleArray(n) { Double.NaN }
        var sum = 0.0; var count = 0; var sig = Double.NaN
        val k9 = 2.0 / 10.0
        for (j in macdLine.indices) {
            if (macdLine[j].isNaN()) continue
            count++; sum += macdLine[j]
            if (count == 9) { sig = sum / 9; r[j] = sig }
            else if (count > 9) { sig = (macdLine[j] - sig) * k9 + sig; r[j] = sig }
        }
        return r
    }

    /** Wilder RSI series aligned to prices[0..n-1]. */
    private fun rsiSeries(closes: List<Double>, period: Int = 14): DoubleArray {
        val n = closes.size
        val r = DoubleArray(n) { Double.NaN }
        if (n < period + 1) return r
        var ag = 0.0; var al = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            if (d > 0) ag += d else al -= d
        }
        ag /= period; al /= period
        r[period] = 100.0 - 100.0 / (1.0 + if (al > 0) ag / al else 1e9)
        for (i in period + 1 until n) {
            val d = closes[i] - closes[i - 1]
            ag = (ag * (period - 1) + if (d > 0) d else 0.0) / period
            al = (al * (period - 1) + if (d < 0) -d else 0.0) / period
            r[i] = 100.0 - 100.0 / (1.0 + if (al > 0) ag / al else 1e9)
        }
        return r
    }

    /** Stochastic %K and %D full-index arrays. */
    private fun stochSeries(entries: List<ChartEntry>, k: Int = 14, d: Int = 3): Pair<DoubleArray, DoubleArray> {
        val n = entries.size
        val ks = DoubleArray(n) { Double.NaN }
        val ds = DoubleArray(n) { Double.NaN }
        for (i in k - 1 until n) {
            val w = entries.subList(i - k + 1, i + 1)
            val hi = w.maxOf { it.high }; val lo = w.minOf { it.low }
            ks[i] = if (hi != lo) 100.0 * (entries[i].close - lo) / (hi - lo) else 50.0
        }
        for (i in k + d - 2 until n) {
            val slice = ks.slice(i - d + 1..i)
            if (slice.none { it.isNaN() }) ds[i] = slice.average()
        }
        return ks to ds
    }

    /** Bollinger Band upper/lower full-index arrays. */
    private fun bbSeries(closes: List<Double>, period: Int = 20, devs: Double = 2.0): Pair<DoubleArray, DoubleArray> {
        val n = closes.size
        val up = DoubleArray(n) { Double.NaN }
        val lo = DoubleArray(n) { Double.NaN }
        for (i in period - 1 until n) {
            val w = closes.subList(i - period + 1, i + 1)
            val mean = w.average()
            val std  = kotlin.math.sqrt(w.map { (it - mean) * (it - mean) }.average())
            up[i] = mean + devs * std
            lo[i] = mean - devs * std
        }
        return up to lo
    }

    /** Merge raw signals within [window] bars: same direction → fuse; conflicting → keep strongest. */
    private fun mergeSignals(sorted: List<BuySellSignal>, window: Int = 2): List<BuySellSignal> {
        if (sorted.isEmpty()) return emptyList()
        val out = mutableListOf<BuySellSignal>()
        var grp = mutableListOf(sorted[0])
        for (i in 1 until sorted.size) {
            val prev = grp.last(); val cur = sorted[i]
            if (cur.index - prev.index <= window) {
                grp.add(cur)
            } else {
                out += collapseGroup(grp); grp = mutableListOf(cur)
            }
        }
        out += collapseGroup(grp)
        return out
    }

    private fun collapseGroup(grp: List<BuySellSignal>): BuySellSignal {
        val buys  = grp.filter { it.isBuy }
        val sells = grp.filter { !it.isBuy }
        val dominant = if (buys.size >= sells.size) buys else sells
        val isBuy   = dominant === buys
        val maxStr  = dominant.maxOf { it.strength }
        val sources = dominant.map { it.label }.distinct()
        val strength = (maxStr + (sources.size - 1)).coerceIn(1, 3)
        val label   = if (sources.size > 1) "MULTI" else sources.first()
        val best    = dominant.maxByOrNull { it.strength }!!
        return BuySellSignal(best.index, isBuy, strength, label)
    }

    /**
     * Strictly enforce Buy/Sell alternation — no two consecutive buys or sells.
     * When the same direction repeats, keep the strongest; replace if the newcomer is stronger.
     */
    private fun enforceAlternation(signals: List<BuySellSignal>): List<BuySellSignal> {
        if (signals.isEmpty()) return emptyList()
        val result = mutableListOf<BuySellSignal>()
        for (s in signals) {
            when {
                result.isEmpty()                       -> result.add(s)
                result.last().isBuy != s.isBuy         -> result.add(s)  // direction changed ✓
                s.strength > result.last().strength    -> result[result.lastIndex] = s  // same dir, stronger → replace
                // else: same direction and not stronger → skip
            }
        }
        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Japanese Candlestick Pattern Detector
    //  Analyses the last N candles for classic reversal & continuation patterns.
    // ─────────────────────────────────────────────────────────────────────────
    fun detectCandlePatterns(entries: List<ChartEntry>): List<com.tradescreenerai.app.data.model.CandlePattern> {
        if (entries.size < 3) return emptyList()
        val patterns = mutableListOf<com.tradescreenerai.app.data.model.CandlePattern>()

        for (i in 2 until entries.size) {
            val c0 = entries[i]
            val c1 = entries[i - 1]
            val c2 = entries[i - 2]

            val body0   = abs(c0.close - c0.open)
            val body1   = abs(c1.close - c1.open)
            val body2   = abs(c2.close - c2.open)
            val range0  = c0.high - c0.low
            val range1  = c1.high - c1.low
            val range2  = c2.high - c2.low
            val upper0  = c0.high - maxOf(c0.open, c0.close)
            val lower0  = minOf(c0.open, c0.close) - c0.low
            val upper1  = c1.high - maxOf(c1.open, c1.close)
            val lower1  = minOf(c1.open, c1.close) - c1.low
            val isGreen0 = c0.close > c0.open
            val isGreen1 = c1.close > c1.open
            val isGreen2 = c2.close > c2.open
            val isDoji1  = range1 > 0 && body1 < range1 * 0.08

            // ── HAMMER (bullish reversal) ──────────────────────────────────────
            // Small body at top, lower shadow ≥ 2× body, tiny upper shadow, after downtrend
            if (!isGreen1 && range0 > 0 && body0 > 0 &&
                lower0 >= 2.0 * body0 && upper0 <= 0.15 * range0 && isGreen0) {
                patterns.add(com.tradescreenerai.app.data.model.CandlePattern(
                    index = i, name = "Hammer", emoji = "🔨",
                    isBullish = true, strength = 2,
                    prediction = "Bullish reversal likely — sellers exhausted, buyers stepping in"
                ))
            }

            // ── INVERTED HAMMER (bullish reversal) ────────────────────────────
            if (!isGreen1 && range0 > 0 && body0 > 0 &&
                upper0 >= 2.0 * body0 && lower0 <= 0.15 * range0 && isGreen0) {
                patterns.add(com.tradescreenerai.app.data.model.CandlePattern(
                    index = i, name = "Inverted Hammer", emoji = "🔼",
                    isBullish = true, strength = 1,
                    prediction = "Potential bullish reversal — buyers tested higher prices"
                ))
            }

            // ── SHOOTING STAR (bearish reversal) ─────────────────────────────
            if (isGreen1 && range0 > 0 && body0 > 0 &&
                upper0 >= 2.0 * body0 && lower0 <= 0.15 * range0 && !isGreen0) {
                patterns.add(com.tradescreenerai.app.data.model.CandlePattern(
                    index = i, name = "Shooting Star", emoji = "💫",
                    isBullish = false, strength = 2,
                    prediction = "Bearish reversal signal — rally rejected at high, expect pullback"
                ))
            }

            // ── HANGING MAN (bearish reversal) ────────────────────────────────
            if (isGreen1 && range0 > 0 && body0 > 0 &&
                lower0 >= 2.0 * body0 && upper0 <= 0.15 * range0 && !isGreen0) {
                patterns.add(com.tradescreenerai.app.data.model.CandlePattern(
                    index = i, name = "Hanging Man", emoji = "🪝",
                    isBullish = false, strength = 2,
                    prediction = "Bearish warning at uptrend peak — momentum may be fading"
                ))
            }

            // ── DOJI (indecision) ─────────────────────────────────────────────
            if (range0 > 0 && body0 < range0 * 0.05) {
                patterns.add(com.tradescreenerai.app.data.model.CandlePattern(
                    index = i, name = "Doji", emoji = "⚖️",
                    isBullish = isGreen1, strength = 1,
                    prediction = "Market indecision — watch next candle for breakout direction"
                ))
            }

            // ── BULLISH ENGULFING ─────────────────────────────────────────────
            if (!isGreen1 && isGreen0 && body0 > body1 &&
                c0.open <= c1.close && c0.close >= c1.open) {
                val str = if (body0 > body1 * 1.5) 3 else 2
                patterns.add(com.tradescreenerai.app.data.model.CandlePattern(
                    index = i, name = "Bullish Engulfing", emoji = "🟢",
                    isBullish = true, strength = str,
                    prediction = "Strong bullish reversal — bulls completely overpowered bears"
                ))
            }

            // ── BEARISH ENGULFING ─────────────────────────────────────────────
            if (isGreen1 && !isGreen0 && body0 > body1 &&
                c0.open >= c1.close && c0.close <= c1.open) {
                val str = if (body0 > body1 * 1.5) 3 else 2
                patterns.add(com.tradescreenerai.app.data.model.CandlePattern(
                    index = i, name = "Bearish Engulfing", emoji = "🔴",
                    isBullish = false, strength = str,
                    prediction = "Strong bearish reversal — bears overwhelmed buyers, downside expected"
                ))
            }

            // ── MORNING STAR (3-candle bullish) ─────────────────────────────
            if (!isGreen2 && isDoji1 && isGreen0 &&
                c0.close > (c2.open + c2.close) / 2.0) {
                patterns.add(com.tradescreenerai.app.data.model.CandlePattern(
                    index = i, name = "Morning Star", emoji = "🌅",
                    isBullish = true, strength = 3,
                    prediction = "Powerful bullish reversal (3-candle) — downtrend ending, strong upside ahead"
                ))
            }

            // ── EVENING STAR (3-candle bearish) ─────────────────────────────
            if (isGreen2 && isDoji1 && !isGreen0 &&
                c0.close < (c2.open + c2.close) / 2.0) {
                patterns.add(com.tradescreenerai.app.data.model.CandlePattern(
                    index = i, name = "Evening Star", emoji = "🌆",
                    isBullish = false, strength = 3,
                    prediction = "Powerful bearish reversal (3-candle) — uptrend topping, decline expected"
                ))
            }

            // ── THREE WHITE SOLDIERS ─────────────────────────────────────────
            if (isGreen0 && isGreen1 && isGreen2 &&
                c0.close > c1.close && c1.close > c2.close &&
                range0 > 0 && range1 > 0 && range2 > 0 &&
                body0 > range0 * 0.5 && body1 > range1 * 0.5) {
                patterns.add(com.tradescreenerai.app.data.model.CandlePattern(
                    index = i, name = "3 White Soldiers", emoji = "⬆️",
                    isBullish = true, strength = 3,
                    prediction = "Strong uptrend continuation — three consecutive bullish sessions"
                ))
            }

            // ── THREE BLACK CROWS ────────────────────────────────────────────
            if (!isGreen0 && !isGreen1 && !isGreen2 &&
                c0.close < c1.close && c1.close < c2.close &&
                range0 > 0 && range1 > 0 && range2 > 0 &&
                body0 > range0 * 0.5 && body1 > range1 * 0.5) {
                patterns.add(com.tradescreenerai.app.data.model.CandlePattern(
                    index = i, name = "3 Black Crows", emoji = "⬇️",
                    isBullish = false, strength = 3,
                    prediction = "Strong downtrend continuation — three consecutive bearish sessions"
                ))
            }

            // ── BULLISH HARAMI ────────────────────────────────────────────────
            if (!isGreen1 && isGreen0 &&
                c0.open > c1.close && c0.close < c1.open && body0 < body1 * 0.5) {
                patterns.add(com.tradescreenerai.app.data.model.CandlePattern(
                    index = i, name = "Bullish Harami", emoji = "🌱",
                    isBullish = true, strength = 2,
                    prediction = "Potential bullish reversal — small green candle inside bearish bar"
                ))
            }

            // ── BEARISH HARAMI ────────────────────────────────────────────────
            if (isGreen1 && !isGreen0 &&
                c0.open < c1.close && c0.close > c1.open && body0 < body1 * 0.5) {
                patterns.add(com.tradescreenerai.app.data.model.CandlePattern(
                    index = i, name = "Bearish Harami", emoji = "🍂",
                    isBullish = false, strength = 2,
                    prediction = "Potential bearish reversal — small red candle inside bullish bar"
                ))
            }

            // ── PIERCING LINE (bullish) ────────────────────────────────────────
            if (!isGreen1 && isGreen0 &&
                c0.open < c1.low &&
                c0.close > (c1.open + c1.close) / 2.0 && c0.close < c1.open) {
                patterns.add(com.tradescreenerai.app.data.model.CandlePattern(
                    index = i, name = "Piercing Line", emoji = "📈",
                    isBullish = true, strength = 2,
                    prediction = "Bullish reversal — price pierced above midpoint of bearish candle"
                ))
            }

            // ── DARK CLOUD COVER (bearish) ────────────────────────────────────
            if (isGreen1 && !isGreen0 &&
                c0.open > c1.high &&
                c0.close < (c1.open + c1.close) / 2.0 && c0.close > c1.open) {
                patterns.add(com.tradescreenerai.app.data.model.CandlePattern(
                    index = i, name = "Dark Cloud Cover", emoji = "🌩️",
                    isBullish = false, strength = 2,
                    prediction = "Bearish reversal — dark cloud over bullish candle, expect decline"
                ))
            }
        }
        return patterns.sortedBy { it.index }
    }

    /**
     * Build an AI-style prediction summary from the most recent candle patterns + indicators.
     * Returns a structured analysis: direction, confidence %, key reasons.
     */
    fun buildPatternPrediction(
        entries: List<ChartEntry>,
        patterns: List<com.tradescreenerai.app.data.model.CandlePattern>
    ): com.tradescreenerai.app.data.model.PredictionData {
        if (entries.isEmpty()) return com.tradescreenerai.app.data.model.PredictionData()

        // Only look at the last 5 patterns
        val recentPatterns = patterns.takeLast(5)
        var bullScore = 0.0
        var bearScore = 0.0

        for (p in recentPatterns) {
            val weight = p.strength.toDouble()
            // More recent patterns get higher weight
            val recency = recentPatterns.indexOf(p) + 1  // 1 = oldest, 5 = newest
            val weighted = weight * recency * 0.4
            if (p.isBullish) bullScore += weighted else bearScore += weighted
        }

        // Add indicator confluence
        val closes = entries.map { it.close }
        val rsi = calculateRSI(closes)
        val (macd, macdSig, _) = calculateMACD(closes)
        val ema20 = calculateEMA(closes, 20).lastOrNull() ?: closes.last()
        val ema50 = calculateEMA(closes, 50).lastOrNull() ?: closes.last()
        val currentPrice = closes.last()

        if (rsi < 35) bullScore += 2.0
        else if (rsi > 65) bearScore += 2.0
        if (macd > macdSig) bullScore += 1.5 else bearScore += 1.5
        if (currentPrice > ema20) bullScore += 1.0 else bearScore += 1.0
        if (ema20 > ema50) bullScore += 1.0 else bearScore += 1.0

        val total = bullScore + bearScore
        val confidence = if (total > 0) (maxOf(bullScore, bearScore) / total * 100).coerceIn(0.0, 99.0) else 50.0
        val direction = when {
            bullScore > bearScore + 1.0 -> com.tradescreenerai.app.data.model.PredictionDirection.BULLISH
            bearScore > bullScore + 1.0 -> com.tradescreenerai.app.data.model.PredictionDirection.BEARISH
            else -> com.tradescreenerai.app.data.model.PredictionDirection.NEUTRAL
        }

        val reasons = mutableListOf<String>()
        recentPatterns.takeLast(3).forEach { p ->
            reasons.add("${p.emoji} ${p.name}: ${p.prediction}")
        }
        if (rsi < 35) reasons.add("RSI ${String.format(java.util.Locale.US, "%.0f", rsi)} — oversold territory")
        else if (rsi > 65) reasons.add("RSI ${String.format(java.util.Locale.US, "%.0f", rsi)} — overbought territory")
        if (macd > macdSig) reasons.add("MACD bullish crossover confirms upward momentum")
        else if (macd < macdSig) reasons.add("MACD bearish crossover signals downward pressure")
        if (currentPrice > ema20 && ema20 > ema50) reasons.add("Price riding above EMA20 & EMA50 — healthy uptrend")
        else if (currentPrice < ema20 && ema20 < ema50) reasons.add("Price below EMA20 & EMA50 — bearish trend structure")

        val riskLevel = when {
            confidence > 75 -> "Low"
            confidence > 55 -> "Medium"
            else -> "High"
        }

        return com.tradescreenerai.app.data.model.PredictionData(
            direction = direction,
            confidence = confidence,
            target24h = currentPrice * (1.0 + if (direction == com.tradescreenerai.app.data.model.PredictionDirection.BULLISH) 0.015 else -0.015),
            target7d  = currentPrice * (1.0 + if (direction == com.tradescreenerai.app.data.model.PredictionDirection.BULLISH) 0.045 else -0.045),
            target30d = currentPrice * (1.0 + if (direction == com.tradescreenerai.app.data.model.PredictionDirection.BULLISH) 0.12  else -0.12),
            reasons = reasons.take(5),
            riskLevel = riskLevel
        )
    }

    /**
     * calculateMultiSignals — the main signal generator.
     *
     * Fires B/S badges from SEVEN indicator groups:
     *  1. Supertrend direction flip          (strength 2)
     *  2. MACD line × signal-line crossover  (strength 1 below zero, 2 above zero)
     *  3. RSI bounce from extreme (< 30 / > 70)  (strength 1)
     *  4. EMA-9 × EMA-21 crossover           (strength 1)
     *  5. Stochastic %K × %D in extreme zone (strength 1)
     *  6. Bollinger Band pierce + recovery   (strength 1)
     *  7. Candlestick patterns (Hammer/Engulfing/Star etc.) (strength 1–3)
     *
     * Signals within 2 bars are fused. Strict Buy/Sell ALTERNATION is
     * enforced at the end — no consecutive same-direction badges ever.
     */
    fun calculateMultiSignals(
        entries: List<ChartEntry>,
        atrPeriod: Int = 7,
        multiplier: Double = 2.0
    ): List<BuySellSignal> {
        if (entries.size < 30) return emptyList()
        val closes = entries.map { it.close }
        val n      = entries.size
        val raw    = mutableListOf<BuySellSignal>()

        // Pre-compute indicator series
        val ema9F  = fullEMA(closes, 9)
        val ema21F = fullEMA(closes, 21)
        val ema12F = fullEMA(closes, 12)
        val ema26F = fullEMA(closes, 26)
        val macdL  = DoubleArray(n) { j ->
            val a = ema12F[j]; val b = ema26F[j]
            if (a.isNaN() || b.isNaN()) Double.NaN else a - b
        }
        val macdS  = macdSignalLine(macdL)
        val rsi    = rsiSeries(closes)
        val (stK, stD) = stochSeries(entries)
        val (bbUp, bbLo) = bbSeries(closes)
        val st     = computeSupertrendArrays(entries, atrPeriod, multiplier)

        // 1. Supertrend crossovers (weight: 2 — most reliable)
        if (st != null) {
            for (i in st.firstIdx + 1 until n) {
                if (st.isBullish[i] != st.isBullish[i - 1])
                    raw += BuySellSignal(i, st.isBullish[i], 2, "ST")
            }
        }

        // 2. MACD line × signal crossovers
        for (j in 1 until n) {
            val m0 = macdL[j]; val m1 = macdL[j-1]
            val s0 = macdS[j]; val s1 = macdS[j-1]
            if (m0.isNaN() || m1.isNaN() || s0.isNaN() || s1.isNaN()) continue
            if (m1 <= s1 && m0 > s0)
                raw += BuySellSignal(j, true,  if (m0 > 0) 2 else 1, "MACD")
            else if (m1 >= s1 && m0 < s0)
                raw += BuySellSignal(j, false, if (m0 < 0) 2 else 1, "MACD")
        }

        // 3. RSI extreme bounces
        for (j in 1 until n) {
            val r0 = rsi[j]; val r1 = rsi[j-1]
            if (r0.isNaN() || r1.isNaN()) continue
            if (r1 < 30 && r0 >= 30) raw += BuySellSignal(j, true,  1, "RSI")
            if (r1 > 70 && r0 <= 70) raw += BuySellSignal(j, false, 1, "RSI")
        }

        // 4. EMA-9 × EMA-21 crossovers
        for (j in 1 until n) {
            val f0 = ema9F[j]; val f1 = ema9F[j-1]
            val s0 = ema21F[j]; val s1 = ema21F[j-1]
            if (f0.isNaN() || f1.isNaN() || s0.isNaN() || s1.isNaN()) continue
            if (f1 <= s1 && f0 > s0) raw += BuySellSignal(j, true,  1, "EMA")
            if (f1 >= s1 && f0 < s0) raw += BuySellSignal(j, false, 1, "EMA")
        }

        // 5. Stochastic %K × %D in extreme zones (< 25 buy, > 75 sell)
        for (j in 1 until n) {
            val k0 = stK[j]; val k1 = stK[j-1]
            val d0 = stD[j]; val d1 = stD[j-1]
            if (k0.isNaN() || k1.isNaN() || d0.isNaN() || d1.isNaN()) continue
            if (k1 <= d1 && k0 > d0 && k0 < 25) raw += BuySellSignal(j, true,  1, "STOCH")
            if (k1 >= d1 && k0 < d0 && k0 > 75) raw += BuySellSignal(j, false, 1, "STOCH")
        }

        // 6. Bollinger Band pierce + reversal
        for (j in 1 until n) {
            val lo0 = bbLo[j]; val lo1 = bbLo[j-1]
            val up0 = bbUp[j]; val up1 = bbUp[j-1]
            if (lo0.isNaN() || up0.isNaN() || lo1.isNaN() || up1.isNaN()) continue
            val c0 = closes[j]; val c1 = closes[j-1]
            if (c1 <= lo1 && c0 > lo0) raw += BuySellSignal(j, true,  1, "BB")
            if (c1 >= up1 && c0 < up0) raw += BuySellSignal(j, false, 1, "BB")
        }

        // 7. Candlestick pattern signals (strength 1–3 from pattern engine)
        val candlePatterns = detectCandlePatterns(entries)
        for (p in candlePatterns) {
            // Only use moderate+ patterns (strength ≥ 2) to avoid noise
            if (p.strength >= 2) {
                raw += BuySellSignal(p.index, p.isBullish, p.strength, "CANDLE")
            }
        }

        raw.sortBy { it.index }
        // Step 1: merge signals within 2-bar window
        val merged = mergeSignals(raw, window = 2)
        // Step 2: enforce strict B/S/B/S alternation — THIS FIXES THE BUY-BUY GLITCH
        return enforceAlternation(merged)
    }
    //  Analyses short/medium/long-term data and returns agreement score
    // ─────────────────────────────────────────────────────────────────────────
    fun calculateMultiTimeframeConfluence(
        shortTerm: List<ChartEntry>,   // e.g. 7 days
        mediumTerm: List<ChartEntry>,  // e.g. 30 days
        longTerm: List<ChartEntry>     // e.g. 365 days
    ): MultiTimeframeSignal {
        fun getTimeframeSignal(entries: List<ChartEntry>): SignalType {
            if (entries.size < 14) return SignalType.HOLD
            val closes = entries.map { it.close }
            val rsi = calculateRSI(closes)
            val stSignals = calculateSupertrend(entries)
            val lastSt = stSignals.lastOrNull()
            val ema20 = calculateEMA(closes, minOf(20, closes.size - 1).coerceAtLeast(3)).lastOrNull() ?: closes.last()
            val currentPrice = closes.last()

            var bullScore = 0
            var bearScore = 0

            // Supertrend
            if (lastSt != null) {
                if (lastSt.isBuy) bullScore += 2 else bearScore += 2
            }
            // RSI
            when {
                rsi < 35 -> bullScore += 1
                rsi > 65 -> bearScore += 1
            }
            // EMA
            if (currentPrice > ema20) bullScore += 1 else bearScore += 1

            return when {
                bullScore >= 3 -> SignalType.BUY
                bearScore >= 3 -> SignalType.SELL
                else -> SignalType.HOLD
            }
        }

        val daily = getTimeframeSignal(shortTerm)
        val weekly = getTimeframeSignal(mediumTerm)
        val monthly = getTimeframeSignal(longTerm)

        val signals = listOf(daily, weekly, monthly)
        val buyCount = signals.count { it == SignalType.BUY }
        val sellCount = signals.count { it == SignalType.SELL }

        val confluenceScore = maxOf(buyCount, sellCount)
        val overall = when {
            buyCount >= 2 -> SignalType.BUY
            sellCount >= 2 -> SignalType.SELL
            else -> SignalType.HOLD
        }

        val label = when {
            confluenceScore == 3 && overall == SignalType.BUY -> "🟢 Strong Multi-TF Buy"
            confluenceScore == 3 && overall == SignalType.SELL -> "🔴 Strong Multi-TF Sell"
            confluenceScore == 2 && overall == SignalType.BUY -> "🟡 Multi-TF Buy Lean"
            confluenceScore == 2 && overall == SignalType.SELL -> "🟡 Multi-TF Sell Lean"
            else -> "⚪ Mixed Signals"
        }

        return MultiTimeframeSignal(
            daily = daily,
            weekly = weekly,
            monthly = monthly,
            confluenceScore = confluenceScore,
            overallSignal = overall,
            label = label
        )
    }
}
