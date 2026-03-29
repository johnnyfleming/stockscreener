package com.tradescreenerai.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.repository.CryptoRepository
import com.tradescreenerai.app.data.repository.MarketDataRepository
import com.tradescreenerai.app.data.repository.NewsRepository
import com.tradescreenerai.app.data.repository.StockRepository
import com.tradescreenerai.app.util.Resource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardState(
    val cryptos: List<Crypto> = emptyList(),
    val trendingCryptos: List<Crypto> = emptyList(),
    val topGainers: List<Stock> = emptyList(),
    val topLosers: List<Stock> = emptyList(),
    val latestNews: List<NewsArticle> = emptyList(),
    val fearGreed: FearGreedIndex? = null,
    val whaleAlerts: List<WhaleTransaction> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val lastUpdated: Long = 0L,
    val nextRefreshIn: Int = 60
)

class DashboardViewModel : ViewModel() {
    private val cryptoRepo = CryptoRepository()
    private val stockRepo = StockRepository()
    private val newsRepo = NewsRepository()
    private val marketDataRepo = MarketDataRepository()

    private val _state = MutableStateFlow(DashboardState())
    val state = _state.asStateFlow()

    companion object {
        const val REFRESH_SECONDS = 60L
    }

    init {
        startLiveUpdates()
    }

    private fun startLiveUpdates() {
        viewModelScope.launch {
            while (true) {
                loadDashboard()
                for (sec in REFRESH_SECONDS.toInt() downTo 1) {
                    _state.value = _state.value.copy(nextRefreshIn = sec)
                    delay(1_000L)
                }
            }
        }
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            // Load crypto markets
            launch {
                when (val result = cryptoRepo.getMarkets(perPage = 20)) {
                    is Resource.Success -> _state.value = _state.value.copy(cryptos = result.data ?: emptyList())
                    is Resource.Error -> _state.value = _state.value.copy(error = result.message)
                    is Resource.Loading -> {}
                }
            }

            // Load trending crypto
            launch {
                when (val result = cryptoRepo.getTrending()) {
                    is Resource.Success -> _state.value = _state.value.copy(trendingCryptos = result.data ?: emptyList())
                    else -> {}
                }
            }

            // Load gainers/losers
            launch {
                when (val result = stockRepo.getTopGainersLosers()) {
                    is Resource.Success -> {
                        result.data?.let { (gainers, losers) ->
                            _state.value = _state.value.copy(
                                topGainers = gainers.take(10),
                                topLosers = losers.take(10)
                            )
                        }
                    }
                    else -> {}
                }
            }

            // Load news (less frequent — only on first load or manual refresh)
            if (_state.value.latestNews.isEmpty()) {
                launch {
                    when (val result = newsRepo.getMarketNews()) {
                        is Resource.Success -> _state.value = _state.value.copy(latestNews = result.data?.take(5) ?: emptyList())
                        else -> {}
                    }
                }
            }

            // Load Fear & Greed Index
            launch {
                when (val result = marketDataRepo.getFearGreedIndex()) {
                    is Resource.Success -> _state.value = _state.value.copy(fearGreed = result.data)
                    else -> {}
                }
            }

            // Load Whale Alerts
            launch {
                when (val result = marketDataRepo.getWhaleAlerts()) {
                    is Resource.Success -> _state.value = _state.value.copy(whaleAlerts = result.data ?: emptyList())
                    else -> {}
                }
            }

            _state.value = _state.value.copy(
                isLoading = false,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    fun refresh() = loadDashboard()
}


