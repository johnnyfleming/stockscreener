package com.tradescreenerai.app.ui.screens.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.repository.*
import com.tradescreenerai.app.domain.AiInsightEngine
import com.tradescreenerai.app.domain.BacktestEngine
import com.tradescreenerai.app.domain.ConfluenceScoreEngine
import com.tradescreenerai.app.domain.SmartSignalEngine
import com.tradescreenerai.app.domain.TechnicalAnalysis
import com.tradescreenerai.app.util.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CryptoDetailState(
    val coinId: String = "",
    val crypto: Crypto? = null,
    val priceHistory: List<PricePoint> = emptyList(),
    val chartEntries: List<ChartEntry> = emptyList(),
    val technicalData: TechnicalData = TechnicalData(),
    val smartScore: SmartSignalScore = SmartSignalScore(),
    val aiInsight: String = "",
    val indicatorVisibility: IndicatorVisibility = IndicatorVisibility(),
    val sentiment: SentimentData = SentimentData(),
    val relatedNews: List<NewsArticle> = emptyList(),
    val selectedTimeRange: String = "1M",
    val selectedChartType: String = "Line",
    val isLoading: Boolean = true,
    val isInWatchlist: Boolean = false,
    val error: String? = null,
    val buySellSignals: List<BuySellSignal> = emptyList(),
    val supertrendLine: List<SupertrendLinePoint> = emptyList(),
    val candlePatterns: List<CandlePattern> = emptyList(),
    val patternPrediction: PredictionData = PredictionData(),
    // ── Decision-focused features ────────────────────────────────────────
    val confluenceScore: ConfluenceResult = ConfluenceResult(),
    val riskOverlay: RiskOverlay = RiskOverlay(),
    val strategy: StrategyType? = null,
    val timeframeTrends: List<TimeframeTrend> = emptyList(),
    val backtestResult: BacktestResult = BacktestResult()
)

class CryptoDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val cryptoRepo = CryptoRepository()
    private val newsRepo   = NewsRepository()
    private val localRepo  = LocalDataRepository(application)

    private val _state = MutableStateFlow(CryptoDetailState())
    val state = _state.asStateFlow()

    /** Tracks the current chart-load job so we can cancel it on a new time-range tap. */
    private var chartLoadJob: Job? = null
    private var watchlistJob: Job? = null
    private var latestChartRequestId: Long = 0L

    fun loadCoin(id: String) {
        viewModelScope.launch {
            chartLoadJob?.cancel()
            _state.value = CryptoDetailState(coinId = id, isLoading = true)

            // Run coin-detail, chart and news in parallel; wait for all three.
            coroutineScope {
                launch {
                    when (val result = cryptoRepo.getCoinDetail(id)) {
                        is Resource.Success -> _state.value = _state.value.copy(crypto = result.data)
                        is Resource.Error   -> _state.value = _state.value.copy(error = result.message)
                        else -> {}
                    }
                }
                chartLoadJob = launch { loadChart(id, "30", nextChartRequestId()) }
                launch {
                    when (val result = newsRepo.searchNews(id)) {
                        is Resource.Success -> _state.value = _state.value.copy(
                            relatedNews = result.data?.take(5) ?: emptyList()
                        )
                        else -> {}
                    }
                }
            }

            // isLoading cleared only AFTER all three coroutines above finish.
            _state.value = _state.value.copy(isLoading = false)

            // Watchlist is a Flow — observe it continuously in the background.
            watchlistJob?.cancel()
            watchlistJob = launch {
                localRepo.watchlist.collect { items ->
                    _state.value = _state.value.copy(
                        isInWatchlist = items.any { it.symbol == id && it.type == AssetType.CRYPTO }
                    )
                }
            }
        }
    }

    fun selectTimeRange(range: String) {
        val currentState = _state.value
        if (range == currentState.selectedTimeRange &&
            (currentState.priceHistory.isNotEmpty() || currentState.chartEntries.isNotEmpty())) {
            return
        }

        _state.value = _state.value.copy(selectedTimeRange = range)
        val days = when (range) {
            "1D" -> "1"; "1W" -> "7"; "1M" -> "30"
            "3M" -> "90"; "1Y" -> "365"; "ALL" -> "max"
            else -> "30"
        }
        // Use crypto.id first; fall back to the stored coinId (set by loadCoin)
        // so that switching ranges still works even if the detail fetch failed.
        val id = _state.value.crypto?.id
            ?: _state.value.coinId.takeIf { it.isNotEmpty() }
            ?: return
        chartLoadJob?.cancel()
        chartLoadJob = viewModelScope.launch { loadChart(id, days, nextChartRequestId()) }
    }

    fun selectChartType(type: String) {
        _state.value = _state.value.copy(selectedChartType = type)
    }

    fun toggleIndicator(field: String) {
        val v = _state.value.indicatorVisibility
        _state.value = _state.value.copy(
            indicatorVisibility = when (field) {
                "EMA20"  -> v.copy(showEMA20          = !v.showEMA20)
                "EMA50"  -> v.copy(showEMA50          = !v.showEMA50)
                "EMA200" -> v.copy(showEMA200         = !v.showEMA200)
                "SMA20"  -> v.copy(showSMA20          = !v.showSMA20)
                "MACD"   -> v.copy(showMACD           = !v.showMACD)
                "RSI"    -> v.copy(showRSI            = !v.showRSI)
                "BB"     -> v.copy(showBollingerBands = !v.showBollingerBands)
                "VWAP"   -> v.copy(showVWAP           = !v.showVWAP)
                "ATR"    -> v.copy(showATR            = !v.showATR)
                "ADX"    -> v.copy(showADX            = !v.showADX)
                "Stoch"  -> v.copy(showStochastic     = !v.showStochastic)
                "OBV"    -> v.copy(showOBV            = !v.showOBV)
                "Volume" -> v.copy(showVolume         = !v.showVolume)
                else     -> v
            }
        )
    }

    private fun applyIndicators(entries: List<ChartEntry>) {
        val td       = TechnicalAnalysis.calculateAllIndicators(entries)
        val smart    = SmartSignalEngine.calculate(entries, td)
        val insight  = AiInsightEngine.generateInsight(td, smart, entries.lastOrNull()?.close ?: 0.0)
        val patterns = TechnicalAnalysis.detectCandlePatterns(entries)
        val predict  = TechnicalAnalysis.buildPatternPrediction(entries, patterns)

        // Decision-focused features
        val confluence = ConfluenceScoreEngine.calculate(entries, td)
        val strategy   = ConfluenceScoreEngine.classifyStrategy(entries, td)
        val isBuy      = confluence.score >= 50
        val risk       = ConfluenceScoreEngine.calculateRiskOverlay(entries, isBuy)
        val tfTrends   = ConfluenceScoreEngine.multiTimeframeTrends(entries)
        val backtest   = BacktestEngine.run(entries)

        _state.value = _state.value.copy(
            technicalData     = td,
            smartScore        = smart,
            aiInsight         = insight,
            buySellSignals    = TechnicalAnalysis.calculateMultiSignals(entries),
            supertrendLine    = TechnicalAnalysis.calculateSupertrendLine(entries),
            candlePatterns    = patterns,
            patternPrediction = predict,
            confluenceScore   = confluence,
            riskOverlay       = risk,
            strategy          = strategy,
            timeframeTrends   = tfTrends,
            backtestResult    = backtest
        )
    }

    fun retryLoad(id: String) = loadCoin(id)

    fun toggleWatchlist() {
        val crypto = _state.value.crypto ?: return
        viewModelScope.launch {
            if (_state.value.isInWatchlist) {
                localRepo.removeFromWatchlist(crypto.id, AssetType.CRYPTO)
            } else {
                localRepo.addToWatchlist(
                    WatchlistItem(symbol = crypto.id, name = crypto.name, type = AssetType.CRYPTO)
                )
            }
        }
    }

    /**
     * Loads both the line-chart price points AND real OHLC candles.
     * The OHLC data comes from CoinGecko's dedicated /ohlc endpoint which
     * returns true open/high/low/close per candle — not synthetic values.
     * If the OHLC call fails (e.g. 429 rate-limit) we fall back to deriving
     * approximate candles from the hourly market-chart data.
     */
    private fun nextChartRequestId(): Long = ++latestChartRequestId

    private fun isLatestChartRequest(requestId: Long): Boolean = requestId == latestChartRequestId

    private suspend fun loadChart(id: String, days: String, requestId: Long) {
        coroutineScope {
            launch {
                when (val result = cryptoRepo.getMarketChart(id, days)) {
                    is Resource.Success -> {
                        val points = result.data ?: emptyList()
                        if (!isLatestChartRequest(requestId)) return@launch
                        _state.value = _state.value.copy(priceHistory = points)
                        if (_state.value.chartEntries.isEmpty() && points.size > 1) {
                            _state.value = _state.value.copy(chartEntries = buildFallbackCandles(points))
                        }
                    }
                    else -> {}
                }
            }
            launch {
                when (val result = cryptoRepo.getCoinOhlcData(id, days)) {
                    is Resource.Success -> {
                        val entries = result.data ?: emptyList()
                        if (!isLatestChartRequest(requestId)) return@launch
                        if (entries.isNotEmpty()) {
                            _state.value = _state.value.copy(chartEntries = entries)
                            if (entries.size > 26) applyIndicators(entries)
                        }
                    }
                    else -> {
                        if (!isLatestChartRequest(requestId)) return@launch
                        val existing = _state.value.chartEntries
                        if (existing.size > 26) applyIndicators(existing)
                    }
                }
            }
        }
    }

    /** Derives daily candles from a list of raw price points (line-chart data). */
    private fun buildFallbackCandles(points: List<PricePoint>): List<ChartEntry> {
        if (points.isEmpty()) return emptyList()
        // Group by calendar-day bucket (86 400 000 ms)
        val dayMs = 86_400_000L
        return points
            .groupBy { it.timestamp / dayMs }
            .entries
            .sortedBy { it.key }
            .map { (dayKey, pts) ->
                val open  = pts.first().price
                val close = pts.last().price
                val high  = pts.maxOf { it.price }
                val low   = pts.minOf { it.price }
                ChartEntry(
                    timestamp = dayKey * dayMs,
                    open      = open,
                    high      = high,
                    low       = low,
                    close     = close,
                    volume    = 0L
                )
            }
    }
}
