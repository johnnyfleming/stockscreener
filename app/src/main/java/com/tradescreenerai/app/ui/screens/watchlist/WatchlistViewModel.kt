package com.tradescreenerai.app.ui.screens.watchlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.repository.CryptoRepository
import com.tradescreenerai.app.data.repository.LocalDataRepository
import com.tradescreenerai.app.data.repository.StockRepository
import com.tradescreenerai.app.domain.TechnicalAnalysis
import com.tradescreenerai.app.util.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class WatchlistSortBy(val label: String) {
    ADDED("Date Added"),
    NAME("Name"),
    CHANGE("24h Change"),
    SCORE("Setup Score"),
    PRICE("Price")
}

data class WatchlistState(
    val items: List<WatchlistItem> = emptyList(),
    val stockPrices: Map<String, Stock> = emptyMap(),
    val cryptoPrices: Map<String, Crypto> = emptyMap(),
    val isLoading: Boolean = true,
    /** Symbol of the watchlist item with the best setup (highest sparkline score). */
    val bestSetupSymbol: String? = null,
    /** Score breakdown per symbol: symbol → (score 0-100, label) */
    val setupScores: Map<String, Pair<Int, String>> = emptyMap(),
    val sortBy: WatchlistSortBy = WatchlistSortBy.ADDED,
    val sortDesc: Boolean = true,
    val lastRefreshed: Long = 0L,
    val nextRefreshIn: Int = 120,
    val error: String? = null,
    /** Overall watchlist health: avg score across all scored items */
    val avgScore: Int = 0,
    val bullishCount: Int = 0,
    val bearishCount: Int = 0
)

class WatchlistViewModel(application: Application) : AndroidViewModel(application) {
    private val localRepo = LocalDataRepository(application)
    private val stockRepo = StockRepository()
    private val cryptoRepo = CryptoRepository()

    private val _state = MutableStateFlow(WatchlistState())
    val state = _state.asStateFlow()

    private var autoRefreshJob: Job? = null

    companion object {
        const val REFRESH_INTERVAL = 120L // seconds
    }

    init {
        viewModelScope.launch {
            localRepo.watchlist.collect { items ->
                _state.value = _state.value.copy(items = items)
                startAutoRefresh(items)
            }
        }
    }

    private fun startAutoRefresh(items: List<WatchlistItem>) {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                refreshPrices(items)
                for (sec in REFRESH_INTERVAL.toInt() downTo 1) {
                    _state.value = _state.value.copy(nextRefreshIn = sec)
                    delay(1_000L)
                }
            }
        }
    }

    fun refresh() {
        val items = _state.value.items
        if (items.isNotEmpty()) startAutoRefresh(items)
    }

    fun setSortBy(sort: WatchlistSortBy) {
        val newDesc = if (_state.value.sortBy == sort) !_state.value.sortDesc else true
        _state.value = _state.value.copy(sortBy = sort, sortDesc = newDesc)
    }

    /** Returns items sorted according to the current sort preference. */
    fun sortedItems(): List<WatchlistItem> {
        val st = _state.value
        val items = st.items
        val sorted = when (st.sortBy) {
            WatchlistSortBy.ADDED -> items.sortedBy { it.addedAt }
            WatchlistSortBy.NAME -> items.sortedBy { it.name.lowercase() }
            WatchlistSortBy.CHANGE -> items.sortedBy { changeFor(it) }
            WatchlistSortBy.SCORE -> items.sortedBy { st.setupScores[it.symbol]?.first ?: 0 }
            WatchlistSortBy.PRICE -> items.sortedBy { priceFor(it) }
        }
        return if (st.sortDesc) sorted.reversed() else sorted
    }

    private fun changeFor(item: WatchlistItem): Double {
        return when (item.type) {
            AssetType.CRYPTO -> _state.value.cryptoPrices[item.symbol]?.changePercent24h ?: 0.0
            AssetType.STOCK -> _state.value.stockPrices[item.symbol]?.changePercent ?: 0.0
            AssetType.COMMODITY -> _state.value.stockPrices[item.symbol]?.changePercent ?: 0.0
        }
    }

    private fun priceFor(item: WatchlistItem): Double {
        return when (item.type) {
            AssetType.CRYPTO -> _state.value.cryptoPrices[item.symbol]?.price ?: 0.0
            AssetType.STOCK -> _state.value.stockPrices[item.symbol]?.price ?: 0.0
            AssetType.COMMODITY -> _state.value.stockPrices[item.symbol]?.price ?: 0.0
        }
    }

    private suspend fun refreshPrices(items: List<WatchlistItem>) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        val scores = mutableMapOf<String, Pair<Int, String>>()

        try {
            items.forEach { item ->
                when (item.type) {
                    AssetType.STOCK -> {
                        when (val result = stockRepo.getQuote(item.symbol)) {
                            is Resource.Success -> result.data?.let { stock ->
                                _state.value = _state.value.copy(
                                    stockPrices = _state.value.stockPrices + (item.symbol to stock)
                                )
                                // Simple stock scoring based on change %
                                val stockScore = scoreStockChange(stock.changePercent)
                                val label = when {
                                    stockScore >= 70 -> "Strong Bullish"
                                    stockScore >= 50 -> "Bullish"
                                    stockScore >= 30 -> "Neutral"
                                    else -> "Bearish"
                                }
                                scores[item.symbol] = stockScore to label
                            }
                            else -> {}
                        }
                    }
                    AssetType.CRYPTO -> {
                        when (val result = cryptoRepo.getCoinDetail(item.symbol)) {
                            is Resource.Success -> result.data?.let { crypto ->
                                _state.value = _state.value.copy(
                                    cryptoPrices = _state.value.cryptoPrices + (item.symbol to crypto)
                                )
                                // Score the sparkline for watchlist intelligence
                                if (crypto.sparkline.size >= 30) {
                                    val sparkScore = scoreSparkline(crypto.sparkline)
                                    val label = when {
                                        sparkScore >= 70 -> "Strong Bullish"
                                        sparkScore >= 50 -> "Bullish"
                                        sparkScore >= 30 -> "Neutral"
                                        else -> "Bearish"
                                    }
                                    scores[item.symbol] = sparkScore to label
                                }
                            }
                            else -> {}
                        }
                    }
                    AssetType.COMMODITY -> {
                        // Commodity watchlist items use stock quote as fallback
                        when (val result = stockRepo.getQuote(item.symbol)) {
                            is Resource.Success -> result.data?.let { stock ->
                                _state.value = _state.value.copy(
                                    stockPrices = _state.value.stockPrices + (item.symbol to stock)
                                )
                                val stockScore = scoreStockChange(stock.changePercent)
                                val label = when {
                                    stockScore >= 70 -> "Strong Bullish"
                                    stockScore >= 50 -> "Bullish"
                                    stockScore >= 30 -> "Neutral"
                                    else -> "Bearish"
                                }
                                scores[item.symbol] = stockScore to label
                            }
                            else -> {}
                        }
                    }
                }
            }

            val bestSymbol = scores.maxByOrNull { it.value.first }?.key
            val avgScore = if (scores.isNotEmpty()) scores.values.map { it.first }.average().toInt() else 0
            val bullishCount = scores.values.count { it.first >= 50 }
            val bearishCount = scores.values.count { it.first < 30 }

            _state.value = _state.value.copy(
                isLoading = false,
                setupScores = scores,
                bestSetupSymbol = bestSymbol,
                lastRefreshed = System.currentTimeMillis(),
                avgScore = avgScore,
                bullishCount = bullishCount,
                bearishCount = bearishCount
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = e.message ?: "Failed to refresh prices"
            )
        }
    }

    /** Simple stock scoring based on daily change % */
    private fun scoreStockChange(changePct: Double): Int {
        var s = 50 // start neutral
        when {
            changePct > 5 -> s += 30
            changePct > 2 -> s += 20
            changePct > 0 -> s += 10
            changePct < -5 -> s -= 30
            changePct < -2 -> s -= 20
            changePct < 0 -> s -= 10
        }
        return s.coerceIn(0, 100)
    }

    /** Quick sparkline scoring: Supertrend + RSI + momentum → 0-100 */
    private fun scoreSparkline(prices: List<Double>): Int {
        var s = 0
        // Supertrend bullish
        if (TechnicalAnalysis.isCurrentlyBullish(prices, 7, 2.0, 4)) s += 30
        if (TechnicalAnalysis.isCurrentlyBullish(prices, 5, 1.5, 4)) s += 15
        // Simple RSI
        val n = prices.size
        val changes = prices.zipWithNext { a, b -> b - a }
        val gains = changes.takeLast(14).filter { it > 0 }
        val losses = changes.takeLast(14).filter { it < 0 }.map { -it }
        val avgG = if (gains.isNotEmpty()) gains.average() else 0.001
        val avgL = if (losses.isNotEmpty()) losses.average() else 0.001
        val rsi = 100 - 100 / (1 + avgG / avgL)
        when {
            rsi < 30 -> s += 20  // oversold bounce potential
            rsi in 40.0..60.0 -> s += 10
            rsi > 70 -> s += 0   // overbought, less attractive
        }
        // Momentum
        if (n >= 24) {
            val pct = (prices.last() - prices[n - 24]) / prices[n - 24] * 100
            if (pct > 3) s += 15
            if (pct > 5) s += 10
        }
        return s.coerceIn(0, 100)
    }

    fun removeFromWatchlist(symbol: String, type: AssetType) {
        viewModelScope.launch { localRepo.removeFromWatchlist(symbol, type) }
    }

    fun addToWatchlist(symbol: String, name: String, type: AssetType) {
        viewModelScope.launch {
            localRepo.addToWatchlist(WatchlistItem(symbol = symbol, name = name, type = type))
        }
    }
}
