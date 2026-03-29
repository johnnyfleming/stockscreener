package com.tradescreenerai.app.ui.screens.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradescreenerai.app.data.model.NewsArticle
import com.tradescreenerai.app.data.repository.NewsRepository
import com.tradescreenerai.app.util.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NewsState(
    val articles: List<NewsArticle> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
    val activeCategory: String = "All",
    val selectedSymbol: String? = null,
    val lastRefreshed: Long = 0L,
    val nextRefreshIn: Int = 45
)

class NewsViewModel : ViewModel() {
    private val newsRepo = NewsRepository()
    private val _state = MutableStateFlow(NewsState())
    val state = _state.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        loadNews()
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                for (sec in 45 downTo 1) {
                    _state.value = _state.value.copy(nextRefreshIn = sec)
                    delay(1_000L)
                }
                // Auto-refresh regardless of search state (silently update)
                silentRefresh()
            }
        }
    }

    private suspend fun silentRefresh() {
        val s = _state.value
        val result = when {
            s.selectedSymbol != null ->
                newsRepo.getCompanyNews(s.selectedSymbol)
            s.searchQuery.isNotBlank() ->
                newsRepo.searchNews(s.searchQuery)
            s.activeCategory != "All" -> {
                setCategory(s.activeCategory); return
            }
            else -> newsRepo.getMarketNews()
        }
        when (result) {
            is Resource.Success -> _state.value = _state.value.copy(
                articles = deduplicateNews((_state.value.articles + (result.data ?: emptyList()))),
                lastRefreshed = System.currentTimeMillis()
            )
            else -> {}
        }
    }

    /** Deduplicate by normalised title (removes near-duplicate headlines). */
    private fun deduplicateNews(list: List<NewsArticle>): List<NewsArticle> {
        val seen = mutableSetOf<String>()
        return list.filter { article ->
            val key = article.title.lowercase().filter { it.isLetterOrDigit() }.take(60)
            seen.add(key)
        }.sortedByDescending { it.publishedAt }
    }

    fun filterBySymbol(symbol: String) {
        _state.value = _state.value.copy(selectedSymbol = symbol, activeCategory = symbol)
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            when (val result = newsRepo.getCompanyNews(symbol)) {
                is Resource.Success -> _state.value = _state.value.copy(
                    articles = result.data ?: emptyList(),
                    isLoading = false,
                    lastRefreshed = System.currentTimeMillis()
                )
                is Resource.Error -> _state.value = _state.value.copy(
                    error = result.message, isLoading = false
                )
                is Resource.Loading -> {}
            }
        }
    }

    fun clearSymbolFilter() {
        _state.value = _state.value.copy(selectedSymbol = null, activeCategory = "All")
        loadNews()
    }

    fun loadNews() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            when (val result = newsRepo.getMarketNews()) {
                is Resource.Success -> _state.value = _state.value.copy(
                    articles = result.data ?: emptyList(),
                    isLoading = false,
                    lastRefreshed = System.currentTimeMillis()
                )
                is Resource.Error -> _state.value = _state.value.copy(
                    error = result.message, isLoading = false
                )
                is Resource.Loading -> {}
            }
        }
    }

    fun searchNews(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        if (query.isBlank()) { loadNews(); return }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            when (val result = newsRepo.searchNews(query)) {
                is Resource.Success -> _state.value = _state.value.copy(
                    articles = result.data ?: emptyList(), isLoading = false
                )
                is Resource.Error -> _state.value = _state.value.copy(
                    error = result.message, isLoading = false
                )
                is Resource.Loading -> {}
            }
        }
    }

    fun setCategory(category: String) {
        _state.value = _state.value.copy(activeCategory = category)
        when (category) {
            "All"      -> loadNews()
            "Stocks"   -> searchNews("stocks market trading")
            "Crypto"   -> searchNews("cryptocurrency bitcoin ethereum")
            "Economy"  -> searchNews("economy inflation interest rates")
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }
}

