package com.tradescreenerai.app.ui.screens.commodities

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.repository.CommodityRepository
import com.tradescreenerai.app.data.repository.LocalDataRepository
import com.tradescreenerai.app.data.repository.NewsRepository
import com.tradescreenerai.app.domain.*
import com.tradescreenerai.app.util.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CommodityDetailState(
    val commodity: Commodity? = null,
    val commodityType: CommodityType? = null,
    val chartEntries: List<ChartEntry> = emptyList(),
    val technicalData: TechnicalData = TechnicalData(),
    val smartScore: SmartSignalScore = SmartSignalScore(),
    val aiInsight: String = "",
    val quickInsight: QuickInsightData = QuickInsightData(),
    val indicatorVisibility: IndicatorVisibility = IndicatorVisibility(),
    val relatedNews: List<NewsArticle> = emptyList(),
    val selectedTimeRange: String = "1Y",
    val selectedChartType: String = "Candle",
    val isLoading: Boolean = true,
    val isInWatchlist: Boolean = false,
    val error: String? = null,
    val buySellSignals: List<BuySellSignal> = emptyList(),
    val supertrendLine: List<SupertrendLinePoint> = emptyList(),
    val confluenceZones: List<ConfluenceZone> = emptyList(),
    val candlePatterns: List<CandlePattern> = emptyList(),
    val patternPrediction: PredictionData = PredictionData(),
    val confluenceScore: ConfluenceResult = ConfluenceResult(),
    val riskOverlay: RiskOverlay = RiskOverlay(),
    val strategy: StrategyType? = null,
    val timeframeTrends: List<TimeframeTrend> = emptyList(),
    val backtestResult: BacktestResult = BacktestResult(),
    val seasonalContext: String = "",
    val beginnerInsight: BeginnerInsight = BeginnerInsight()
)

class CommodityDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val commodityRepo = CommodityRepository()
    private val newsRepo = NewsRepository()
    private val localRepo = LocalDataRepository(application)

    private val _state = MutableStateFlow(CommodityDetailState())
    val state = _state.asStateFlow()

    private var currentTypeStr = ""

    fun loadCommodity(typeStr: String) {
        currentTypeStr = typeStr
        val type = CommodityType.fromSymbol(typeStr) ?: return

        _state.value = _state.value.copy(
            isLoading = true,
            error = null,
            commodityType = type,
            seasonalContext = CommodityAnalysisEngine.getSeasonalContext(type)
        )

        viewModelScope.launch {
            // Fetch live quote
            launch {
                when (val result = commodityRepo.getCommodityQuote(type)) {
                    is Resource.Success -> result.data?.let { commodity ->
                        // Only apply if we actually got a real price — avoids overwriting with $0
                        if (commodity.price > 0) {
                            _state.value = _state.value.copy(
                                commodity = commodity,
                                beginnerInsight = BeginnerInsightEngine.generateCommodityInsight(commodity)
                            )
                        }
                    }
                    else -> {}
                }
            }

            // Fetch chart history and run all the same engines as StockDetailViewModel
            launch { loadChartForRange(_state.value.selectedTimeRange) }

            // Fetch commodity-related news via NewsAPI (filter client-side by commodity name)
            launch {
                try {
                    when (val result = newsRepo.getMarketNews()) {
                        is Resource.Success -> {
                            val filtered = result.data?.filter { article ->
                                val keywords = listOf(type.displayName, type.name, type.category.displayName)
                                keywords.any { kw ->
                                    article.title.contains(kw, ignoreCase = true) ||
                                    article.description.contains(kw, ignoreCase = true)
                                }
                            }?.take(5) ?: emptyList()
                            // Fall back to all market news if no commodity-specific articles
                            val news = filtered.ifEmpty { result.data?.take(5) ?: emptyList() }
                            _state.value = _state.value.copy(relatedNews = news)
                        }
                        else -> {}
                    }
                } catch (_: Exception) {}
            }

            // Watchlist
            launch {
                localRepo.watchlist.collect { items ->
                    _state.value = _state.value.copy(
                        isInWatchlist = items.any { it.symbol == type.name && it.type == AssetType.COMMODITY }
                    )
                }
            }

            _state.value = _state.value.copy(isLoading = false)
        }
    }

    fun selectTimeRange(range: String) {
        _state.value = _state.value.copy(selectedTimeRange = range)
        if (currentTypeStr.isNotBlank()) {
            viewModelScope.launch { loadChartForRange(range) }
        }
    }

    private suspend fun loadChartForRange(range: String) {
        val type = _state.value.commodityType ?: return
        // Use monthly data for longer ranges (saves API calls), daily for shorter
        val interval = when (range) {
            "1D", "5D", "1W", "1M", "3M" -> "daily"
            else -> "monthly"
        }

        when (val result = commodityRepo.getCommodityHistory(type, interval)) {
            is Resource.Success -> {
                val allEntries = result.data ?: emptyList()
                val entries = trimToRange(allEntries, range)

                if (entries.size > 26) {
                    val td = TechnicalAnalysis.calculateAllIndicators(entries)
                    val smart = SmartSignalEngine.calculate(entries, td)
                    val currentPrice = entries.lastOrNull()?.close ?: 0.0
                    val insight = AiInsightEngine.generateInsight(td, smart, currentPrice)
                    val quick = QuickInsightEngine.generate(td, currentPrice, entries)
                    val confluenceZ = TechnicalAnalysis.calculateConfluenceZones(entries)
                    val patterns = TechnicalAnalysis.detectCandlePatterns(entries)
                    val prediction = TechnicalAnalysis.buildPatternPrediction(entries, patterns)
                    val confluence = ConfluenceScoreEngine.calculate(entries, td)
                    val strategy = ConfluenceScoreEngine.classifyStrategy(entries, td)
                    val isBuy = confluence.score >= 50
                    val risk = ConfluenceScoreEngine.calculateRiskOverlay(entries, isBuy)
                    val tfTrends = ConfluenceScoreEngine.multiTimeframeTrends(entries)
                    val backtest = BacktestEngine.run(entries)

                    // Back-fill commodity price from chart if quote API returned $0
                    val existingCommodity = _state.value.commodity
                    val effectiveCommodity = when {
                        existingCommodity != null && existingCommodity.price > 0 -> existingCommodity
                        currentPrice > 0 -> {
                            val prev = entries.getOrNull(entries.size - 2)?.close ?: currentPrice
                            (existingCommodity ?: Commodity(type = type)).copy(
                                price     = currentPrice,
                                change    = currentPrice - prev,
                                changePct = if (prev > 0) ((currentPrice - prev) / prev) * 100.0 else 0.0
                            )
                        }
                        else -> existingCommodity
                    }

                    val beginnerInsight = if (effectiveCommodity != null)
                        BeginnerInsightEngine.generateCommodityInsight(effectiveCommodity, td)
                    else _state.value.beginnerInsight

                    _state.value = _state.value.copy(
                        commodity = effectiveCommodity,
                        chartEntries = entries,
                        technicalData = td,
                        smartScore = smart,
                        aiInsight = insight,
                        quickInsight = quick,
                        confluenceZones = confluenceZ,
                        buySellSignals = TechnicalAnalysis.calculateMultiSignals(entries),
                        supertrendLine = TechnicalAnalysis.calculateSupertrendLine(entries),
                        candlePatterns = patterns,
                        patternPrediction = prediction,
                        confluenceScore = confluence,
                        riskOverlay = risk,
                        strategy = strategy,
                        timeframeTrends = tfTrends,
                        backtestResult = backtest,
                        beginnerInsight = beginnerInsight,
                        isLoading = false
                    )
                } else {
                    // Even with few entries, try to back-fill price from chart
                    val chartPrice = entries.lastOrNull()?.close ?: 0.0
                    val existingCommodity = _state.value.commodity
                    val effectiveCommodity = when {
                        existingCommodity != null && existingCommodity.price > 0 -> existingCommodity
                        chartPrice > 0 -> (existingCommodity ?: Commodity(type = type)).copy(price = chartPrice)
                        else -> existingCommodity
                    }
                    _state.value = _state.value.copy(commodity = effectiveCommodity, chartEntries = entries, isLoading = false)
                }
            }
            is Resource.Error -> {
                _state.value = _state.value.copy(isLoading = false, error = result.message)
            }
            else -> {}
        }
    }

    fun toggleIndicator(field: String) {
        val v = _state.value.indicatorVisibility
        _state.value = _state.value.copy(
            indicatorVisibility = when (field) {
                "EMA20"    -> v.copy(showEMA20          = !v.showEMA20)
                "EMA50"    -> v.copy(showEMA50          = !v.showEMA50)
                "EMA200"   -> v.copy(showEMA200         = !v.showEMA200)
                "SMA20"    -> v.copy(showSMA20          = !v.showSMA20)
                "MACD"     -> v.copy(showMACD           = !v.showMACD)
                "RSI"      -> v.copy(showRSI            = !v.showRSI)
                "BB"       -> v.copy(showBollingerBands = !v.showBollingerBands)
                "VWAP"     -> v.copy(showVWAP           = !v.showVWAP)
                "ATR"      -> v.copy(showATR            = !v.showATR)
                "ADX"      -> v.copy(showADX            = !v.showADX)
                "Stoch"      -> v.copy(showStochastic   = !v.showStochastic)
                "OBV"        -> v.copy(showOBV          = !v.showOBV)
                "Volume"     -> v.copy(showVolume       = !v.showVolume)
                "Confluence" -> v.copy(showConfluenceZones = !v.showConfluenceZones)
                "SigProgress"-> v.copy(showSignalProgress  = !v.showSignalProgress)
                else -> v
            }
        )
    }

    fun selectChartType(type: String) {
        _state.value = _state.value.copy(selectedChartType = type)
    }

    fun toggleWatchlist() {
        val type = _state.value.commodityType ?: return
        val commodity = _state.value.commodity ?: return
        viewModelScope.launch {
            if (_state.value.isInWatchlist) {
                localRepo.removeFromWatchlist(type.name, AssetType.COMMODITY)
            } else {
                localRepo.addToWatchlist(
                    WatchlistItem(symbol = type.name, name = commodity.name, type = AssetType.COMMODITY)
                )
            }
        }
    }

    private fun trimToRange(entries: List<ChartEntry>, range: String): List<ChartEntry> {
        if (entries.isEmpty()) return entries
        val now = System.currentTimeMillis()
        val cutoff = when (range) {
            "1D"  -> now - 1L  * 86_400_000L
            "5D"  -> now - 5L  * 86_400_000L
            "1W"  -> now - 7L  * 86_400_000L
            "1M"  -> now - 30L * 86_400_000L
            "3M"  -> now - 90L * 86_400_000L
            "6M"  -> now - 180L* 86_400_000L
            "1Y"  -> now - 365L* 86_400_000L
            "5Y"  -> now - 5L  * 365L * 86_400_000L
            else  -> 0L
        }
        return entries.filter { it.timestamp >= cutoff }.ifEmpty { entries }
    }
}
