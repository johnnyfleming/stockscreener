package com.tradescreenerai.app.ui.screens.daytrader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.remote.RetrofitClient
import com.tradescreenerai.app.data.repository.CryptoRepository
import com.tradescreenerai.app.data.repository.LocalDataRepository
import com.tradescreenerai.app.domain.DayTraderSignalEngine
import com.tradescreenerai.app.domain.TechnicalAnalysis
import com.tradescreenerai.app.util.Resource
import com.google.gson.JsonArray
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DayTraderState(
    val symbol: String = "",
    val displayName: String = "",
    val assetType: AssetType = AssetType.CRYPTO,
    val entries: List<ChartEntry> = emptyList(),
    val technicalData: TechnicalData = TechnicalData(),
    val signals: List<DayTraderSignal> = emptyList(),
    val recentAlerts: List<DayTraderAlert> = emptyList(),
    val settings: DayTraderSettings = DayTraderSettings(),
    val session: MarketSessionInfo = MarketSessionInfo(),
    val currentPrice: Double = 0.0,
    val priceChange: Double = 0.0,
    val priceChangePct: Double = 0.0,
    val vwap: Double = 0.0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val lastUpdated: Long = 0L,
    val nextRefreshIn: Int = 0,
    val updateCount: Int = 0,
    /** True when no intraday data is available and daily bars are used instead. */
    val isUsingDailyFallback: Boolean = false,
    /**
     * Changes only when the user navigates to a new asset or switches the interval.
     * Used as the remember-key for chart viewport state so zoom/pan survives live refreshes
     * but resets when the displayed asset/timeframe changes.
     */
    val chartStateKey: Long = System.currentTimeMillis(),
    /** Live analysis of the current (latest) bar — why a signal did/didn't fire. */
    val latestBarStatus: LatestBarStatus? = null,
    /** Backtest results for the current settings. */
    val backtestResult: DayTraderBacktestResult? = null
)

class DayTraderViewModel(application: Application) : AndroidViewModel(application) {
    private val localRepo  = LocalDataRepository(application)
    private val cryptoRepo = CryptoRepository()
    private val binanceApi = RetrofitClient.binanceApi
    private val finnhubApi = RetrofitClient.finnhubApi
    private val polygonApi = RetrofitClient.polygonApi
    private val yahooApi   = RetrofitClient.yahooFinanceApi

    private val _state = MutableStateFlow(DayTraderState())
    val state = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var sessionJob: Job? = null
    private var previousVwapSide: Boolean? = null

    init {
        // Load persisted settings
        viewModelScope.launch {
            localRepo.dayTraderSettings.collect { settings ->
                _state.value = _state.value.copy(settings = settings)
            }
        }
    }

    fun loadAsset(symbol: String, assetType: String) {
        val type = when (assetType) {
            "STOCK"     -> AssetType.STOCK
            "COMMODITY" -> AssetType.COMMODITY
            else        -> AssetType.CRYPTO
        }

        // For commodities: resolve ETF ticker (e.g. "GOLD" → "GLD") and display name
        val (resolvedSymbol, resolvedDisplayName) = when (type) {
            AssetType.COMMODITY -> {
                val commodityType = com.tradescreenerai.app.data.model.CommodityType.fromSymbol(symbol)
                val ticker = commodityType?.etfTicker ?: symbol
                ticker to (commodityType?.let { "${it.emoji} ${it.displayName} (${it.etfTicker})" } ?: symbol)
            }
            else -> symbol to symbol
        }

        _state.value = _state.value.copy(
            symbol        = resolvedSymbol,
            displayName   = resolvedDisplayName,
            assetType     = type,
            isLoading     = true,
            error         = null,
            entries       = emptyList(),
            signals       = emptyList(),
            chartStateKey = System.currentTimeMillis()  // reset viewport for new asset
        )

        // Start market session ticker
        startSessionTicker(type)

        // If crypto, try to get display name
        if (type == AssetType.CRYPTO) {
            viewModelScope.launch {
                when (val r = cryptoRepo.getCoinDetail(symbol)) {
                    is Resource.Success -> r.data?.let { crypto ->
                        _state.value = _state.value.copy(
                            displayName = "${crypto.symbol.uppercase()} · ${crypto.name}"
                        )
                    }
                    else -> {}
                }
            }
        }

        startLiveUpdates()
    }

    fun updateInterval(interval: DayTraderInterval) {
        val newSettings = _state.value.settings.copy(interval = interval)
        _state.value = _state.value.copy(
            settings      = newSettings,
            entries       = emptyList(),
            signals       = emptyList(),
            isLoading     = true,
            chartStateKey = System.currentTimeMillis()  // reset viewport for new timeframe
        )
        saveSettings(newSettings)
        startLiveUpdates()
    }

    fun updateRiskiness(level: RiskinessLevel) {
        val newSettings = _state.value.settings.copy(riskiness = level)
        _state.value = _state.value.copy(settings = newSettings)
        saveSettings(newSettings)
        // Re-generate signals with new riskiness
        recalculateSignals()
    }

    fun updateChartType(type: String) {
        val newSettings = _state.value.settings.copy(chartType = type)
        _state.value = _state.value.copy(settings = newSettings)
        saveSettings(newSettings)
    }

    fun toggleIndicator(field: String) {
        val ind = _state.value.settings.indicators
        val updated = when (field) {
            "VWAP"    -> ind.copy(showVWAP = !ind.showVWAP)
            "EMA9"    -> ind.copy(showEMA9 = !ind.showEMA9)
            "EMA20"   -> ind.copy(showEMA20 = !ind.showEMA20)
            "EMA50"   -> ind.copy(showEMA50 = !ind.showEMA50)
            "Volume"  -> ind.copy(showVolume = !ind.showVolume)
            "MACD"    -> ind.copy(showMACD = !ind.showMACD)
            "RSI"     -> ind.copy(showRSI = !ind.showRSI)
            "ATR"     -> ind.copy(showATR = !ind.showATR)
            "Signals" -> ind.copy(showSignals = !ind.showSignals)
            else      -> ind
        }
        val newSettings = _state.value.settings.copy(indicators = updated)
        _state.value = _state.value.copy(settings = newSettings)
        saveSettings(newSettings)
    }

    fun toggleAlert(field: String) {
        val s = _state.value.settings
        val newSettings = when (field) {
            "buy"       -> s.copy(alertOnBuy = !s.alertOnBuy)
            "sell"      -> s.copy(alertOnSell = !s.alertOnSell)
            "vwap"      -> s.copy(alertVWAPCross = !s.alertVWAPCross)
            "volume"    -> s.copy(alertVolumeSpike = !s.alertVolumeSpike)
            "rsi"       -> s.copy(alertRSIExtreme = !s.alertRSIExtreme)
            "open"      -> s.copy(alertMarketOpen = !s.alertMarketOpen)
            "close15m"  -> s.copy(alertMarketClose15m = !s.alertMarketClose15m)
            else        -> s
        }
        _state.value = _state.value.copy(settings = newSettings)
        saveSettings(newSettings)
    }

    fun dismissAlert(index: Int) {
        val current = _state.value.recentAlerts.toMutableList()
        if (index in current.indices) current.removeAt(index)
        _state.value = _state.value.copy(recentAlerts = current)
    }

    fun manualRefresh() {
        startLiveUpdates()
    }

    // ── Live update loop ──────────────────────────────────────────────────────
    private fun startLiveUpdates() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                fetchData()
                val intervalMs = _state.value.settings.interval.refreshMs
                val refreshSec = (intervalMs / 1000).toInt()
                for (sec in refreshSec downTo 1) {
                    _state.value = _state.value.copy(nextRefreshIn = sec)
                    delay(1_000L)
                }
            }
        }
    }

    private fun startSessionTicker(type: AssetType) {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            while (true) {
                val session = DayTraderSignalEngine.detectMarketSession(type)
                _state.value = _state.value.copy(session = session)

                // Market close in 15m alert
                if (_state.value.settings.alertMarketClose15m &&
                    session.type == MarketSessionType.REGULAR &&
                    session.countdownMs in 1..900_000) {
                    val existing = _state.value.recentAlerts
                    if (existing.none { it.type == DayTraderAlertType.MARKET_CLOSE_15M }) {
                        _state.value = _state.value.copy(
                            recentAlerts = existing + DayTraderAlert(
                                DayTraderAlertType.MARKET_CLOSE_15M,
                                "Market closes in ${session.countdownLabel}"
                            )
                        )
                    }
                }

                delay(1_000L) // Update every second for countdown
            }
        }
    }

    private suspend fun fetchData() {
        val st = _state.value
        try {
            var usingDailyFallback = false
            var newEntries = when (st.assetType) {
                AssetType.CRYPTO    -> fetchBinanceKlines(st.symbol, st.settings.interval)
                AssetType.STOCK     -> fetchPolygonCandles(st.symbol, st.settings.interval)
                AssetType.COMMODITY -> fetchPolygonCandles(st.symbol, st.settings.interval)
            }

            // ── Daily-bar fallback when market is closed / intraday data empty ──
            if (newEntries.isEmpty()) {
                val dailyEntries = when (st.assetType) {
                    AssetType.CRYPTO -> fetchDailyBars(getCryptoYahooTicker(st.symbol))
                    else             -> fetchDailyBars(st.symbol)
                }
                if (dailyEntries.isNotEmpty()) {
                    newEntries = dailyEntries
                    usingDailyFallback = true
                }
            }

            if (newEntries.isEmpty()) {
                // Still nothing — keep existing chart data silently if we have it
                if (st.entries.isNotEmpty()) {
                    _state.value = st.copy(
                        isLoading = false,
                        error = null,
                        isUsingDailyFallback = st.isUsingDailyFallback
                    )
                } else {
                    val isMarketClosed = st.session.type == MarketSessionType.CLOSED
                    val msg = when {
                        isMarketClosed -> "🌙 Market closed — no intraday data yet\nTry again when the market opens, or tap Refresh"
                        st.assetType == AssetType.CRYPTO -> "Unable to load chart data — check internet connection"
                        else -> "No intraday data available for this timeframe"
                    }
                    _state.value = st.copy(isLoading = false, error = msg)
                }
                return
            }

            // ── Incremental merge preserves chart viewport across refreshes ──
            val merged = mergeEntries(st.entries, newEntries)

            val td      = if (merged.size >= 26) TechnicalAnalysis.calculateAllIndicators(merged) else TechnicalData()
            val vwap    = TechnicalAnalysis.calculateVWAP(merged)
            val signals = DayTraderSignalEngine.generateSignals(merged, st.settings)
            val newAlerts = DayTraderSignalEngine.detectAlerts(merged, st.settings, previousVwapSide)
            val latestBarStatus = if (merged.size >= 35) DayTraderSignalEngine.analyzeLatestBar(merged, st.settings) else null
            val backtestResult: DayTraderBacktestResult? = if (merged.size >= 40) DayTraderSignalEngine.runBacktest(merged, st.settings) else null

            if (vwap > 0) previousVwapSide = merged.last().close > vwap

            val firstPrice = merged.first().close
            val lastPrice  = merged.last().close
            val change     = lastPrice - firstPrice
            val changePct  = if (firstPrice > 0) (change / firstPrice) * 100 else 0.0

            val allAlerts = (newAlerts + st.recentAlerts).distinctBy { "${it.type}_${it.message}" }.take(5)

            val signalAlerts = mutableListOf<DayTraderAlert>()
            if (signals.isNotEmpty()) {
                val latest = signals.last()
                if (latest.isBuy && st.settings.alertOnBuy) {
                    val existing = allAlerts.any { it.type == DayTraderAlertType.BUY_TRIGGER && it.timestamp > System.currentTimeMillis() - 60_000 }
                    if (!existing) signalAlerts.add(DayTraderAlert(DayTraderAlertType.BUY_TRIGGER, "BUY signal at ${formatPrice(latest.entryPrice)} (${latest.confidence}%)"))
                }
                if (!latest.isBuy && st.settings.alertOnSell) {
                    val existing = allAlerts.any { it.type == DayTraderAlertType.SELL_TRIGGER && it.timestamp > System.currentTimeMillis() - 60_000 }
                    if (!existing) signalAlerts.add(DayTraderAlert(DayTraderAlertType.SELL_TRIGGER, "SELL signal at ${formatPrice(latest.entryPrice)} (${latest.confidence}%)"))
                }
            }

            _state.value = st.copy(
                entries        = merged,
                technicalData  = td,
                signals        = if (usingDailyFallback) emptyList() else signals,
                recentAlerts   = (signalAlerts + allAlerts).take(5),
                currentPrice   = lastPrice,
                priceChange    = change,
                priceChangePct = changePct,
                vwap           = if (usingDailyFallback) 0.0 else vwap,
                isLoading      = false,
                error          = null,
                isUsingDailyFallback = usingDailyFallback,
                lastUpdated    = System.currentTimeMillis(),
                updateCount    = st.updateCount + 1,
                latestBarStatus = if (usingDailyFallback) null else latestBarStatus,
                backtestResult  = if (usingDailyFallback) null else backtestResult
                // chartStateKey intentionally NOT changed — viewport survives refresh
            )
        } catch (e: Exception) {
            _state.value = st.copy(isLoading = false, error = e.message ?: "Failed to fetch data")
        }
    }

    /**
     * Merges [incoming] candles into [existing]:
     *  – Updates the last candle in place (it may still be forming, e.g. the current 1-min bar)
     *  – Appends genuinely new candles (timestamps not yet seen)
     *  – Caps the total list at 600 entries
     *  – Returns the SAME reference if nothing changed to prevent needless recomposition
     */
    private fun mergeEntries(existing: List<ChartEntry>, incoming: List<ChartEntry>): List<ChartEntry> {
        if (existing.isEmpty()) return incoming
        if (incoming.isEmpty()) return existing

        val existingByTs = existing.associateBy { it.timestamp }

        // Update the most-recent candle (it is often still in progress)
        val lastIncoming = incoming.last()
        val baseList: MutableList<ChartEntry> = if (existingByTs.containsKey(lastIncoming.timestamp)) {
            val updated = existing.toMutableList()
            val idx = updated.indexOfLast { it.timestamp == lastIncoming.timestamp }
            if (idx >= 0) updated[idx] = lastIncoming
            updated
        } else existing.toMutableList()

        // Append new candles
        val newCandles = incoming.filter { it.timestamp !in existingByTs }
        if (newCandles.isNotEmpty()) baseList.addAll(newCandles)

        // Cap + early-exit if nothing changed
        val merged = if (baseList.size > 600) baseList.takeLast(600) else baseList
        return if (merged.size == existing.size && merged.last() == existing.last()) existing
               else merged
    }

    // ── Binance klines for crypto ─────────────────────────────────────────────
    private suspend fun fetchBinanceKlines(coinId: String, interval: DayTraderInterval): List<ChartEntry> {
        val binanceSymbol = DayTraderSignalEngine.getBinanceSymbol(coinId) ?: ""

        // 1. Binance (free, best for crypto — may be geo-blocked in some regions)
        val binanceEntries: List<ChartEntry> = if (binanceSymbol.isBlank()) emptyList() else try {
            val klines = binanceApi.getKlines(
                symbol   = binanceSymbol,
                interval = interval.binanceCode,
                limit    = 200
            )
            parseBinanceKlines(klines)
        } catch (_: Exception) { emptyList() }
        if (binanceEntries.isNotEmpty()) return binanceEntries

        // 2. CoinCap fallback
        val coinCapEntries = try { fetchCoinCapData(coinId, interval) } catch (_: Exception) { emptyList() }
        if (coinCapEntries.isNotEmpty()) return coinCapEntries

        // 3. Yahoo Finance crypto fallback (BTC-USD, ETH-USD, etc. — no geo-restriction)
        return fetchYahooIntradayChart(getCryptoYahooTicker(coinId), interval)
    }

    /** Maps CoinGecko coin IDs → Yahoo Finance crypto ticker symbols (e.g. BTC-USD) */
    private fun getCryptoYahooTicker(coinId: String): String {
        val map = mapOf(
            "bitcoin" to "BTC-USD", "ethereum" to "ETH-USD", "binancecoin" to "BNB-USD",
            "solana" to "SOL-USD", "cardano" to "ADA-USD", "ripple" to "XRP-USD",
            "polkadot" to "DOT-USD", "dogecoin" to "DOGE-USD", "avalanche-2" to "AVAX-USD",
            "chainlink" to "LINK-USD", "polygon" to "MATIC-USD", "litecoin" to "LTC-USD",
            "uniswap" to "UNI-USD", "stellar" to "XLM-USD", "cosmos" to "ATOM-USD",
            "near" to "NEAR-USD", "algorand" to "ALGO-USD", "fantom" to "FTM-USD",
            "aave" to "AAVE-USD", "maker" to "MKR-USD", "tron" to "TRX-USD",
            "shiba-inu" to "SHIB-USD", "pepe" to "PEPE-USD", "arbitrum" to "ARB-USD",
            "optimism" to "OP-USD", "sui" to "SUI-USD", "aptos" to "APT-USD",
            "injective-protocol" to "INJ-USD", "render-token" to "RNDR-USD",
            "filecoin" to "FIL-USD"
        )
        val normalized = coinId.lowercase().trim()
        return map[normalized] ?: "${normalized.replace("-", "").uppercase()}-USD"
    }

    private fun parseBinanceKlines(data: JsonArray): List<ChartEntry> {
        return (0 until data.size()).mapNotNull { i ->
            val candle = data[i].asJsonArray
            if (candle.size() < 6) return@mapNotNull null
            ChartEntry(
                timestamp = candle[0].asLong,
                open = candle[1].asString.toDoubleOrNull() ?: return@mapNotNull null,
                high = candle[2].asString.toDoubleOrNull() ?: return@mapNotNull null,
                low = candle[3].asString.toDoubleOrNull() ?: return@mapNotNull null,
                close = candle[4].asString.toDoubleOrNull() ?: return@mapNotNull null,
                volume = candle[5].asString.toDoubleOrNull()?.toLong() ?: 0L
            )
        }
    }

    // ── CoinCap fallback ──────────────────────────────────────────────────────
    private suspend fun fetchCoinCapData(coinId: String, interval: DayTraderInterval): List<ChartEntry> {
        val coinCapApi = RetrofitClient.coinCapApi
        val now = System.currentTimeMillis()
        val hoursBack = when (interval) {
            DayTraderInterval.M1 -> 4
            DayTraderInterval.M5 -> 12
            DayTraderInterval.M15 -> 24
            DayTraderInterval.M30 -> 48
            DayTraderInterval.H1 -> 168
        }
        val start = now - hoursBack * 3600_000L
        return try {
            val response = coinCapApi.getAssetHistory(
                id = coinId,
                interval = interval.coinCapCode,
                start = start,
                end = now
            )
            val dataArr = response.getAsJsonArray("data") ?: return emptyList()
            (0 until dataArr.size()).mapNotNull { i ->
                val pt = dataArr[i].asJsonObject
                val price = pt.get("priceUsd")?.asString?.toDoubleOrNull() ?: return@mapNotNull null
                val time = pt.get("time")?.asLong ?: return@mapNotNull null
                // CoinCap returns single price points; derive synthetic candle
                ChartEntry(time, price, price, price, price, 0L)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Polygon agg bars for stocks (replaces Finnhub candles which require premium) ──
    private suspend fun fetchPolygonCandles(symbol: String, interval: DayTraderInterval): List<ChartEntry> {
        val key = com.tradescreenerai.app.BuildConfig.POLYGON_KEY
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val today = sdf.format(java.util.Date())
        // Use extra days back so weekends still find Friday's data
        val hoursBack = when (interval) {
            DayTraderInterval.M1  -> 72
            DayTraderInterval.M5  -> 72
            DayTraderInterval.M15 -> 120
            DayTraderInterval.M30 -> 168
            DayTraderInterval.H1  -> 336
        }
        val from = sdf.format(java.util.Date(System.currentTimeMillis() - hoursBack * 3_600_000L))
        val (multiplier, timespan) = when (interval) {
            DayTraderInterval.M1  -> 1 to "minute"
            DayTraderInterval.M5  -> 5 to "minute"
            DayTraderInterval.M15 -> 15 to "minute"
            DayTraderInterval.M30 -> 30 to "minute"
            DayTraderInterval.H1  -> 1 to "hour"
        }

        // ── Polygon (primary) ─────────────────────────────────────────────────
        val polygonEntries: List<ChartEntry> = try {
            val response = polygonApi.getAggBars(
                ticker     = symbol.uppercase(),
                multiplier = multiplier,
                timespan   = timespan,
                from       = from,
                to         = today,
                adjusted   = true,
                sort       = "asc",
                limit      = 500,
                apiKey     = key
            )
            val results = response.getAsJsonArray("results")
            if (results == null || results.size() == 0) emptyList()
            else (0 until results.size()).mapNotNull { i ->
                val bar = results[i].asJsonObject
                val t   = bar.get("t")?.asLong ?: return@mapNotNull null
                ChartEntry(
                    timestamp = t,
                    open      = bar.get("o")?.asDouble ?: 0.0,
                    high      = bar.get("h")?.asDouble ?: 0.0,
                    low       = bar.get("l")?.asDouble ?: 0.0,
                    close     = bar.get("c")?.asDouble ?: 0.0,
                    volume    = bar.get("v")?.asLong   ?: 0L
                )
            }
        } catch (_: Exception) { emptyList() }

        if (polygonEntries.isNotEmpty()) return polygonEntries

        // ── Yahoo Finance intraday fallback ───────────────────────────────────
        return fetchYahooIntradayChart(symbol, interval)
    }

    /**
     * Fetches daily OHLCV bars for the past ~60 days.
     * Used as a guaranteed fallback when intraday data is unavailable (market closed, weekend).
     */
    private suspend fun fetchDailyBars(symbol: String): List<ChartEntry> {
        val key = com.tradescreenerai.app.BuildConfig.POLYGON_KEY
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val today = sdf.format(java.util.Date())
        val from  = sdf.format(java.util.Date(System.currentTimeMillis() - 90L * 86_400_000L))

        // ── Polygon daily bars ────────────────────────────────────────────────
        if (key != "YOUR_POLYGON_API_KEY") {
            try {
                val response = polygonApi.getAggBars(
                    ticker     = symbol.uppercase(),
                    multiplier = 1,
                    timespan   = "day",
                    from       = from,
                    to         = today,
                    adjusted   = true,
                    sort       = "asc",
                    limit      = 90,
                    apiKey     = key
                )
                val results = response.getAsJsonArray("results")
                if (results != null && results.size() > 5) {
                    return (0 until results.size()).mapNotNull { i ->
                        val bar = results[i].asJsonObject
                        val t   = bar.get("t")?.asLong ?: return@mapNotNull null
                        ChartEntry(t, bar.get("o")?.asDouble ?: 0.0, bar.get("h")?.asDouble ?: 0.0,
                            bar.get("l")?.asDouble ?: 0.0, bar.get("c")?.asDouble ?: 0.0,
                            bar.get("v")?.asLong ?: 0L)
                    }
                }
            } catch (_: Exception) {}
        }

        // ── Yahoo Finance 3-month daily chart ─────────────────────────────────
        return try {
            val response = yahooApi.getChart(
                symbol   = symbol.uppercase(),
                interval = "1d",
                range    = "3mo"
            )
            parseYahooChart(response)
        } catch (_: Exception) { emptyList() }
    }

    /** Yahoo Finance intraday chart — free, no key, reliable fallback */
    private suspend fun fetchYahooIntradayChart(symbol: String, interval: DayTraderInterval): List<ChartEntry> {
        val (yahooInterval, yahooRange) = when (interval) {
            DayTraderInterval.M1  -> "1m"  to "1d"
            DayTraderInterval.M5  -> "5m"  to "5d"
            DayTraderInterval.M15 -> "15m" to "5d"
            DayTraderInterval.M30 -> "30m" to "1mo"
            DayTraderInterval.H1  -> "1h"  to "1mo"
        }
        return try {
            val response = yahooApi.getChart(
                symbol   = symbol.uppercase(),
                interval = yahooInterval,
                range    = yahooRange
            )
            parseYahooChart(response)
        } catch (_: Exception) { emptyList() }
    }

    private fun parseYahooChart(json: com.google.gson.JsonObject): List<ChartEntry> {
        val result = json.getAsJsonObject("chart")
            ?.getAsJsonArray("result")
            ?.takeIf { it.size() > 0 }
            ?.get(0)?.asJsonObject ?: return emptyList()
        val timestamps = result.getAsJsonArray("timestamp") ?: return emptyList()
        val quote = result.getAsJsonObject("indicators")
            ?.getAsJsonArray("quote")
            ?.takeIf { it.size() > 0 }
            ?.get(0)?.asJsonObject ?: return emptyList()
        val opens   = quote.getAsJsonArray("open")
        val highs   = quote.getAsJsonArray("high")
        val lows    = quote.getAsJsonArray("low")
        val closes  = quote.getAsJsonArray("close")
        val volumes = quote.getAsJsonArray("volume")
        return (0 until timestamps.size()).mapNotNull { i ->
            val ts    = timestamps[i].asLong * 1000L
            val close = closes?.get(i)?.asDouble?.takeIf { !it.isNaN() && it > 0 } ?: return@mapNotNull null
            ChartEntry(
                timestamp = ts,
                open      = opens?.get(i)?.asDouble?.takeIf { !it.isNaN() } ?: close,
                high      = highs?.get(i)?.asDouble?.takeIf { !it.isNaN() } ?: close,
                low       = lows?.get(i)?.asDouble?.takeIf { !it.isNaN()  } ?: close,
                close     = close,
                volume    = volumes?.get(i)?.asLong ?: 0L
            )
        }
    }

    // ── Finnhub candles kept as fallback reference (not currently used) ────────
    @Suppress("unused")
    private suspend fun fetchFinnhubCandles(symbol: String, interval: DayTraderInterval): List<ChartEntry> {
        val finnhubKey = com.tradescreenerai.app.BuildConfig.FINNHUB_KEY
        val now = System.currentTimeMillis() / 1000
        val hoursBack = when (interval) {
            DayTraderInterval.M1 -> 4
            DayTraderInterval.M5 -> 12
            DayTraderInterval.M15 -> 24
            DayTraderInterval.M30 -> 48
            DayTraderInterval.H1 -> 168
        }
        val from = now - hoursBack * 3600

        return try {
            val response = finnhubApi.getCandles(
                symbol = symbol.uppercase(),
                resolution = interval.finnhubCode,
                from = from,
                to = now,
                token = finnhubKey
            )

            val status = response.get("s")?.asString
            if (status != "ok") return emptyList()

            val timestamps = response.getAsJsonArray("t") ?: return emptyList()
            val opens = response.getAsJsonArray("o") ?: return emptyList()
            val highs = response.getAsJsonArray("h") ?: return emptyList()
            val lows = response.getAsJsonArray("l") ?: return emptyList()
            val closes = response.getAsJsonArray("c") ?: return emptyList()
            val volumes = response.getAsJsonArray("v") ?: return emptyList()

            (0 until timestamps.size()).map { i ->
                ChartEntry(
                    timestamp = timestamps[i].asLong * 1000,
                    open = opens[i].asDouble,
                    high = highs[i].asDouble,
                    low = lows[i].asDouble,
                    close = closes[i].asDouble,
                    volume = volumes[i].asLong
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun recalculateSignals() {
        val st = _state.value
        if (st.entries.size < 35) return
        val signals         = DayTraderSignalEngine.generateSignals(st.entries, st.settings)
        val latestBarStatus = DayTraderSignalEngine.analyzeLatestBar(st.entries, st.settings)
        val backtestResult: DayTraderBacktestResult? = if (st.entries.size >= 40) DayTraderSignalEngine.runBacktest(st.entries, st.settings) else null
        _state.value = st.copy(
            signals          = signals,
            latestBarStatus  = latestBarStatus,
            backtestResult   = backtestResult
        )
    }

    private fun saveSettings(settings: DayTraderSettings) {
        viewModelScope.launch { localRepo.saveDayTraderSettings(settings) }
    }

    private fun formatPrice(price: Double): String = when {
        price >= 1_000 -> "$${"%,.0f".format(price)}"
        price >= 1.0 -> "$${"%,.2f".format(price)}"
        price >= 0.01 -> "$${"%,.4f".format(price)}"
        else -> "$${"%,.6f".format(price)}"
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
        sessionJob?.cancel()
    }
}

