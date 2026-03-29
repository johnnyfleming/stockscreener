package com.tradescreenerai.app.ui.screens.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.repository.*
import com.tradescreenerai.app.domain.AiInsightEngine
import com.tradescreenerai.app.domain.BacktestEngine
import com.tradescreenerai.app.domain.ConfluenceScoreEngine
import com.tradescreenerai.app.domain.QuickInsightEngine
import com.tradescreenerai.app.domain.SmartSignalEngine
import com.tradescreenerai.app.domain.TechnicalAnalysis
import com.tradescreenerai.app.util.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StockDetailState(
    val stock: Stock? = null,
    val chartEntries: List<ChartEntry> = emptyList(),
    val technicalData: TechnicalData = TechnicalData(),
    val smartScore: SmartSignalScore = SmartSignalScore(),
    val aiInsight: String = "",
    val quickInsight: QuickInsightData = QuickInsightData(),
    val indicatorVisibility: IndicatorVisibility = IndicatorVisibility(),
    val sentiment: SentimentData = SentimentData(),
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
    // ── Currency conversion ──────────────────────────────────────────────
    val displayCurrency: String = "USD",
    val currencyRate: Double = 1.0,
    val currencySymbol: String = "$",
    // ── Professional Chart Tools ─────────────────────────────────────────
    val selectedChartTool: ChartTool = ChartTool.NONE,
    val drawnObjects: List<DrawnObject> = emptyList(),
    /** Index of the hovered/scrubbed candle — shared with drawing tools. */
    val hoveredIndex: Int = -1,
    val hoveredPrice: Double = 0.0,
    /** Anchor point 1 for 2-tap tools (TrendLine, Fibonacci). */
    val drawAnchor1: Pair<Int, Double>? = null,
    /** True while waiting for the second tap. */
    val waitingForAnchor2: Boolean = false,
    /** Show Long/Short position entry dialog. */
    val showPositionDialog: Boolean = false,
    val positionIsLong: Boolean = true,
    val showFibDialog: Boolean = false,
    val showHLineDialog: Boolean = false
)

// Approximate exchange rates vs USD – refreshed from open.er-api.com at runtime
val CURRENCY_SYMBOLS = mapOf(
    "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥",
    "CAD" to "C$", "AUD" to "A$", "CHF" to "Fr", "CNY" to "¥",
    "INR" to "₹", "KRW" to "₩", "BRL" to "R$", "MXN" to "MX$",
    "SGD" to "S$", "HKD" to "HK$", "NOK" to "kr", "SEK" to "kr"
)

class StockDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val stockRepo = StockRepository()
    private val newsRepo  = NewsRepository()
    private val localRepo = LocalDataRepository(application)

    private val _state = MutableStateFlow(StockDetailState())
    val state = _state.asStateFlow()

    private var currentSymbol = ""
    // Live exchange rates cache (base = USD)
    private val exchangeRates = mutableMapOf("USD" to 1.0)

    fun loadStock(symbol: String) {
        currentSymbol = symbol
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            // ── 1. Await the quote first so price is shown immediately ──────────
            when (val result = stockRepo.getQuote(symbol)) {
                is Resource.Success -> _state.value = _state.value.copy(
                    stock     = result.data,
                    isLoading = false
                )
                is Resource.Error   -> _state.value = _state.value.copy(
                    error     = result.message,
                    isLoading = false
                )
                else -> _state.value = _state.value.copy(isLoading = false)
            }

            // ── 2. Everything else loads in parallel without blocking UI ────────
            launch {
                when (val result = stockRepo.getCompanyOverview(symbol)) {
                    is Resource.Success -> result.data?.let {
                        _state.value = _state.value.copy(stock = it)
                    }
                    else -> {}
                }
            }

            launch { loadChartForRange("1Y") }

            launch {
                when (val result = stockRepo.getSocialSentiment(symbol)) {
                    is Resource.Success -> result.data?.let {
                        _state.value = _state.value.copy(sentiment = it)
                    }
                    else -> {}
                }
            }

            launch {
                when (val result = newsRepo.getCompanyNews(symbol)) {
                    is Resource.Success -> _state.value = _state.value.copy(
                        relatedNews = result.data?.take(5) ?: emptyList()
                    )
                    else -> {}
                }
            }

            launch {
                localRepo.watchlist.collect { items ->
                    _state.value = _state.value.copy(
                        isInWatchlist = items.any { it.symbol == symbol && it.type == AssetType.STOCK }
                    )
                }
            }

            // ── 3. Fetch live exchange rates ────────────────────────────────────
            launch { fetchExchangeRates() }
        }
    }

    fun selectTimeRange(range: String) {
        _state.value = _state.value.copy(selectedTimeRange = range)
        if (currentSymbol.isNotBlank()) {
            viewModelScope.launch { loadChartForRange(range) }
        }
    }

    fun setCurrency(currency: String) {
        val rate   = exchangeRates[currency] ?: return
        val symbol = CURRENCY_SYMBOLS[currency] ?: currency
        _state.value = _state.value.copy(
            displayCurrency = currency,
            currencyRate    = rate,
            currencySymbol  = symbol
        )
    }

    private suspend fun fetchExchangeRates() = withContext(Dispatchers.IO) {
        try {
            val resp  = okhttp3.OkHttpClient().newCall(
                okhttp3.Request.Builder()
                    .url("https://open.er-api.com/v6/latest/USD")
                    .build()
            ).execute()
            val body = resp.body?.string() ?: return@withContext
            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
            val rates = json.getAsJsonObject("rates") ?: return@withContext
            for ((k, v) in rates.entrySet()) {
                exchangeRates[k] = v.asDouble
            }
        } catch (_: Exception) {
            // Use hardcoded fallback rates if API fails
            exchangeRates.putAll(mapOf(
                "EUR" to 0.92, "GBP" to 0.79, "JPY" to 151.5, "CAD" to 1.37,
                "AUD" to 1.56, "CHF" to 0.91, "CNY" to 7.24, "INR" to 83.4,
                "KRW" to 1340.0, "BRL" to 4.98, "MXN" to 17.1,
                "SGD" to 1.35, "HKD" to 7.82, "NOK" to 10.7, "SEK" to 10.5
            ))
        }
    }

    private suspend fun loadChartForRange(range: String) {
        val days = when (range) {
            "1D"  -> 2
            "5D"  -> 5
            "1W"  -> 7
            "1M"  -> 30
            "3M"  -> 90
            "6M"  -> 180
            "1Y"  -> 365
            "5Y"  -> 1825
            "ALL" -> 99999
            else  -> 365
        }
        when (val result = stockRepo.getDailyTimeSeries(currentSymbol, days)) {
            is Resource.Success -> {
                val entries = result.data ?: emptyList()
                // Always update chart entries so the graph shows
                _state.value = _state.value.copy(chartEntries = entries)
                if (entries.size > 20) {
                    val td           = TechnicalAnalysis.calculateAllIndicators(entries)
                    val smart        = SmartSignalEngine.calculate(entries, td)
                    val currentPrice = entries.lastOrNull()?.close ?: 0.0
                    val insight      = AiInsightEngine.generateInsight(td, smart, currentPrice)
                    val quick        = QuickInsightEngine.generate(td, currentPrice, entries)
                    val confluenceZ  = TechnicalAnalysis.calculateConfluenceZones(entries)
                    val patterns     = TechnicalAnalysis.detectCandlePatterns(entries)
                    val prediction   = TechnicalAnalysis.buildPatternPrediction(entries, patterns)
                    val confluence   = ConfluenceScoreEngine.calculate(entries, td)
                    val strategy     = ConfluenceScoreEngine.classifyStrategy(entries, td)
                    val isBuy        = confluence.score >= 50
                    val risk         = ConfluenceScoreEngine.calculateRiskOverlay(entries, isBuy)
                    val tfTrends     = ConfluenceScoreEngine.multiTimeframeTrends(entries)
                    val backtest     = BacktestEngine.run(entries)

                    _state.value = _state.value.copy(
                        technicalData     = td,
                        smartScore        = smart,
                        aiInsight         = insight,
                        quickInsight      = quick,
                        confluenceZones   = confluenceZ,
                        buySellSignals    = TechnicalAnalysis.calculateMultiSignals(entries),
                        supertrendLine    = TechnicalAnalysis.calculateSupertrendLine(entries),
                        candlePatterns    = patterns,
                        patternPrediction = prediction,
                        confluenceScore   = confluence,
                        riskOverlay       = risk,
                        strategy          = strategy,
                        timeframeTrends   = tfTrends,
                        backtestResult    = backtest,
                        sentiment         = deriveSentimentFromTechnicals(td, _state.value.sentiment)
                    )
                    // If stock price is still 0 or stock is null, use chart's last close
                    val st = _state.value.stock
                    val lastEntry = entries.last()
                    val lastClose = lastEntry.close
                    val prevClose = if (entries.size > 1) entries[entries.size - 2].close else lastClose
                    if (st == null) {
                        // Quote API failed entirely – create a minimal Stock from chart data
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error     = null,
                            stock     = Stock(
                                symbol        = currentSymbol,
                                name          = currentSymbol,
                                price         = lastClose,
                                change        = lastClose - prevClose,
                                changePercent = if (prevClose > 0) ((lastClose - prevClose) / prevClose) * 100 else 0.0,
                                previousClose = prevClose,
                                high          = lastEntry.high.takeIf { it > 0 } ?: lastClose,
                                low           = lastEntry.low.takeIf  { it > 0 } ?: lastClose,
                                open          = lastEntry.open.takeIf { it > 0 } ?: lastClose,
                                volume        = lastEntry.volume
                            )
                        )
                    } else if (st.price <= 0) {
                        _state.value = _state.value.copy(
                            stock = st.copy(
                                price         = lastClose,
                                previousClose = prevClose,
                                high          = lastEntry.high.takeIf { it > 0 } ?: lastClose,
                                low           = lastEntry.low.takeIf  { it > 0 } ?: lastClose,
                                open          = lastEntry.open.takeIf { it > 0 } ?: lastClose
                            )
                        )
                    }
                }
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
                "Stoch"      -> v.copy(showStochastic     = !v.showStochastic)
                "OBV"        -> v.copy(showOBV            = !v.showOBV)
                "Volume"     -> v.copy(showVolume         = !v.showVolume)
                "Confluence" -> v.copy(showConfluenceZones= !v.showConfluenceZones)
                "SigProgress"-> v.copy(showSignalProgress = !v.showSignalProgress)
                else -> v
            }
        )
    }

    fun selectChartType(type: String) {
        _state.value = _state.value.copy(selectedChartType = type)
    }

    // ── Professional Chart Drawing Tools ─────────────────────────────────────

    fun selectChartTool(tool: ChartTool) {
        _state.value = _state.value.copy(
            selectedChartTool = tool,
            drawAnchor1 = null,
            waitingForAnchor2 = false,
            showPositionDialog = tool == ChartTool.LONG_POSITION || tool == ChartTool.SHORT_POSITION,
            positionIsLong = tool == ChartTool.LONG_POSITION,
            showFibDialog = tool == ChartTool.FIBONACCI,
            showHLineDialog = tool == ChartTool.HORIZONTAL_LINE
        )
    }

    /** Called by the chart when the user scrubs to a candle. */
    fun onChartHover(index: Int, price: Double) {
        _state.value = _state.value.copy(hoveredIndex = index, hoveredPrice = price)
    }

    /** Set anchor point 1 for two-tap tools (TrendLine, Fibonacci). */
    fun setAnchorPoint1() {
        val idx = _state.value.hoveredIndex.takeIf { it >= 0 } ?: return
        val price = _state.value.hoveredPrice.takeIf { it > 0 } ?: return
        _state.value = _state.value.copy(drawAnchor1 = Pair(idx, price), waitingForAnchor2 = true)
    }

    /** Set anchor point 2 and complete the drawn object. */
    fun setAnchorPoint2() {
        val a1 = _state.value.drawAnchor1 ?: return
        val idx = _state.value.hoveredIndex.takeIf { it >= 0 } ?: return
        val price = _state.value.hoveredPrice.takeIf { it > 0 } ?: return
        val obj: DrawnObject = when (_state.value.selectedChartTool) {
            ChartTool.TREND_LINE -> DrawnObject.TrendLine(a1.first, a1.second, idx, price)
            ChartTool.FIBONACCI  -> {
                val hi = if (a1.second >= price) a1.second else price
                val lo = if (a1.second <  price) a1.second else price
                val hiIdx = if (a1.second >= price) a1.first else idx
                val loIdx = if (a1.second <  price) a1.first else idx
                DrawnObject.FibRetracement(hiIdx, hi, loIdx, lo)
            }
            else -> return
        }
        _state.value = _state.value.copy(
            drawnObjects = _state.value.drawnObjects + obj,
            drawAnchor1 = null,
            waitingForAnchor2 = false,
            selectedChartTool = ChartTool.NONE
        )
    }

    /** Add a horizontal price level line. */
    fun addHorizontalLine(price: Double, label: String = "") {
        if (price <= 0) return
        _state.value = _state.value.copy(
            drawnObjects = _state.value.drawnObjects + DrawnObject.HorizontalLine(price, label),
            selectedChartTool = ChartTool.NONE,
            showHLineDialog = false
        )
    }

    /** Add a long or short position overlay. */
    fun addPosition(isLong: Boolean, entry: Double, stopLoss: Double, takeProfit: Double) {
        if (entry <= 0) return
        val entryIdx = _state.value.chartEntries.size - 1
        val obj = if (isLong)
            DrawnObject.LongPosition(entryIdx, entry, stopLoss, takeProfit)
        else
            DrawnObject.ShortPosition(entryIdx, entry, stopLoss, takeProfit)
        _state.value = _state.value.copy(
            drawnObjects = _state.value.drawnObjects + obj,
            selectedChartTool = ChartTool.NONE,
            showPositionDialog = false
        )
    }

    /** Add a Fibonacci retracement from dialog. */
    fun addFibRetracement(fib: DrawnObject.FibRetracement) {
        _state.value = _state.value.copy(
            drawnObjects = _state.value.drawnObjects + fib,
            selectedChartTool = ChartTool.NONE,
            showFibDialog = false
        )
    }

    fun dismissAllDialogs() {
        _state.value = _state.value.copy(
            showPositionDialog = false,
            showFibDialog = false,
            showHLineDialog = false,
            selectedChartTool = ChartTool.NONE,
            drawAnchor1 = null,
            waitingForAnchor2 = false
        )
    }

    fun undoLastDrawing() {
        val list = _state.value.drawnObjects
        if (list.isNotEmpty()) {
            _state.value = _state.value.copy(drawnObjects = list.dropLast(1))
        }
    }

    fun clearAllDrawings() {
        _state.value = _state.value.copy(
            drawnObjects = emptyList(),
            drawAnchor1 = null,
            waitingForAnchor2 = false,
            selectedChartTool = ChartTool.NONE
        )
    }

    fun toggleWatchlist() {
        val stock = _state.value.stock ?: return
        viewModelScope.launch {
            if (_state.value.isInWatchlist) {
                localRepo.removeFromWatchlist(stock.symbol, AssetType.STOCK)
            } else {
                localRepo.addToWatchlist(
                    WatchlistItem(symbol = stock.symbol, name = stock.name, type = AssetType.STOCK)
                )
            }
        }
    }

    private fun deriveSentimentFromTechnicals(td: TechnicalData, existing: SentimentData): SentimentData {
        if (existing.totalMentions > 0) return existing
        val rsiSignal = when {
            td.rsi >= 70 -> -15.0
            td.rsi >= 60 -> +12.0
            td.rsi >= 50 -> +6.0
            td.rsi >= 40 -> -6.0
            td.rsi >= 30 -> -12.0
            else         -> -15.0
        }
        val macdSignal = if (td.macd > td.macdSignal) +10.0 else -10.0
        val emaSignal = when {
            td.ema50 > 0 && td.ema200 > 0 && td.ema50 > td.ema200 -> +10.0
            td.ema50 > 0 && td.ema200 > 0 && td.ema50 < td.ema200 -> -10.0
            else -> 0.0
        }
        val bullish = (50.0 + rsiSignal + macdSignal + emaSignal).coerceIn(15.0, 85.0)
        return SentimentData(
            bullishPercent = bullish,
            bearishPercent = 100.0 - bullish,
            totalMentions  = 0,
            trendingScore  = td.rsi
        )
    }
}
