package com.tradescreenerai.app.ui.screens.commodities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.repository.CommodityRepository
import com.tradescreenerai.app.domain.BeginnerInsightEngine
import com.tradescreenerai.app.domain.MarketDiscoveryEngine
import com.tradescreenerai.app.util.Resource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CommoditiesState(
    val commodities: List<Commodity> = emptyList(),
    val rankings: List<CommodityRanking> = emptyList(),
    val viewMode: CommodityViewMode = CommodityViewMode.STANDARD,
    val selectedCategory: CommodityCategory? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val lastUpdated: Long = 0L,
    val nextRefreshIn: Int = 300,
    val isBeginnerMode: Boolean = true,
    val selectedInsight: BeginnerInsight? = null,
    val selectedCommodity: Commodity? = null,
    val dataLimitationNote: String = ""
)

enum class CommodityViewMode(val label: String) {
    STANDARD("Standard"),
    TRADING("Trading")
}

class CommoditiesViewModel : ViewModel() {
    private val commodityRepo = CommodityRepository()

    private val _state = MutableStateFlow(CommoditiesState())
    val state = _state.asStateFlow()

    companion object {
        const val REFRESH_SECONDS = 300L  // 5 minutes (AV rate limits)
    }

    init {
        startLiveUpdates()
    }

    private fun startLiveUpdates() {
        viewModelScope.launch {
            while (true) {
                loadCommodityData()
                for (sec in REFRESH_SECONDS.toInt() downTo 1) {
                    _state.value = _state.value.copy(nextRefreshIn = sec)
                    delay(1_000L)
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { loadCommodityData() }
    }

    fun setViewMode(mode: CommodityViewMode) {
        _state.value = _state.value.copy(viewMode = mode)
    }

    fun setCategory(category: CommodityCategory?) {
        _state.value = _state.value.copy(selectedCategory = category)
    }

    fun toggleBeginnerMode() {
        _state.value = _state.value.copy(isBeginnerMode = !_state.value.isBeginnerMode)
    }

    fun selectCommodityForInsight(commodity: Commodity) {
        val insight = BeginnerInsightEngine.generateCommodityInsight(commodity)
        _state.value = _state.value.copy(selectedInsight = insight, selectedCommodity = commodity)
    }

    fun dismissInsight() {
        _state.value = _state.value.copy(selectedInsight = null, selectedCommodity = null)
    }

    private suspend fun loadCommodityData() {
        _state.value = _state.value.copy(isLoading = true, error = null)

        try {
            when (val result = commodityRepo.getAllCommodities()) {
                is Resource.Success -> {
                    val commodities = result.data ?: emptyList()
                    val rankings = MarketDiscoveryEngine.rankCommodityMovers(commodities)

                    _state.value = _state.value.copy(
                        commodities = commodities,
                        rankings = rankings,
                        isLoading = false,
                        lastUpdated = System.currentTimeMillis(),
                        dataLimitationNote = buildLimitationNote()
                    )
                }
                is Resource.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.message ?: "Failed to load commodities",
                        dataLimitationNote = buildLimitationNote()
                    )
                }
                else -> {}
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    private fun buildLimitationNote(): String = buildString {
        append("📌 Data Provider Notes:\n")
        append("• Commodity prices are from Alpha Vantage (daily data)\n")
        append("• Free tier: 25 API calls/day — data is cached for 1 hour\n")
        append("• For live intraday commodity futures, you would need:\n")
        append("  – Polygon.io Stocks Advanced plan ($199/mo) for futures\n")
        append("  – Tradermade paid plan for real-time commodity quotes\n")
        append("  – Interactive Brokers API for live futures streaming\n")
        append("• Current implementation uses daily-resolution data")
    }
}

