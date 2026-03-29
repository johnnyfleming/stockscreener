package com.tradescreenerai.app.ui.screens.portfolio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.repository.LocalDataRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PortfolioState(
    val items: List<PortfolioItem> = emptyList(),
    val totalValue: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalPL: Double = 0.0,
    val totalPLPercent: Double = 0.0,
    val showAddDialog: Boolean = false
)

class PortfolioViewModel(application: Application) : AndroidViewModel(application) {
    private val localRepo = LocalDataRepository(application)

    private val _state = MutableStateFlow(PortfolioState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            localRepo.portfolio.collect { items ->
                val totalValue = items.sumOf { it.totalValue }
                val totalCost = items.sumOf { it.totalCost }
                val totalPL = totalValue - totalCost
                val totalPLPercent = if (totalCost > 0) (totalPL / totalCost) * 100 else 0.0
                _state.value = _state.value.copy(
                    items = items,
                    totalValue = totalValue,
                    totalCost = totalCost,
                    totalPL = totalPL,
                    totalPLPercent = totalPLPercent
                )
            }
        }
    }

    fun showAddDialog() { _state.value = _state.value.copy(showAddDialog = true) }
    fun hideAddDialog() { _state.value = _state.value.copy(showAddDialog = false) }

    fun addHolding(symbol: String, name: String, type: AssetType, quantity: Double, buyPrice: Double) {
        viewModelScope.launch {
            localRepo.addToPortfolio(
                PortfolioItem(
                    symbol = symbol,
                    name = name,
                    type = type,
                    quantity = quantity,
                    buyPrice = buyPrice,
                    currentPrice = buyPrice
                )
            )
            hideAddDialog()
        }
    }

    fun removeHolding(symbol: String) {
        viewModelScope.launch { localRepo.removeFromPortfolio(symbol) }
    }
}

