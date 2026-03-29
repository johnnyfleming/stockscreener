package com.tradescreenerai.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.repository.CryptoRepository
import com.tradescreenerai.app.data.repository.StockRepository
import com.tradescreenerai.app.util.Constants
import com.tradescreenerai.app.util.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val stockResults: List<Stock> = emptyList(),
    val cryptoResults: List<Crypto> = emptyList(),
    val commodityResults: List<CommodityType> = emptyList(),
    val isSearching: Boolean = false,
    val recentSearches: List<String> = emptyList()
)

class SearchViewModel : ViewModel() {
    private val stockRepo  = StockRepository()
    private val cryptoRepo = CryptoRepository()

    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    // All known stock symbols from the app's data pools
    private val allKnownSymbols: List<String> by lazy {
        (Constants.RANKING_STOCK_POOL + Constants.TOP_STOCKS).distinct()
    }

    fun search(query: String) {
        _state.value = _state.value.copy(query = query)

        if (query.isEmpty()) {
            _state.value = _state.value.copy(
                stockResults = emptyList(),
                cryptoResults = emptyList(),
                commodityResults = emptyList(),
                isSearching = false
            )
            return
        }

        val q = query.uppercase().trim()

        // ── Instant: commodities (local, no network) ──────────────────────────
        val commodityMatches = CommodityType.entries.filter { ct ->
            ct.displayName.contains(query, ignoreCase = true) ||
            ct.name.contains(query, ignoreCase = true) ||
            ct.category.displayName.contains(query, ignoreCase = true) ||
            ct.etfTicker.contains(query, ignoreCase = true)
        }
        _state.value = _state.value.copy(commodityResults = commodityMatches)

        // ── Instant: stocks from local pool ───────────────────────────────────
        val localSymbolMatches = allKnownSymbols.filter { sym ->
            sym.contains(q) || sym.startsWith(q)
        }.take(15)

        if (localSymbolMatches.isNotEmpty()) {
            val instant = localSymbolMatches.map {
                Stock(symbol = it, name = it, price = 0.0, change = 0.0, changePercent = 0.0)
            }
            _state.value = _state.value.copy(stockResults = instant)
        }

        if (query.length < 2) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            _state.value = _state.value.copy(isSearching = true)

            // Enrich local matches with real prices (Yahoo Finance batch)
            val enrichJob = if (localSymbolMatches.isNotEmpty()) async {
                when (val r = stockRepo.getBatchQuotes(localSymbolMatches)) {
                    is Resource.Success -> {
                        val map = (r.data ?: emptyList()).associateBy { it.symbol }
                        val enriched = localSymbolMatches.map { sym ->
                            map[sym] ?: _state.value.stockResults.find { it.symbol == sym }
                            ?: Stock(symbol = sym, name = sym, price = 0.0, change = 0.0, changePercent = 0.0)
                        }
                        _state.value = _state.value.copy(stockResults = enriched)
                    }
                    else -> {}
                }
            } else null

            // Alpha Vantage symbol search (broader, any stock worldwide)
            val avJob = async {
                when (val result = stockRepo.searchSymbols(query)) {
                    is Resource.Success -> {
                        val avResults = result.data ?: emptyList()
                        val existing  = _state.value.stockResults.map { it.symbol }.toSet()
                        val merged = (_state.value.stockResults + avResults.filter { it.symbol !in existing })
                            .distinctBy { it.symbol }.take(20)
                        _state.value = _state.value.copy(stockResults = merged)
                    }
                    else -> {}
                }
            }

            // Crypto search
            val cryptoJob = async {
                when (val result = cryptoRepo.searchCrypto(query)) {
                    is Resource.Success -> _state.value = _state.value.copy(cryptoResults = result.data ?: emptyList())
                    else -> {}
                }
            }

            enrichJob?.await()
            avJob.await()
            cryptoJob.await()
            _state.value = _state.value.copy(isSearching = false)
        }
    }

    fun clearSearch() {
        _state.value = SearchState()
    }
}
