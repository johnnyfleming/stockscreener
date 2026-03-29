package com.tradescreenerai.app.ui.screens.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradescreenerai.app.data.model.AssetType
import com.tradescreenerai.app.ui.components.cards.CryptoCard
import com.tradescreenerai.app.ui.components.cards.StockCard
import com.tradescreenerai.app.ui.components.common.EmptyState
import com.tradescreenerai.app.ui.components.common.ShimmerCard
import com.tradescreenerai.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onNavigateToCryptoDetail: (String) -> Unit,
    onNavigateToStockDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    viewModel: WatchlistViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Watchlist",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.items.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // LIVE indicator
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (!state.isLoading) Color(0xFF00C853).copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        if (!state.isLoading) "● LIVE" else "○ Loading…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (!state.isLoading) Color(0xFF00C853)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                if (state.lastRefreshed > 0 && !state.isLoading) {
                                    Text(
                                        "Updated ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(state.lastRefreshed))} · ${state.nextRefreshIn}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (state.items.isNotEmpty()) {
                        // Sort button
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                WatchlistSortBy.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(sort.label)
                                                if (state.sortBy == sort) {
                                                    Spacer(Modifier.width(4.dp))
                                                    Icon(
                                                        if (state.sortDesc) Icons.Filled.ArrowDownward
                                                        else Icons.Filled.ArrowUpward,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.setSortBy(sort)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    // Refresh
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading && state.items.isNotEmpty())
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    // Search / Add
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->

        if (state.items.isEmpty() && !state.isLoading) {
            EmptyState(
                icon = Icons.Filled.StarOutline,
                title = "No items in watchlist",
                subtitle = "Search and add stocks or crypto to track them here",
                actionLabel = "Search",
                onAction = onNavigateToSearch,
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(top = 48.dp)
            )
        } else {
            val sortedItems = remember(state.items, state.sortBy, state.sortDesc, state.setupScores) {
                viewModel.sortedItems()
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = 100.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── Watchlist Intelligence Summary ────────────────────────
                if (state.setupScores.isNotEmpty() && !state.isLoading) {
                    item(key = "intelligence") {
                        WatchlistIntelligenceCard(state)
                    }
                }

                // ── Best Setup Highlight ──────────────────────────────────
                val bestSymbol = state.bestSetupSymbol
                val bestScore = bestSymbol?.let { state.setupScores[it] }
                if (bestSymbol != null && bestScore != null && bestScore.first >= 50) {
                    item(key = "best_setup") {
                        BestSetupCard(
                            symbol = bestSymbol,
                            score = bestScore.first,
                            label = bestScore.second,
                            crypto = state.cryptoPrices[bestSymbol],
                            stock = state.stockPrices[bestSymbol],
                            onClick = {
                                if (state.cryptoPrices.containsKey(bestSymbol)) {
                                    onNavigateToCryptoDetail(bestSymbol)
                                } else {
                                    onNavigateToStockDetail(bestSymbol)
                                }
                            }
                        )
                    }
                }

                // ── Error banner ──────────────────────────────────────────
                if (state.error != null) {
                    item(key = "error") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Warning, null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    state.error ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // ── Sort info pill ────────────────────────────────────────
                if (state.items.size > 1) {
                    item(key = "sort_info") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    "${state.items.size} items · ${state.sortBy.label} ${if (state.sortDesc) "↓" else "↑"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // ── Watchlist items ───────────────────────────────────────
                items(sortedItems, key = { "${it.symbol}_${it.type}" }) { item ->
                    val scoreData = state.setupScores[item.symbol]

                    when (item.type) {
                        AssetType.STOCK -> {
                            val stock = state.stockPrices[item.symbol]
                            if (stock != null) {
                                WatchlistItemWithScore(
                                    score = scoreData?.first,
                                    label = scoreData?.second
                                ) {
                                    StockCard(
                                        stock = stock,
                                        onClick = { onNavigateToStockDetail(item.symbol) },
                                        isWatchlisted = true,
                                        onWatchlistToggle = {
                                            viewModel.removeFromWatchlist(item.symbol, AssetType.STOCK)
                                        }
                                    )
                                }
                            } else {
                                ShimmerCard()
                            }
                        }
                        AssetType.CRYPTO -> {
                            val crypto = state.cryptoPrices[item.symbol]
                            if (crypto != null) {
                                WatchlistItemWithScore(
                                    score = scoreData?.first,
                                    label = scoreData?.second
                                ) {
                                    CryptoCard(
                                        crypto = crypto,
                                        onClick = { onNavigateToCryptoDetail(item.symbol) },
                                        isWatchlisted = true,
                                        onWatchlistToggle = {
                                            viewModel.removeFromWatchlist(item.symbol, AssetType.CRYPTO)
                                        }
                                    )
                                }
                            } else {
                                ShimmerCard()
                            }
                        }
                        AssetType.COMMODITY -> {
                            // Commodity watchlist items shown as stock-style cards
                            val stock = state.stockPrices[item.symbol]
                            if (stock != null) {
                                WatchlistItemWithScore(
                                    score = scoreData?.first,
                                    label = scoreData?.second
                                ) {
                                    StockCard(
                                        stock = stock,
                                        onClick = { /* TODO: navigate to commodity detail */ },
                                        isWatchlisted = true,
                                        onWatchlistToggle = {
                                            viewModel.removeFromWatchlist(item.symbol, AssetType.COMMODITY)
                                        }
                                    )
                                }
                            } else {
                                ShimmerCard()
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Watchlist Intelligence Summary Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun WatchlistIntelligenceCard(state: WatchlistState) {
    val healthColor = when {
        state.avgScore >= 60 -> GainGreen
        state.avgScore >= 40 -> Color(0xFFFFC107)
        else -> LossRed
    }
    val healthLabel = when {
        state.avgScore >= 60 -> "Bullish"
        state.avgScore >= 40 -> "Mixed"
        else -> "Bearish"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Insights, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Watchlist Intelligence",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Overall health
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = healthColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${state.avgScore}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = healthColor
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        healthLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = healthColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Avg Score",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Bullish count
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = GainGreen.copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${state.bullishCount}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = GainGreen
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bullish",
                        style = MaterialTheme.typography.labelSmall,
                        color = GainGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Score ≥50",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Bearish count
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = LossRed.copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${state.bearishCount}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = LossRed
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bearish",
                        style = MaterialTheme.typography.labelSmall,
                        color = LossRed,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Score <30",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Total tracked
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${state.setupScores.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Scored",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "of ${state.items.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Best Setup Highlight Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BestSetupCard(
    symbol: String,
    score: Int,
    label: String,
    crypto: com.tradescreenerai.app.data.model.Crypto?,
    stock: com.tradescreenerai.app.data.model.Stock?,
    onClick: () -> Unit
) {
    val name = crypto?.name ?: stock?.name ?: symbol
    val displaySymbol = crypto?.symbol ?: stock?.symbol ?: symbol
    val changePct = crypto?.changePercent24h ?: stock?.changePercent ?: 0.0
    val isPositive = changePct >= 0

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = GainGreen.copy(alpha = 0.08f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, GainGreen.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Trophy icon
            Surface(
                shape = CircleShape,
                color = GainGreen.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp, null,
                        tint = GainGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "⭐ Best Setup",
                        style = MaterialTheme.typography.labelMedium,
                        color = GainGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "$displaySymbol · $name",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$label · ${if (isPositive) "+" else ""}${"%.2f".format(changePct)}% today",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPositive) GainGreen else LossRed
                )
            }

            // Score badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GainGreen.copy(alpha = 0.2f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "$score",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = GainGreen
                    )
                    Text(
                        "score",
                        style = MaterialTheme.typography.labelSmall,
                        color = GainGreen.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Score overlay wrapper for each watchlist item
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun WatchlistItemWithScore(
    score: Int?,
    label: String?,
    content: @Composable () -> Unit
) {
    if (score == null) {
        content()
        return
    }

    val scoreColor = when {
        score >= 70 -> GainGreen
        score >= 50 -> Color(0xFFFFC107)
        score >= 30 -> Color(0xFFFF9800)
        else -> LossRed
    }

    Column {
        content()
        // Score strip below the card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Score progress bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = (score / 100f).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(scoreColor)
                )
            }
            Text(
                "$score",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )
            Text(
                label ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
