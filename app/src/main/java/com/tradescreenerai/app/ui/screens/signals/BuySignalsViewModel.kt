package com.tradescreenerai.app.ui.screens.signals

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tradescreenerai.app.data.model.BuySignalConfig
import com.tradescreenerai.app.data.model.Crypto
import com.tradescreenerai.app.data.model.RiskOverlay
import com.tradescreenerai.app.data.model.StrategyType
import com.tradescreenerai.app.data.repository.CryptoRepository
import com.tradescreenerai.app.data.repository.LocalDataRepository
import com.tradescreenerai.app.domain.TechnicalAnalysis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout

data class BuySignalItem(
    val crypto: Crypto,
    val sparkline: List<Double>,
    val isBullish: Boolean = true,
    val reason: String = "Supertrend ↑",
    val reasons: List<String> = emptyList(),
    val confluenceScore: Int = 50,
    val confluenceLabel: String = "Neutral",
    val strategy: StrategyType? = null,
    val riskOverlay: RiskOverlay = RiskOverlay()
)

data class BuySignalsState(
    val items: List<BuySignalItem> = emptyList(),
    val config: BuySignalConfig = BuySignalConfig(),
    val isLoading: Boolean = true,
    val lastScanned: Long = 0L,
    val error: String? = null,
    val newSignalCount: Int = 0,
    val nextRefreshIn: Int = 60,
    val selectedStrategy: StrategyType? = null
)

class BuySignalsViewModel(application: Application) : AndroidViewModel(application) {

    private val cryptoRepo = CryptoRepository()
    private val localRepo  = LocalDataRepository(application)

    private val _state = MutableStateFlow(BuySignalsState())
    val state = _state.asStateFlow()

    private var autoScanJob: Job? = null

    companion object {
        const val CHANNEL_ID       = "buy_signals_channel"
        const val NOTIF_ID         = 9001
        const val REFRESH_INTERVAL = 60L
        const val SCAN_TIMEOUT_MS  = 25_000L
        const val MAX_OHLC_FETCHES = 8
    }

    init {
        createNotificationChannel()
        viewModelScope.launch {
            val savedConfig = localRepo.buySignalConfig.first()
            _state.value = _state.value.copy(config = savedConfig)
            startAutoScan()
        }
    }

    private fun startAutoScan() {
        autoScanJob?.cancel()
        autoScanJob = viewModelScope.launch {
            while (true) {
                performScan()
                for (sec in REFRESH_INTERVAL.toInt() downTo 1) {
                    _state.value = _state.value.copy(nextRefreshIn = sec)
                    delay(1_000L)
                }
            }
        }
    }

    fun scan() { startAutoScan() }

    // ─────────────────────────────────────────────────────────────────────────
    //  Multi-criteria signal detection — collects ALL matching reasons.
    //  Returns (isBullish, primaryReason, allReasons, strategyTag, score)
    // ─────────────────────────────────────────────────────────────────────────
    private data class DetectResult(
        val isBullish: Boolean,
        val reason: String,
        val reasons: List<String>,
        val strategy: StrategyType?,
        val score: Int
    )

    private fun detectBullishFull(prices: List<Double>, cfg: BuySignalConfig): DetectResult {
        if (prices.size < 30) return DetectResult(false, "", emptyList(), null, 0)

        val n = prices.size
        val reasons = mutableListOf<String>()
        var score = 0

        // 1. Supertrend (configured)
        if (TechnicalAnalysis.isCurrentlyBullish(prices, cfg.atrPeriod, cfg.multiplier, 4)) {
            reasons.add("Supertrend ↑ — bullish trend active")
            score += 20
        }

        // 2. Supertrend relaxed
        if (TechnicalAnalysis.isCurrentlyBullish(prices, 5, 1.5, 4)) {
            if (reasons.none { it.startsWith("Supertrend") }) reasons.add("Supertrend (sensitive) ↑")
            score += 10
        }

        // 3. RSI oversold recovery
        val rsi = simpleRSI(prices, minOf(14, n / 3))
        val avg3 = prices.takeLast(3).average()
        if (rsi < 42.0 && prices.last() > avg3) {
            reasons.add("RSI recovery — oversold at ${"%,.0f".format(rsi)}")
            score += 15
        } else if (rsi in 40.0..60.0) {
            score += 5
        }

        // 4. EMA 9/21 bullish crossover
        if (n >= 30) {
            val ema9  = simpleEMA(prices, 9)
            val ema21 = simpleEMA(prices, 21)
            if (ema9[n - 1] > ema21[n - 1] && ema9[maxOf(n - 5, 0)] <= ema21[maxOf(n - 5, 0)]) {
                reasons.add("EMA 9/21 bullish cross — short-term momentum shifting up")
                score += 15
            } else if (ema9[n - 1] > ema21[n - 1]) {
                score += 5
            }
        }

        // 5. Short-term momentum
        if (n >= 24) {
            val pct = (prices.last() - prices[n - 24]) / prices[n - 24] * 100.0
            if (pct > 3.0) {
                reasons.add("↑ ${"%,.1f".format(pct)}% momentum in last 24 bars")
                score += 15
            }
        }

        // 6. Bollinger Band bounce
        if (n >= 22) {
            val window = prices.takeLast(20)
            val mean   = window.average()
            val sd     = Math.sqrt(window.map { (it - mean) * (it - mean) }.average())
            val lower  = mean - 2.0 * sd
            if (prices[n - 3] <= lower * 1.01 && prices.last() > lower) {
                reasons.add("Bollinger Band bounce — price recovering from support")
                score += 15
            }
        }

        val isBullish = reasons.isNotEmpty()
        val clampedScore = score.coerceIn(0, 100)

        // Classify strategy from collected reasons
        val strategy = when {
            reasons.any { "Bollinger" in it } && reasons.any { "RSI" in it } -> StrategyType.REVERSAL
            reasons.any { "momentum" in it.lowercase() } && score >= 30 -> StrategyType.HIGH_MOMENTUM
            reasons.any { "EMA" in it } && reasons.any { "Supertrend" in it } -> StrategyType.TREND_CONTINUATION
            reasons.size >= 3 -> StrategyType.BREAKOUT
            reasons.any { "Supertrend" in it } -> StrategyType.TREND_CONTINUATION
            reasons.any { "RSI" in it || "Bollinger" in it } -> StrategyType.REVERSAL
            else -> null
        }

        return DetectResult(
            isBullish = isBullish,
            reason = reasons.firstOrNull() ?: "",
            reasons = reasons.take(5),
            strategy = strategy,
            score = clampedScore
        )
    }

    // Keep old signature for compatibility
    private fun detectBullish(prices: List<Double>, cfg: BuySignalConfig): Pair<Boolean, String> {
        val result = detectBullishFull(prices, cfg)
        return result.isBullish to result.reason
    }

    private fun simpleRSI(prices: List<Double>, period: Int): Double {
        if (prices.size < period + 1) return 50.0
        var ag = 0.0; var al = 0.0
        for (i in 1..period) {
            val d = prices[i] - prices[i - 1]
            if (d > 0) ag += d else al -= d
        }
        ag /= period; al /= period
        for (i in period + 1 until prices.size) {
            val d = prices[i] - prices[i - 1]
            ag = (ag * (period - 1) + maxOf(d, 0.0)) / period
            al = (al * (period - 1) + maxOf(-d, 0.0)) / period
        }
        return if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al)
    }

    private fun simpleEMA(prices: List<Double>, period: Int): DoubleArray {
        val n = prices.size
        val r = DoubleArray(n) { prices[0] }
        val k = 2.0 / (period + 1)
        for (i in 1 until n) r[i] = prices[i] * k + r[i - 1] * (1 - k)
        return r
    }

    // ─────────────────────────────────────────────────────────────────────────
    private suspend fun performScan() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val cfg = _state.value.config

            withTimeout(SCAN_TIMEOUT_MS) {

                val result = cryptoRepo.getMarkets(page = 1, perPage = cfg.poolSize)
                val allCryptos = when {
                    result is com.tradescreenerai.app.util.Resource.Success -> result.data ?: emptyList()
                    else -> emptyList()
                }

                if (allCryptos.isEmpty()) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Could not load market data — check connection."
                    )
                    return@withTimeout
                }

                val buyItems  = mutableListOf<BuySignalItem>()
                val minPrices = 30

                // Phase 1: sparkline scan
                val needsOhlc = mutableListOf<Crypto>()
                for (crypto in allCryptos) {
                    val prices = crypto.sparkline
                    if (prices.size >= minPrices) {
                        val result = detectBullishFull(prices, cfg)
                        if (result.isBullish) {
                            val risk = buildSimpleRisk(prices)
                            buyItems.add(BuySignalItem(
                                crypto = crypto, sparkline = prices,
                                isBullish = true, reason = result.reason,
                                reasons = result.reasons, confluenceScore = result.score,
                                confluenceLabel = confluenceLabel(result.score),
                                strategy = result.strategy, riskOverlay = risk
                            ))
                        }
                    } else {
                        needsOhlc.add(crypto)
                    }
                }

                // Phase 2: OHLC fallback (capped)
                val willDoOhlc   = needsOhlc.take(MAX_OHLC_FETCHES)
                val skippedCount = needsOhlc.size - willDoOhlc.size

                if (willDoOhlc.isNotEmpty()) {
                    try {
                        supervisorScope {
                            willDoOhlc.chunked(4).forEach { batch ->
                                val deferreds = batch.map { crypto ->
                                    async {
                                        try {
                                            val ohlcResult = withTimeout(6_000L) {
                                                cryptoRepo.getCoinOhlcData(crypto.id, "30")
                                            }
                                            val entries = when (ohlcResult) {
                                                is com.tradescreenerai.app.util.Resource.Success -> ohlcResult.data ?: emptyList()
                                                else -> emptyList()
                                            }
                                            if (entries.size >= minPrices) {
                                                val prices = entries.map { it.close }
                                                val result = detectBullishFull(prices, cfg)
                                                if (result.isBullish) {
                                                    val risk = buildSimpleRisk(prices)
                                                    BuySignalItem(
                                                        crypto = crypto, sparkline = prices,
                                                        isBullish = true, reason = result.reason,
                                                        reasons = result.reasons, confluenceScore = result.score,
                                                        confluenceLabel = confluenceLabel(result.score),
                                                        strategy = result.strategy, riskOverlay = risk
                                                    )
                                                } else null
                                            } else null
                                        } catch (_: Exception) { null }
                                    }
                                }
                                buyItems.addAll(deferreds.awaitAll().filterNotNull())
                                delay(150)
                            }
                        }
                    } catch (_: Exception) { }
                }

                val previousIds = localRepo.getLastBuySignalIds()
                val currentIds  = buyItems.map { it.crypto.id }.toSet()
                val newIds      = currentIds - previousIds
                val newCount    = newIds.size

                if (newCount > 0 && cfg.notificationsEnabled) {
                    val names = buyItems.filter { it.crypto.id in newIds }
                        .take(3).joinToString(", ") { it.crypto.symbol }
                    val more = if (newCount > 3) " +${newCount - 3} more" else ""
                    postNotification(
                        "$newCount new buy signal${if (newCount > 1) "s" else ""}!",
                        "$names$more — buy zone detected"
                    )
                }

                localRepo.saveLastBuySignalIds(currentIds)

                val infoMsg = when {
                    buyItems.isEmpty() -> "No signals right now — market ranging. Retrying in ${REFRESH_INTERVAL}s"
                    skippedCount > 0   -> "Checked ${allCryptos.size - skippedCount} / ${allCryptos.size} coins"
                    else               -> null
                }

                _state.value = _state.value.copy(
                    items          = buyItems.sortedByDescending { it.crypto.marketCap },
                    isLoading      = false,
                    lastScanned    = System.currentTimeMillis(),
                    newSignalCount = newCount,
                    error          = infoMsg
                )
            }

        } catch (e: CancellationException) {
            _state.value = _state.value.copy(isLoading = false)
            throw e
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            if (_state.value.items.isNotEmpty()) {
                _state.value = _state.value.copy(isLoading = false,
                    error = "Scan timed out — showing last results")
            } else {
                _state.value = _state.value.copy(isLoading = false,
                    error = "Scan timed out — tap Refresh to try again")
            }
        } catch (e: Exception) {
            val msg = when {
                "429" in (e.message ?: "") -> "Rate limited — retrying in ${REFRESH_INTERVAL}s"
                "timeout" in (e.message ?: "").lowercase() -> "Timed out — check your internet"
                else -> e.message ?: "Scan failed"
            }
            _state.value = _state.value.copy(isLoading = false, error = msg)
        }
    }

    fun updateConfig(config: BuySignalConfig) {
        _state.value = _state.value.copy(config = config)
        viewModelScope.launch { localRepo.saveBuySignalConfig(config) }
    }

    fun applyConfigAndRescan(config: BuySignalConfig) {
        updateConfig(config)
        startAutoScan()
    }

    fun selectStrategy(strategy: StrategyType?) {
        _state.value = _state.value.copy(selectedStrategy = strategy)
    }

    private fun confluenceLabel(score: Int): String = when {
        score >= 70 -> "Strong Bullish"
        score >= 50 -> "Bullish"
        score >= 30 -> "Neutral"
        else        -> "Weak"
    }

    /** Build a simplified risk overlay from raw prices (no OHLC needed). */
    private fun buildSimpleRisk(prices: List<Double>): RiskOverlay {
        if (prices.size < 14) return RiskOverlay()
        val price = prices.last()
        // Approximate ATR from price changes
        val changes = prices.zipWithNext { a, b -> kotlin.math.abs(b - a) }
        val atr = changes.takeLast(14).average()
        if (atr <= 0 || price <= 0) return RiskOverlay(entryPrice = price)

        val slDist = atr * 1.5
        val tpDist = atr * 3.0
        return RiskOverlay(
            entryPrice = price,
            stopLoss = price - slDist,
            takeProfit = price + tpDist,
            riskRewardRatio = if (slDist > 0) tpDist / slDist else 0.0,
            atrValue = atr,
            riskPct = slDist / price * 100
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Buy Signals", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Alerts when buy signals are detected" }
        val nm = getApplication<Application>()
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(title: String, body: String) {
        val ctx = getApplication<Application>()
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notif)
    }
}

