package com.tradescreenerai.app.ui.screens.screener

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.repository.CommodityRepository
import com.tradescreenerai.app.data.repository.CryptoDataSource
import com.tradescreenerai.app.data.repository.CryptoRepository
import com.tradescreenerai.app.data.repository.StockRepository
import com.tradescreenerai.app.domain.MarketDiscoveryEngine
import com.tradescreenerai.app.domain.StockRankingEngine
import com.tradescreenerai.app.domain.TechnicalAnalysis
import com.tradescreenerai.app.util.Constants
import com.tradescreenerai.app.util.Resource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScreenerState(
    val activeTab: Int = 0, // 0 = Crypto, 1 = Stocks (penny stocks), 2 = Short-Term, 3 = Long-Term, 4 = Breakouts, 5 = Reversals, 6 = Commodities
    val cryptos: List<Crypto> = emptyList(),
    val filteredCryptos: List<Crypto> = emptyList(),
    val stocks: List<Stock> = emptyList(),
    val filteredStocks: List<Stock> = emptyList(),
    val shortTermStocks: List<RankedStock> = emptyList(),
    val longTermStocks: List<RankedStock> = emptyList(),
    val breakoutStocks: List<RankedStock> = emptyList(),
    val reversalStocks: List<RankedStock> = emptyList(),
    val highVolumeStocks: List<RankedStock> = emptyList(),
    val commodityMovers: List<CommodityRanking> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val stockError: String? = null,   // shown when penny stocks fail to load
    // Filters
    val minPrice: Double = 0.0,
    val maxPrice: Double = Double.MAX_VALUE,
    val minChange: Double = -100.0,
    val maxChange: Double = 100.0,
    val sortBy: SortBy = SortBy.MARKET_CAP,
    val sortDesc: Boolean = true,
    val showFilters: Boolean = false,
    // Penny stock specific
    val showPennyStocksOnly: Boolean = false,
    val minVolumeFilter: Long = 0L,
    // Live update tracking
    val lastUpdated: Long = 0L,
    val nextRefreshIn: Int = 60,
    /** Which API is currently serving crypto data (CoinGecko or fallback CoinPaprika). */
    val dataSource: CryptoDataSource = CryptoDataSource.COINGECKO,
    val discoveryLoading: Boolean = false,
    /** True when US market is currently open (Mon–Fri 09:30–16:00 ET). */
    val isMarketOpen: Boolean = true
)

class ScreenerViewModel : ViewModel() {
    private val cryptoRepo = CryptoRepository()
    private val stockRepo = StockRepository()
    private val commodityRepo = CommodityRepository()

    private val _state = MutableStateFlow(ScreenerState())
    val state = _state.asStateFlow()

    companion object {
        const val REFRESH_SECONDS = 60L

        /** Returns true when the US stock market is currently open (Mon–Fri 09:30–16:00 ET). */
        fun isUSMarketOpen(): Boolean {
            val et = java.util.TimeZone.getTimeZone("America/New_York")
            val now = java.util.Calendar.getInstance(et)
            val dow = now.get(java.util.Calendar.DAY_OF_WEEK)
            if (dow == java.util.Calendar.SATURDAY || dow == java.util.Calendar.SUNDAY) return false
            val mins = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
            return mins in (9 * 60 + 30)..(16 * 60)
        }
    }

    init {
        startLiveUpdates()
    }

    /** Auto-refresh loop: reload prices every 60 seconds. */
    private fun startLiveUpdates() {
        viewModelScope.launch {
            while (true) {
                doLoadData()
                // Countdown timer
                for (sec in REFRESH_SECONDS.toInt() downTo 1) {
                    _state.value = _state.value.copy(nextRefreshIn = sec)
                    delay(1_000L)
                }
            }
        }
    }

    /** Public: manual refresh (called by the Refresh button). */
    fun loadData() {
        viewModelScope.launch { doLoadData() }
    }

    private suspend fun doLoadData() {
        val marketOpen = isUSMarketOpen()
        _state.value = _state.value.copy(isLoading = true, isMarketOpen = marketOpen)

        // Load cryptos
        val cryptoJob = viewModelScope.launch {
            when (val result = cryptoRepo.getMarkets(perPage = 100)) {
                is Resource.Success -> {
                    val data = result.data ?: emptyList()
                    _state.value = _state.value.copy(cryptos = data)
                    applyFilters()
                }
                is Resource.Error -> _state.value = _state.value.copy(error = result.message)
                is Resource.Loading -> {}
            }
        }

        // Load penny stocks via Polygon (gainers/losers + curated snapshot)
        val stockJob = viewModelScope.launch {
            when (val result = stockRepo.getPennyStocks(
                maxPrice  = 5.0,
                minVolume = 0L,    // volume filter handled by applyFilters() UI slider
                limit     = 150
            )) {
                is Resource.Success -> {
                    result.data?.let { stocks ->
                        _state.value = _state.value.copy(stocks = stocks, stockError = null)
                        applyFilters()
                    }
                }
                is Resource.Error -> {
                    // Keep previously loaded stocks; show offline notice instead of clearing
                    val offlineMsg = if (!marketOpen)
                        "⚠ Market closed · prices show last session close · graphs still available"
                    else
                        result.message ?: "No stock data available right now"
                    _state.value = _state.value.copy(stockError = offlineMsg)
                    applyFilters()  // re-apply so any cached stocks remain visible
                }
                else -> {}
            }
        }

        cryptoJob.join()
        stockJob.join()

        _state.value = _state.value.copy(
            isLoading = false,
            lastUpdated = System.currentTimeMillis(),
            nextRefreshIn = REFRESH_SECONDS.toInt(),
            dataSource = cryptoRepo.activeDataSource
        )

        // Load discovery data in background (doesn't block main screener)
        loadDiscoveryData()
    }

    /**
     * Loads ranked stock lists and commodity movers for discovery tabs.
     * Runs in background after the main screener data is loaded.
     */
    private fun loadDiscoveryData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(discoveryLoading = true)

            try {
                // Fetch batch quotes for ranking
                val quotesResult = stockRepo.getBatchQuotes(Constants.RANKING_STOCK_POOL.take(40))
                val stocks = when (quotesResult) {
                    is Resource.Success -> quotesResult.data ?: emptyList()
                    else -> emptyList()
                }

                val shortTermList = mutableListOf<RankedStock>()
                val longTermList = mutableListOf<RankedStock>()
                val breakoutList = mutableListOf<RankedStock>()
                val reversalList = mutableListOf<RankedStock>()
                val highVolumeList = mutableListOf<RankedStock>()

                for (stock in stocks.take(25)) {
                    try {
                        val chartResult = stockRepo.getDailyTimeSeries(stock.symbol, days = 365)
                        val entries = when (chartResult) {
                            is Resource.Success -> chartResult.data ?: emptyList()
                            else -> emptyList()
                        }
                        if (entries.size < 26) continue

                        val td = TechnicalAnalysis.calculateAllIndicators(entries)

                        val shortRank = StockRankingEngine.scoreShortTerm(stock, entries, td)
                        if (shortRank.score >= 40) shortTermList.add(shortRank)

                        val longRank = StockRankingEngine.scoreLongTerm(stock, entries, td)
                        if (longRank.score >= 40) longTermList.add(longRank)

                        if (StockRankingEngine.isBreakout(entries, td)) {
                            breakoutList.add(shortRank.copy(
                                reasons = listOf("Breakout detected") + shortRank.reasons
                            ))
                        }

                        if (StockRankingEngine.isReversal(entries, td)) {
                            reversalList.add(shortRank.copy(
                                reasons = listOf("Reversal candidate") + shortRank.reasons
                            ))
                        }

                        if (td.volumeAvg20 > 0 && entries.last().volume > td.volumeAvg20 * 1.5) {
                            highVolumeList.add(shortRank)
                        }
                    } catch (_: Exception) { }
                }

                // Load commodity movers
                var commodityMovers = emptyList<CommodityRanking>()
                try {
                    val commodityResult = commodityRepo.getAllCommodities()
                    if (commodityResult is Resource.Success) {
                        commodityMovers = MarketDiscoveryEngine.rankCommodityMovers(
                            commodityResult.data ?: emptyList()
                        )
                    }
                } catch (_: Exception) { }

                _state.value = _state.value.copy(
                    shortTermStocks = shortTermList.sortedByDescending { it.score }.take(15),
                    longTermStocks = longTermList.sortedByDescending { it.score }.take(15),
                    breakoutStocks = breakoutList.sortedByDescending { it.score }.take(10),
                    reversalStocks = reversalList.sortedByDescending { it.score }.take(10),
                    highVolumeStocks = highVolumeList.sortedByDescending { it.volumeScore }.take(10),
                    commodityMovers = commodityMovers,
                    discoveryLoading = false
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(discoveryLoading = false)
            }
        }
    }

    fun setTab(tab: Int) {
        _state.value = _state.value.copy(activeTab = tab)
    }

    fun toggleFilters() {
        _state.value = _state.value.copy(showFilters = !_state.value.showFilters)
    }

    fun updateMinPrice(value: Double) {
        _state.value = _state.value.copy(minPrice = value)
        applyFilters()
    }

    fun updateMaxPrice(value: Double) {
        _state.value = _state.value.copy(maxPrice = value)
        applyFilters()
    }

    fun updateMinChange(value: Double) {
        _state.value = _state.value.copy(minChange = value)
        applyFilters()
    }

    fun updateMaxChange(value: Double) {
        _state.value = _state.value.copy(maxChange = value)
        applyFilters()
    }

    fun updateSort(sortBy: SortBy) {
        val desc = if (_state.value.sortBy == sortBy) !_state.value.sortDesc else true
        _state.value = _state.value.copy(sortBy = sortBy, sortDesc = desc)
        applyFilters()
    }

    fun togglePennyFilter() {
        _state.value = _state.value.copy(showPennyStocksOnly = !_state.value.showPennyStocksOnly)
        applyFilters()
    }

    fun updateMinVolumeFilter(volume: Long) {
        _state.value = _state.value.copy(minVolumeFilter = volume)
        applyFilters()
    }

    fun resetFilters() {
        _state.value = _state.value.copy(
            minPrice = 0.0,
            maxPrice = Double.MAX_VALUE,
            minChange = -100.0,
            maxChange = 100.0,
            sortBy = SortBy.MARKET_CAP,
            sortDesc = true,
            showPennyStocksOnly = false,
            minVolumeFilter = 0L
        )
        applyFilters()
    }

    private fun applyFilters() {
        val s = _state.value

        // Filter cryptos
        val filteredC = s.cryptos.filter { c ->
            c.price >= s.minPrice &&
            (s.maxPrice == Double.MAX_VALUE || c.price <= s.maxPrice) &&
            c.changePercent24h >= s.minChange &&
            c.changePercent24h <= s.maxChange
        }.let { list ->
            when (s.sortBy) {
                SortBy.PRICE -> if (s.sortDesc) list.sortedByDescending { it.price } else list.sortedBy { it.price }
                SortBy.CHANGE -> if (s.sortDesc) list.sortedByDescending { it.changePercent24h } else list.sortedBy { it.changePercent24h }
                SortBy.VOLUME -> if (s.sortDesc) list.sortedByDescending { it.volume24h } else list.sortedBy { it.volume24h }
                SortBy.MARKET_CAP -> if (s.sortDesc) list.sortedByDescending { it.marketCap } else list.sortedBy { it.marketCap }
                SortBy.NAME -> if (s.sortDesc) list.sortedByDescending { it.name } else list.sortedBy { it.name }
            }
        }

        // Filter stocks
        val filteredS = s.stocks.filter { stock ->
            stock.price >= s.minPrice &&
            (s.maxPrice == Double.MAX_VALUE || stock.price <= s.maxPrice) &&
            stock.changePercent >= s.minChange &&
            stock.changePercent <= s.maxChange &&
            // Penny stock toggle: price < $5
            (!s.showPennyStocksOnly || stock.price in 0.0001..4.999) &&
            // Volume filter
            (s.minVolumeFilter == 0L || stock.volume >= s.minVolumeFilter)
        }.let { list ->
            when (s.sortBy) {
                SortBy.PRICE -> if (s.sortDesc) list.sortedByDescending { it.price } else list.sortedBy { it.price }
                SortBy.CHANGE -> if (s.sortDesc) list.sortedByDescending { it.changePercent } else list.sortedBy { it.changePercent }
                SortBy.VOLUME -> if (s.sortDesc) list.sortedByDescending { it.volume } else list.sortedBy { it.volume }
                SortBy.MARKET_CAP -> if (s.sortDesc) list.sortedByDescending { it.marketCap } else list.sortedBy { it.marketCap }
                SortBy.NAME -> if (s.sortDesc) list.sortedByDescending { it.name } else list.sortedBy { it.name }
            }
        }

        _state.value = s.copy(filteredCryptos = filteredC, filteredStocks = filteredS)
    }
}


