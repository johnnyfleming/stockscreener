package com.tradescreenerai.app.ui.screens.stocks

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.ui.components.cards.BeginnerExplanationCard
import com.tradescreenerai.app.ui.components.cards.StockRankingCard
import com.tradescreenerai.app.ui.theme.*
import com.tradescreenerai.app.util.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StocksScreen(
    onNavigateToStockDetail: (String) -> Unit,
    onNavigateToDayTrader: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToNews: () -> Unit = {},
    viewModel: StocksViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs = listOf("⚡ Short", "🏗️ Long", "📚 Beginner", "🎯 Day Trade", "🌱 LT Beginner", "📰 Advanced")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Stocks", fontWeight = FontWeight.Bold)
                        if (state.lastUpdated > 0) {
                            Text(
                                "Next refresh in ${state.nextRefreshIn}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleBeginnerMode() }) {
                        Icon(
                            Icons.Filled.School,
                            contentDescription = "Beginner Mode",
                            tint = if (state.isBeginnerMode) GoldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToNews) {
                        Icon(Icons.Filled.Newspaper, contentDescription = "News", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = state.activeTab, edgePadding = 8.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.activeTab == index,
                        onClick = { viewModel.setTab(index) },
                        text = { Text(title, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            AnimatedVisibility(visible = state.isBeginnerMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = GoldAccent.copy(alpha = 0.1f)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.School, "Beginner", tint = GoldAccent)
                        Spacer(Modifier.width(8.dp))
                        Text("📚 Beginner Mode ON — Tap any stock for a simple explanation", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            when {
                state.isLoading && state.shortTermStocks.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Analyzing stocks...", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Ranking ${Constants.RANKING_STOCK_POOL.size} stocks dynamically",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                state.error != null && state.shortTermStocks.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚠️ ${state.error}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                        }
                    }
                }
                else -> {
                    when (state.activeTab) {
                        0 -> ShortTermTab(state, onNavigateToStockDetail, onNavigateToDayTrader, viewModel, onNavigateToNews)
                        1 -> LongTermTab(state, onNavigateToStockDetail, viewModel, onNavigateToNews)
                        2 -> BeginnerTab(state, onNavigateToStockDetail, viewModel)
                        3 -> DayTraderTab(state, onNavigateToDayTrader)
                        4 -> BeginnerLongTermTab(state, onNavigateToStockDetail, viewModel)
                        5 -> AdvancedNewsTab(state, onNavigateToStockDetail, viewModel)
                    }
                }
            }
        }

        if (state.selectedInsight != null && state.selectedStock != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissInsight() },
                title = { Text("📚 ${state.selectedStock!!.symbol} Explained") },
                text = { BeginnerExplanationCard(insight = state.selectedInsight!!) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissInsight() }) { Text("Got it!") }
                }
            )
        }
    }
}

// ── Short-Term Tab ─────────────────────────────────────────────────────────────
@Composable
private fun ShortTermTab(
    state: StocksState,
    onNavigateToStockDetail: (String) -> Unit,
    onNavigateToDayTrader: (String) -> Unit,
    viewModel: StocksViewModel,
    onNavigateToNews: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Surface(shape = RoundedCornerShape(12.dp), color = BuySignalColor.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("⚡ Best Short-Term Stocks", fontWeight = FontWeight.Bold)
                    Text("Ranked by momentum, MACD, RSI, volume, and breakout potential", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        itemsIndexed(state.shortTermStocks) { index, rankedStock ->
            StockRankingCard(
                rank = index + 1, rankedStock = rankedStock,
                onClick = { if (state.isBeginnerMode) viewModel.selectStockForInsight(rankedStock.stock) else onNavigateToStockDetail(rankedStock.stock.symbol) },
                onDayTrader = { onNavigateToDayTrader(rankedStock.stock.symbol) },
                onNews = onNavigateToNews
            )
        }
        if (state.shortTermStocks.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No short-term opportunities found right now", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

// ── Long-Term Tab ──────────────────────────────────────────────────────────────
@Composable
private fun LongTermTab(
    state: StocksState,
    onNavigateToStockDetail: (String) -> Unit,
    viewModel: StocksViewModel,
    onNavigateToNews: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Surface(shape = RoundedCornerShape(12.dp), color = ElectricBlue.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("🏗️ Best Long-Term Stocks", fontWeight = FontWeight.Bold)
                    Text("Ranked by trend strength, EMA alignment, consistency, and low volatility", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        itemsIndexed(state.longTermStocks) { index, rankedStock ->
            StockRankingCard(
                rank = index + 1, rankedStock = rankedStock,
                onClick = { if (state.isBeginnerMode) viewModel.selectStockForInsight(rankedStock.stock) else onNavigateToStockDetail(rankedStock.stock.symbol) },
                onNews = onNavigateToNews
            )
        }
        if (state.longTermStocks.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No long-term picks found right now", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

// ── Beginner Tab ───────────────────────────────────────────────────────────────
@Composable
private fun BeginnerTab(state: StocksState, onNavigateToStockDetail: (String) -> Unit, viewModel: StocksViewModel) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(shape = RoundedCornerShape(12.dp), color = GoldAccent.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📚 Beginner's Guide to Stocks", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Stocks represent ownership in a company. When you buy a stock, you own a tiny piece of that company. Stock prices go up when more people want to buy, and down when more people sell.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("🔑 Key terms:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    Text("• Short-term = days to weeks", style = MaterialTheme.typography.bodySmall)
                    Text("• Long-term = months to years", style = MaterialTheme.typography.bodySmall)
                    Text("• RSI = measures if a stock is overbought or oversold", style = MaterialTheme.typography.bodySmall)
                    Text("• MACD = shows momentum direction", style = MaterialTheme.typography.bodySmall)
                    Text("• EMA = smoothed average price over time", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("Top Stocks with Simple Explanations", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        val combined = (state.shortTermStocks.take(5) + state.longTermStocks.take(5)).distinctBy { it.stock.symbol }
        itemsIndexed(combined) { _, rankedStock ->
            val insight = com.tradescreenerai.app.domain.BeginnerInsightEngine.generateStockInsight(rankedStock.stock, rankedStock.technicalData)
            Card(modifier = Modifier.fillMaxWidth().clickable { onNavigateToStockDetail(rankedStock.stock.symbol) }, shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(rankedStock.stock.symbol, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(rankedStock.stock.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text("$${String.format(java.util.Locale.US, "%.2f", rankedStock.stock.price)}", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    BeginnerExplanationCard(insight = insight)
                }
            }
        }
    }
}

// ── Day Trader Tab ─────────────────────────────────────────────────────────────
@Composable
private fun DayTraderTab(state: StocksState, onNavigateToDayTrader: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Surface(shape = RoundedCornerShape(12.dp), color = HoldSignalColor.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("🎯 Day Trader Mode", fontWeight = FontWeight.Bold)
                    Text("Select a stock to enter real-time day trading view with live signals", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val dayTraderCandidates = state.shortTermStocks.filter { it.score >= 40 }.sortedByDescending { it.momentumScore + it.volumeScore }
        itemsIndexed(dayTraderCandidates) { _, rankedStock ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onNavigateToDayTrader(rankedStock.stock.symbol) }, shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rankedStock.stock.symbol, fontWeight = FontWeight.Bold)
                        Text("$${String.format(java.util.Locale.US, "%.2f", rankedStock.stock.price)}", style = MaterialTheme.typography.bodyMedium)
                        Text("${if (rankedStock.stock.changePercent >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.2f", rankedStock.stock.changePercent)}%", color = if (rankedStock.stock.changePercent >= 0) GainGreen else LossRed, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Score: ${rankedStock.score}", fontWeight = FontWeight.Bold, color = ElectricBlue)
                        if (rankedStock.reasons.isNotEmpty()) Text(rankedStock.reasons.first(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Bolt, "Day Trade", tint = HoldSignalColor, modifier = Modifier.size(28.dp))
                }
            }
        }
        if (dayTraderCandidates.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No day trading candidates right now", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

// ── Beginner Long-Term Tab (NEW) ───────────────────────────────────────────────
@Composable
private fun BeginnerLongTermTab(
    state: StocksState,
    onNavigateToStockDetail: (String) -> Unit,
    viewModel: StocksViewModel
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = GainGreen.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🌱 Best Long-Term Stocks for Beginners", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "These are well-known, stable companies that have performed reliably over many years. " +
                        "Perfect for a \"buy and hold\" strategy — invest and let time do the work.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = GainGreen.copy(alpha = 0.15f)) {
                        Text(
                            "💡 Tip: These stocks are generally safer for beginners because they are large, established companies with strong brand recognition and consistent earnings.",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
        if (state.beginnerLongTermStocks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (state.isLoading) CircularProgressIndicator()
                    else Text("Loading beginner picks…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        itemsIndexed(state.beginnerLongTermStocks) { index, rankedStock ->
            BeginnerLongTermCard(
                rank      = index + 1,
                rankedStock = rankedStock,
                onClick   = { onNavigateToStockDetail(rankedStock.stock.symbol) }
            )
        }
    }
}

@Composable
private fun BeginnerLongTermCard(rank: Int, rankedStock: RankedStock, onClick: () -> Unit) {
    val stock   = rankedStock.stock
    val isUp    = stock.changePercent >= 0
    val color   = if (isUp) GainGreen else LossRed
    // Safety badge color based on score
    val safetyColor = when {
        rankedStock.score >= 90 -> GainGreen
        rankedStock.score >= 75 -> ElectricBlue
        else                    -> HoldSignalColor
    }
    val safetyLabel = when {
        rankedStock.score >= 90 -> "⭐ Very Safe"
        rankedStock.score >= 75 -> "✅ Safe"
        else                    -> "🔵 Moderate"
    }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Rank badge
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(safetyColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text("$rank", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = safetyColor)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stock.symbol, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text(stock.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$${String.format(java.util.Locale.US, "%.2f", stock.price)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${if (isUp) "▲" else "▼"} ${String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(stock.changePercent))}%",
                        color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            // Safety badge
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(6.dp), color = safetyColor.copy(alpha = 0.12f)) {
                    Text(safetyLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, color = safetyColor, fontWeight = FontWeight.SemiBold)
                }
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)) {
                    Text("🏗️ Long-Term", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
                if (stock.dividend > 0) {
                    Surface(shape = RoundedCornerShape(6.dp), color = GoldAccent.copy(alpha = 0.12f)) {
                        Text("💰 Dividend", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall, color = GoldAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Why it's good for beginners
            if (rankedStock.reasons.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                    Text(rankedStock.reasons.first(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp))
                }
            }
            // Quick stats
            if (stock.volume > 0 || stock.marketCap > 0) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (stock.marketCap > 0) {
                        Column {
                            Text("Market Cap", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatMarketCap(stock.marketCap), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (stock.pe > 0) {
                        Column {
                            Text("P/E Ratio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(String.format(java.util.Locale.US, "%.1f", stock.pe), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

// ── Advanced + News Tab (NEW) ──────────────────────────────────────────────────
@Composable
private fun AdvancedNewsTab(
    state: StocksState,
    onNavigateToStockDetail: (String) -> Unit,
    viewModel: StocksViewModel
) {
    val context = LocalContext.current

    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = LossRed.copy(alpha = 0.06f), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📰 Advanced Stocks + Live News", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "These are complex, high-volatility stocks. Each card shows the latest news headlines affecting that stock. " +
                        "Not recommended for beginners — requires research and risk tolerance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.isLoadingAdvancedNews) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Fetching live news…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (state.advancedStocks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (state.isLoading) CircularProgressIndicator()
                    else Text("Loading advanced stocks…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(state.advancedStocks, key = { it.stock.symbol }) { rankedStock ->
            val stock   = rankedStock.stock
            val isUp    = stock.changePercent >= 0
            val color   = if (isUp) GainGreen else LossRed
            val news    = state.advancedStockNews[stock.symbol] ?: emptyList()

            Card(modifier = Modifier.fillMaxWidth().clickable { onNavigateToStockDetail(stock.symbol) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // ── Header ───────────────────────────────────────────────
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stock.symbol, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                            Text(stock.name.ifBlank { stock.symbol }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$${String.format(java.util.Locale.US, "%.2f", stock.price)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("${if (isUp) "▲" else "▼"} ${String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(stock.changePercent))}%",
                                color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    // ── Complexity warning ────────────────────────────────────
                    if (rankedStock.reasons.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = LossRed.copy(alpha = 0.08f)) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                                Text("⚠️", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(6.dp))
                                Text(rankedStock.reasons.first(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    // ── Latest News ───────────────────────────────────────────
                    if (news.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Spacer(Modifier.height(6.dp))
                        Text("📰 Latest News", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        news.forEach { article ->
                            val sentColor = when (article.sentiment) {
                                NewsSentiment.POSITIVE -> GainGreen
                                NewsSentiment.NEGATIVE -> LossRed
                                NewsSentiment.NEUTRAL  -> Color.Gray
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url))) } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(sentColor).align(Alignment.Top).padding(top = 4.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(article.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(2.dp))
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text(article.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    } else if (state.isLoadingAdvancedNews) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Loading news…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // Tap to see full detail
                    Text("Tap for full analysis →", style = MaterialTheme.typography.labelSmall, color = ElectricBlue, modifier = Modifier.align(Alignment.End))
                }
            }
        }
    }
}

private fun formatMarketCap(cap: Long): String = when {
    cap >= 1_000_000_000_000L -> "$${String.format(java.util.Locale.US, "%.1f", cap / 1_000_000_000_000.0)}T"
    cap >= 1_000_000_000L     -> "$${String.format(java.util.Locale.US, "%.1f", cap / 1_000_000_000.0)}B"
    cap >= 1_000_000L         -> "$${String.format(java.util.Locale.US, "%.1f", cap / 1_000_000.0)}M"
    else                      -> "$$cap"
}
