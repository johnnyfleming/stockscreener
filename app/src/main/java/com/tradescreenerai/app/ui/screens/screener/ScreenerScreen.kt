package com.tradescreenerai.app.ui.screens.screener

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradescreenerai.app.data.model.SortBy
import com.tradescreenerai.app.data.repository.CryptoDataSource
import com.tradescreenerai.app.ui.components.cards.CommodityTradingCard
import com.tradescreenerai.app.ui.components.cards.CryptoCard
import com.tradescreenerai.app.ui.components.cards.StockCard
import com.tradescreenerai.app.ui.components.cards.StockRankingCard
import com.tradescreenerai.app.ui.components.common.*
import com.tradescreenerai.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenerScreen(
    onNavigateToCryptoDetail: (String) -> Unit,
    onNavigateToStockDetail: (String) -> Unit,
    viewModel: ScreenerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val liveGreen = Color(0xFF00C853)

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Screener",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                // LIVE status row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (!state.isLoading) liveGreen.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            if (!state.isLoading) "● LIVE" else "○ Loading…",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!state.isLoading) liveGreen
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    if (state.lastUpdated > 0L && !state.isLoading) {
                        Text(
                            "Updated ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(state.lastUpdated))}  •  ${state.nextRefreshIn}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row {
                IconButton(onClick = { viewModel.toggleFilters() }) {
                    Icon(
                        Icons.Outlined.FilterList,
                        "Filters",
                        tint = if (state.showFilters) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { viewModel.loadData() }) {
                    if (state.isLoading)
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else
                        Icon(Icons.Filled.Refresh, "Refresh")
                }
            }
        }

        // Tabs
        // ── Fallback-API notice ───────────────────────────────────────────────
        if (state.activeTab == 0 && state.dataSource == CryptoDataSource.COINPAPRIKA) {
            Surface(
                color = Color(0xFFFFF8E1),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF57F17),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "CoinGecko rate-limited – using CoinPaprika (auto-retries in ~90s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF57F17)
                    )
                }
            }
        }

        // Tabs
        val tabTitles = listOf("Crypto", "Penny Stocks", "⚡ Short-Term", "🏗️ Long-Term",
            "🚀 Breakouts", "🔄 Reversals", "🌍 Commodities")
        ScrollableTabRow(
            selectedTabIndex = state.activeTab,
            modifier = Modifier.padding(horizontal = 4.dp),
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = state.activeTab == index,
                    onClick = { viewModel.setTab(index) },
                    text = {
                        Text(
                            title,
                            fontWeight = if (state.activeTab == index) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                )
            }
        }

        // Filters panel (only for Crypto and Penny Stocks tabs)
        AnimatedVisibility(
            visible = state.showFilters && state.activeTab <= 1,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            FilterPanel(state, viewModel)
        }

        // Sort chips (only for Crypto and Penny Stocks tabs)
        if (state.activeTab <= 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SortBy.entries.forEach { sortBy ->
                    val isActive = state.sortBy == sortBy
                    FilterChip(
                        selected = isActive,
                        onClick = { viewModel.updateSort(sortBy) },
                        label = {
                            Text(
                                sortBy.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        trailingIcon = if (isActive) {
                            {
                                Icon(
                                    if (state.sortDesc) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else null,
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
        }

        // Results count (only for tabs 0-1 that use filters)
        if (state.activeTab <= 1) {
            val count = if (state.activeTab == 0) state.filteredCryptos.size else state.filteredStocks.size
            Text(
                "$count results",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        // List
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.isLoading && state.activeTab <= 1) {
                items(5) { ShimmerCard() }
            } else when (state.activeTab) {
                0 -> {
                    // Crypto tab
                    items(state.filteredCryptos, key = { it.id }) { crypto ->
                        CryptoCard(
                            crypto = crypto,
                            onClick = { onNavigateToCryptoDetail(crypto.id) }
                        )
                    }
                }
                1 -> {
                    // Penny Stocks tab — OFFLINE banner when market is closed
                    if (!state.isMarketOpen || state.stockError != null) {
                        item {
                            Surface(
                                color = if (!state.isMarketOpen) Color(0xFF1A237E).copy(alpha = 0.18f)
                                        else Color(0xFFB71C1C).copy(alpha = 0.12f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        if (!state.isMarketOpen) "🌙" else "⚠️",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Column {
                                        Text(
                                            if (!state.isMarketOpen) "Market Offline"
                                            else "Live data unavailable",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!state.isMarketOpen)
                                                Color(0xFF90CAF9) else Color(0xFFEF9A9A)
                                        )
                                        Text(
                                            state.stockError
                                                ?: "US market closed · graphs & analysis still available",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state.filteredStocks.isEmpty() && !state.isLoading) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("📊", style = MaterialTheme.typography.displaySmall)
                                Text(
                                    if (!state.isMarketOpen)
                                        "No cached data yet — tap Refresh to load offline prices"
                                    else
                                        "No stocks matched the current filters",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                if (!state.isMarketOpen) {
                                    Text(
                                        "Prices reflect the last session close.\nTap any stock to view its chart and analyse future trends.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(state.filteredStocks, key = { it.symbol }) { stock ->
                            StockCard(
                                stock = stock,
                                onClick = { onNavigateToStockDetail(stock.symbol) },
                                showSignal = true,
                                isOffline = !state.isMarketOpen
                            )
                        }
                    }
                }
                2 -> {
                    // Short-Term Stocks
                    item { DiscoveryHeader("⚡ Best Short-Term Stocks", "Ranked by momentum, MACD, RSI, volume & breakout potential", state.discoveryLoading) }
                    itemsIndexed(state.shortTermStocks) { index, ranked ->
                        StockRankingCard(
                            rank = index + 1,
                            rankedStock = ranked,
                            onClick = { onNavigateToStockDetail(ranked.stock.symbol) }
                        )
                    }
                    if (state.shortTermStocks.isEmpty() && !state.discoveryLoading) {
                        item { EmptyDiscovery("No short-term opportunities found yet") }
                    }
                }
                3 -> {
                    // Long-Term Stocks
                    item { DiscoveryHeader("🏗️ Best Long-Term Stocks", "Ranked by trend strength, EMA alignment, consistency & low volatility", state.discoveryLoading) }
                    itemsIndexed(state.longTermStocks) { index, ranked ->
                        StockRankingCard(
                            rank = index + 1,
                            rankedStock = ranked,
                            onClick = { onNavigateToStockDetail(ranked.stock.symbol) }
                        )
                    }
                    if (state.longTermStocks.isEmpty() && !state.discoveryLoading) {
                        item { EmptyDiscovery("No long-term picks found yet") }
                    }
                }
                4 -> {
                    // Breakouts
                    item { DiscoveryHeader("🚀 Breakout Candidates", "Stocks breaking above key resistance with high volume", state.discoveryLoading) }
                    itemsIndexed(state.breakoutStocks) { index, ranked ->
                        StockRankingCard(
                            rank = index + 1,
                            rankedStock = ranked,
                            onClick = { onNavigateToStockDetail(ranked.stock.symbol) }
                        )
                    }
                    if (state.breakoutStocks.isEmpty() && !state.discoveryLoading) {
                        item { EmptyDiscovery("No breakout candidates detected right now") }
                    }
                }
                5 -> {
                    // Reversals
                    item { DiscoveryHeader("🔄 Reversal Candidates", "Oversold stocks showing recovery signals", state.discoveryLoading) }
                    itemsIndexed(state.reversalStocks) { index, ranked ->
                        StockRankingCard(
                            rank = index + 1,
                            rankedStock = ranked,
                            onClick = { onNavigateToStockDetail(ranked.stock.symbol) }
                        )
                    }
                    if (state.reversalStocks.isEmpty() && !state.discoveryLoading) {
                        item { EmptyDiscovery("No reversal candidates detected right now") }
                    }
                }
                6 -> {
                    // Commodity Movers
                    item { DiscoveryHeader("🌍 Commodity Movers", "Top moving commodities ranked by daily change  •  via ETF proxies (GLD, USO, UNG…)", state.discoveryLoading) }
                    items(state.commodityMovers) { ranking ->
                        CommodityTradingCard(
                            ranking = ranking,
                            onClick = { /* Navigate would need commodity detail route */ }
                        )
                    }
                    if (state.commodityMovers.isEmpty() && !state.discoveryLoading) {
                        item { EmptyDiscovery("No commodity data yet — tap Refresh") }
                    }
                }
            }

            // Bottom padding for nav bar
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun DiscoveryHeader(title: String, subtitle: String, isLoading: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ElectricBlue.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun EmptyDiscovery(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FilterPanel(state: ScreenerState, viewModel: ScreenerViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { viewModel.resetFilters() }) {
                    Text("Reset")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Price range
            Text("Price Range", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = if (state.minPrice > 0) state.minPrice.toString() else "",
                    onValueChange = { viewModel.updateMinPrice(it.toDoubleOrNull() ?: 0.0) },
                    label = { Text("Min") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = if (state.maxPrice < Double.MAX_VALUE) state.maxPrice.toString() else "",
                    onValueChange = { viewModel.updateMaxPrice(it.toDoubleOrNull() ?: Double.MAX_VALUE) },
                    label = { Text("Max") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(12.dp))

            // Change range
            Text("Change % Range", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.minChange.toString(),
                    onValueChange = { viewModel.updateMinChange(it.toDoubleOrNull() ?: -100.0) },
                    label = { Text("Min %") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.maxChange.toString(),
                    onValueChange = { viewModel.updateMaxChange(it.toDoubleOrNull() ?: 100.0) },
                    label = { Text("Max %") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(12.dp))

            // Penny stocks toggle (Stocks tab only)
            if (state.activeTab == 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Show Penny Stocks Only (< \$5)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Filters stocks priced under \$5",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.showPennyStocksOnly,
                        onCheckedChange = { viewModel.togglePennyFilter() }
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Minimum volume filter
                Text("Min Volume (avoid dead stocks)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = if (state.minVolumeFilter > 0L) state.minVolumeFilter.toString() else "",
                    onValueChange = { viewModel.updateMinVolumeFilter(it.toLongOrNull() ?: 0L) },
                    label = { Text("Min Volume (e.g. 500000)") },
                    placeholder = { Text("0 = no filter") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}

