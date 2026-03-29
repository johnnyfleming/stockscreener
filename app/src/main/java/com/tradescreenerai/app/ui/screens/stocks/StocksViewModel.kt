package com.tradescreenerai.app.ui.screens.stocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.repository.NewsRepository
import com.tradescreenerai.app.data.repository.StockRepository
import com.tradescreenerai.app.domain.AiInsightEngine
import com.tradescreenerai.app.domain.BeginnerInsightEngine
import com.tradescreenerai.app.domain.ConfluenceScoreEngine
import com.tradescreenerai.app.domain.SmartSignalEngine
import com.tradescreenerai.app.domain.StockRankingEngine
import com.tradescreenerai.app.domain.TechnicalAnalysis
import com.tradescreenerai.app.util.Constants
import com.tradescreenerai.app.util.Resource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StocksState(
    val activeTab: Int = 0,  // 0=Short-Term, 1=Long-Term, 2=Beginner, 3=Day Trader, 4=LT Beginner, 5=Advanced+News
    val shortTermStocks: List<RankedStock> = emptyList(),
    val longTermStocks: List<RankedStock> = emptyList(),
    val beginnerLongTermStocks: List<RankedStock> = emptyList(),
    val advancedStocks: List<RankedStock> = emptyList(),
    val advancedStockNews: Map<String, List<NewsArticle>> = emptyMap(),
    val allStocks: List<Stock> = emptyList(),
    val selectedInsight: BeginnerInsight? = null,
    val selectedStock: Stock? = null,
    val isLoading: Boolean = true,
    val isLoadingAdvancedNews: Boolean = false,
    val error: String? = null,
    val lastUpdated: Long = 0L,
    val nextRefreshIn: Int = 120,
    val isBeginnerMode: Boolean = false
)

class StocksViewModel : ViewModel() {
    private val stockRepo = StockRepository()
    private val newsRepo  = NewsRepository()

    private val _state = MutableStateFlow(StocksState())
    val state = _state.asStateFlow()

    companion object {
        const val REFRESH_SECONDS = 120L

        /** Stable blue-chip stocks curated for beginner long-term investing */
        val BEGINNER_LT_SYMBOLS = listOf(
            "AAPL", "MSFT", "JNJ", "KO", "PEP", "V", "MA",
            "WMT", "COST", "HD", "PG", "VZ", "ABBV", "PFE",
            "AMZN", "GOOGL", "BRK-B", "DIS", "MCD", "NKE"
        )

        /** More complex/volatile stocks for the Advanced + News tab */
        val ADVANCED_SYMBOLS = listOf(
            "TSLA", "NVDA", "AMD", "PLTR", "SNOW", "CRWD",
            "DDOG", "COIN", "MSTR", "RIVN", "NIO", "GME",
            "AMC", "RBLX", "ABNB", "SOFI", "HOOD", "NET", "ZS", "PANW"
        )

        /** Simple explanations for well-known beginner stocks */
        val BEGINNER_STOCK_INFO = mapOf(
            "AAPL"  to Pair("Apple Inc.", "Makes iPhones & Macs. Has grown consistently for decades. Pays dividends. 🍎"),
            "MSFT"  to Pair("Microsoft", "Cloud computing giant (Azure). Powers Office & Xbox. Very stable growth. 💻"),
            "JNJ"   to Pair("Johnson & Johnson", "Healthcare leader selling medicines & consumer products worldwide. Defensive stock. 💊"),
            "KO"    to Pair("Coca-Cola", "The world's #1 beverage brand. Known for steady dividends since 1893. 🥤"),
            "PEP"   to Pair("PepsiCo", "Beverages & snacks (Pepsi, Lays, Quaker). Reliable dividend payer. 🍟"),
            "V"     to Pair("Visa", "Processes billions of card payments worldwide. Profits when spending rises. 💳"),
            "MA"    to Pair("Mastercard", "Second-largest card network. Benefits from global shift to cashless payments. 💳"),
            "WMT"   to Pair("Walmart", "World's largest retailer. Resilient in downturns — people always buy essentials. 🛒"),
            "COST"  to Pair("Costco", "Membership warehouse club with loyal customers and consistent growth. 📦"),
            "HD"    to Pair("Home Depot", "Largest home improvement retailer. Benefits from housing market activity. 🔨"),
            "PG"    to Pair("Procter & Gamble", "Makes Tide, Pampers, Gillette. People buy these in good times AND bad. 🧴"),
            "VZ"    to Pair("Verizon", "Major US telecom. High dividend yield. Stable cash flows from subscriptions. 📱"),
            "ABBV"  to Pair("AbbVie", "Pharmaceutical company known for Humira. Strong pipeline & high dividends. 💉"),
            "PFE"   to Pair("Pfizer", "Global pharmaceutical giant. Known for vaccines and diverse drug portfolio. 💊"),
            "AMZN"  to Pair("Amazon", "E-commerce + AWS cloud leader. One of the most dominant businesses on earth. 📦"),
            "GOOGL" to Pair("Alphabet (Google)", "Google Search, YouTube, Google Cloud. Advertising-driven but diversifying fast. 🔍"),
            "BRK-B" to Pair("Berkshire Hathaway", "Warren Buffett's holding company. A diversified basket of top US businesses. 🏛️"),
            "DIS"   to Pair("Walt Disney", "Disney+, parks, ESPN & Marvel. A brand that transcends generations. 🏰"),
            "MCD"   to Pair("McDonald's", "World's largest fast food chain. Resilient earnings even in recessions. 🍔"),
            "NKE"   to Pair("Nike", "Global sportswear brand with iconic Jordan & Air Max lines. Premium pricing power. 👟")
        )

        /** Why each advanced stock is complex */
        val ADVANCED_STOCK_INFO = mapOf(
            "TSLA"  to "High volatility, driven by Elon Musk tweets & EV competition. Can move ±10% in a day. ⚡",
            "NVDA"  to "AI/GPU darling. Massive gains but extremely high valuation — sensitive to AI spending sentiment. 🤖",
            "AMD"   to "Competes directly with Intel & Nvidia. Volatile around earnings and AI hype cycles. 💻",
            "PLTR"  to "Government AI/data analytics. Binary on contract wins. Hard to value on traditional metrics. 🛡️",
            "SNOW"  to "Cloud data platform. High growth but burning cash. Very sensitive to interest rate changes. ❄️",
            "CRWD"  to "Cybersecurity leader but richly valued. Any breach incidents cause massive sell-offs. 🔒",
            "DDOG"  to "DevOps monitoring platform. High multiple stock — falls hard when growth slows. 🐶",
            "COIN"  to "Crypto exchange — moves with Bitcoin price. Regulatory risk is always present. 🪙",
            "MSTR"  to "Leveraged Bitcoin play. CEO Michael Saylor buys BTC constantly. Extreme volatility. ₿",
            "RIVN"  to "EV startup burning cash. Future depends on Amazon van contracts & consumer demand. 🚚",
            "NIO"   to "Chinese EV maker. Subject to US-China tensions, delisting risk, and EV price wars. 🚗",
            "GME"   to "GameStop — the original meme stock. Fundamentals irrelevant; driven by Reddit sentiment. 🎮",
            "AMC"   to "Cinema chain battling streaming. Still a meme stock, fundamental outlook is weak. 🎬",
            "RBLX"  to "Gaming metaverse platform. Young user base but struggles to monetise adults. 🎮",
            "ABNB"  to "Airbnb — sensitive to travel demand, regulation bans, and economic cycles. 🏠",
            "SOFI"  to "Digital bank. Struggling with student loan politics and fintech competition. 💸",
            "HOOD"  to "Robinhood — trading platform. Revenue tied to market volatility, highly cyclical. 📉",
            "NET"   to "Cloudflare networking. High-growth but no profit yet — sensitive to macro rate moves. ☁️",
            "ZS"    to "Zero-trust cybersecurity. Premium valuation demands perfect execution every quarter. 🔐",
            "PANW"  to "Palo Alto Networks. Acquisitive cybersecurity platform — integration risk is high. 🛡️"
        )
    }

    init { startLiveUpdates() }

    private fun startLiveUpdates() {
        viewModelScope.launch {
            while (true) {
                loadStockData()
                for (sec in REFRESH_SECONDS.toInt() downTo 1) {
                    _state.value = _state.value.copy(nextRefreshIn = sec)
                    delay(1_000L)
                }
            }
        }
    }

    fun refresh() { viewModelScope.launch { loadStockData() } }
    fun setTab(index: Int) { _state.value = _state.value.copy(activeTab = index) }
    fun toggleBeginnerMode() { _state.value = _state.value.copy(isBeginnerMode = !_state.value.isBeginnerMode) }

    fun selectStockForInsight(stock: Stock) {
        val insight = BeginnerInsightEngine.generateStockInsight(stock, null)
        _state.value = _state.value.copy(selectedInsight = insight, selectedStock = stock)
    }

    fun dismissInsight() { _state.value = _state.value.copy(selectedInsight = null, selectedStock = null) }

    private suspend fun loadStockData() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            // ── Step 1: Fetch all symbols we need (ranking pool + beginner + advanced) ─
            val allSymbols = (Constants.RANKING_STOCK_POOL + BEGINNER_LT_SYMBOLS + ADVANCED_SYMBOLS).distinct()
            val quotesResult = stockRepo.getBatchQuotes(allSymbols)
            val stocks = when (quotesResult) {
                is Resource.Success -> quotesResult.data ?: emptyList()
                is Resource.Error   -> {
                    _state.value = _state.value.copy(isLoading = false, error = quotesResult.message)
                    return
                }
                else -> emptyList()
            }
            if (stocks.isEmpty()) {
                _state.value = _state.value.copy(isLoading = false, error = "No stock data available")
                return
            }
            _state.value = _state.value.copy(allStocks = stocks)

            val stockMap = stocks.associateBy { it.symbol }

            // ── Step 2: Process charts for ranking pool (top 25) ──────────────
            val toProcess = stocks.filter { it.symbol in Constants.RANKING_STOCK_POOL }.take(25)
            val rankingResults: List<Triple<RankedStock?, RankedStock?, Pair<String, RankedStock>?>> = coroutineScope {
                toProcess.map { stock ->
                    async {
                        try {
                            val chartResult = stockRepo.getDailyTimeSeries(stock.symbol, days = 365)
                            val entries = when (chartResult) {
                                is Resource.Success -> chartResult.data ?: emptyList()
                                else -> emptyList()
                            }
                            if (entries.size < 20) return@async Triple(null, null, null)
                            val td          = TechnicalAnalysis.calculateAllIndicators(entries)
                            val smart       = SmartSignalEngine.calculate(entries, td)
                            val currentPrice = entries.last().close
                            val aiInsight   = AiInsightEngine.generateInsight(td, smart, currentPrice)
                            val confluence  = ConfluenceScoreEngine.calculate(entries, td)
                            val sparkline   = entries.takeLast(30).map { it.close }
                            val shortRank   = StockRankingEngine.scoreShortTerm(stock, entries, td)
                                .copy(technicalData = td, sparkline = sparkline, aiInsight = aiInsight, confluenceScore = confluence.score)
                            val longRank    = StockRankingEngine.scoreLongTerm(stock, entries, td)
                                .copy(technicalData = td, sparkline = sparkline, aiInsight = aiInsight, confluenceScore = confluence.score)
                            Triple(
                                if (shortRank.score > 0) shortRank else null,
                                if (longRank.score  > 0) longRank  else null,
                                null
                            )
                        } catch (_: Exception) { Triple(null, null, null) }
                    }
                }.awaitAll()
            }

            val shortTermRanked = rankingResults.mapNotNull { it.first }.sortedByDescending { it.score }.take(20)
            val longTermRanked  = rankingResults.mapNotNull { it.second }.sortedByDescending { it.score }.take(20)

            val finalShort = shortTermRanked.ifEmpty {
                stocks.filter { it.symbol in Constants.RANKING_STOCK_POOL }
                    .sortedByDescending { it.changePercent }.take(20)
                    .map { s -> RankedStock(stock = s, score = 50, horizon = StockHorizon.SHORT_TERM, reasons = listOf("Ranked by % gain"), momentumScore = s.changePercent.coerceIn(0.0, 100.0), volumeScore = 50.0, trendScore = 50.0) }
            }
            val finalLong = longTermRanked.ifEmpty {
                stocks.filter { it.symbol in Constants.RANKING_STOCK_POOL && it.price > 10 }
                    .sortedByDescending { it.marketCap }.take(20)
                    .map { s -> RankedStock(stock = s, score = 50, horizon = StockHorizon.LONG_TERM, reasons = listOf("Ranked by market cap"), momentumScore = 50.0, volumeScore = 50.0, trendScore = 50.0) }
            }

            // ── Step 3: Build beginner long-term list (curated blue-chips) ────
            val beginnerLt = BEGINNER_LT_SYMBOLS.mapNotNull { sym ->
                val s = stockMap[sym] ?: return@mapNotNull null
                val info = BEGINNER_STOCK_INFO[sym]
                val name = info?.first ?: s.name
                val whyReason = info?.second ?: "Blue-chip company with long track record"
                // Compute a simple safety score: higher market cap = safer
                val safetyScore = when {
                    s.marketCap > 500_000_000_000L -> 95
                    s.marketCap > 200_000_000_000L -> 85
                    s.marketCap > 50_000_000_000L  -> 75
                    s.marketCap > 10_000_000_000L  -> 65
                    else -> 55
                }
                RankedStock(
                    stock         = s.copy(name = name),
                    score         = safetyScore,
                    horizon       = StockHorizon.LONG_TERM,
                    reasons       = listOf(whyReason),
                    momentumScore = 60.0,
                    volumeScore   = 70.0,
                    trendScore    = 70.0
                )
            }

            // ── Step 4: Build advanced stocks list ────────────────────────────
            val advancedList = ADVANCED_SYMBOLS.mapNotNull { sym ->
                val s = stockMap[sym] ?: return@mapNotNull null
                val why = ADVANCED_STOCK_INFO[sym] ?: "High volatility — complex for beginners"
                RankedStock(
                    stock         = s,
                    score         = 50,
                    horizon       = StockHorizon.SHORT_TERM,
                    reasons       = listOf(why),
                    momentumScore = kotlin.math.abs(s.changePercent).coerceIn(0.0, 100.0),
                    volumeScore   = 50.0,
                    trendScore    = 50.0
                )
            }

            _state.value = _state.value.copy(
                allStocks             = stocks,
                shortTermStocks       = finalShort,
                longTermStocks        = finalLong,
                beginnerLongTermStocks = beginnerLt,
                advancedStocks        = advancedList,
                isLoading             = false,
                lastUpdated           = System.currentTimeMillis()
            )

            // ── Step 5: Load news for advanced stocks in background ────────────
            viewModelScope.launch { loadAdvancedStockNews(advancedList.map { it.stock.symbol }.take(8)) }

        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load stock data")
        }
    }

    private suspend fun loadAdvancedStockNews(symbols: List<String>) {
        _state.value = _state.value.copy(isLoadingAdvancedNews = true)
        val newsMap = mutableMapOf<String, List<NewsArticle>>()
        // Fetch news for each symbol sequentially to avoid rate limits
        for (sym in symbols) {
            try {
                val result = newsRepo.getCompanyNews(sym)
                if (result is Resource.Success) {
                    newsMap[sym] = (result.data ?: emptyList()).take(3)
                }
                delay(300L) // small delay to avoid hammering APIs
            } catch (_: Exception) { }
        }
        _state.value = _state.value.copy(
            advancedStockNews     = newsMap,
            isLoadingAdvancedNews = false
        )
    }
}
