package com.tradescreenerai.app.ui.screens.commodities

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.ui.components.cards.BeginnerExplanationCard
import com.tradescreenerai.app.ui.components.cards.CommodityCard
import com.tradescreenerai.app.ui.components.cards.CommodityTradingCard
import com.tradescreenerai.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommoditiesScreen(
    onNavigateToCommodityDetail: (String) -> Unit,
    onNavigateToDayTrader: ((String) -> Unit)? = null,
    viewModel: CommoditiesViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDataNote by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Commodities", fontWeight = FontWeight.Bold)
                        if (state.lastUpdated > 0) {
                            Text(
                                "Refresh in ${state.nextRefreshIn}s • Daily data",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Beginner toggle
                    IconButton(onClick = { viewModel.toggleBeginnerMode() }) {
                        Icon(
                            Icons.Filled.School,
                            contentDescription = "Beginner Mode",
                            tint = if (state.isBeginnerMode) GoldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Data note
                    IconButton(onClick = { showDataNote = !showDataNote }) {
                        Icon(Icons.Filled.Info, "Data Info")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // View mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommodityViewMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.viewMode == mode,
                        onClick = { viewModel.setViewMode(mode) },
                        label = { Text(mode.label) },
                        leadingIcon = if (state.viewMode == mode) {
                            { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            // Category filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = state.selectedCategory == null,
                    onClick = { viewModel.setCategory(null) },
                    label = { Text("All") }
                )
                CommodityCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = state.selectedCategory == cat,
                        onClick = { viewModel.setCategory(cat) },
                        label = { Text("${cat.emoji} ${cat.displayName}") }
                    )
                }
            }

            // Data limitation note
            AnimatedVisibility(visible = showDataNote) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = HoldSignalColor.copy(alpha = 0.08f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("📌 Data Provider Notes", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.dataLimitationNote.ifBlank {
                                "Commodity data from Alpha Vantage (daily). Free tier: 25 calls/day.\n" +
                                "Live intraday futures require Polygon Advanced ($199/mo) or Tradermade."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Content
            when {
                state.isLoading && state.commodities.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Loading commodity data...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                state.error != null && state.commodities.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚠️ ${state.error}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                        }
                    }
                }
                else -> {
                    val filteredCommodities = if (state.selectedCategory != null) {
                        state.commodities.filter { it.category == state.selectedCategory }
                    } else {
                        state.commodities
                    }

                    when (state.viewMode) {
                        CommodityViewMode.STANDARD -> StandardView(
                            commodities = filteredCommodities,
                            isBeginnerMode = state.isBeginnerMode,
                            onNavigateToDetail = onNavigateToCommodityDetail,
                            onSelectForInsight = { viewModel.selectCommodityForInsight(it) }
                        )
                        CommodityViewMode.TRADING -> TradingView(
                            commodities = filteredCommodities,
                            rankings = state.rankings.filter {
                                state.selectedCategory == null || it.commodity.category == state.selectedCategory
                            },
                            onNavigateToDetail = onNavigateToCommodityDetail,
                            onNavigateToDayTrader = onNavigateToDayTrader
                        )
                    }
                }
            }
        }

        // Beginner insight dialog
        if (state.selectedInsight != null && state.selectedCommodity != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissInsight() },
                title = { Text("📚 ${state.selectedCommodity!!.name} Explained") },
                text = { BeginnerExplanationCard(insight = state.selectedInsight!!) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissInsight() }) { Text("Got it!") }
                }
            )
        }
    }
}

@Composable
private fun StandardView(
    commodities: List<Commodity>,
    isBeginnerMode: Boolean,
    onNavigateToDetail: (String) -> Unit,
    onSelectForInsight: (Commodity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isBeginnerMode) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GoldAccent.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📚 What are Commodities?", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Commodities are raw materials like gold, oil, and wheat that are traded " +
                                    "on global markets. Their prices affect everyday costs — from fuel to food. " +
                                    "Tap any commodity to learn more.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Group by category
        val grouped = commodities.groupBy { it.category }
        grouped.forEach { (category, items) ->
            item {
                Text(
                    "${category.emoji} ${category.displayName}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            items(items) { commodity ->
                CommodityCard(
                    commodity = commodity,
                    showBeginnerInfo = isBeginnerMode,
                    onClick = {
                        if (isBeginnerMode) onSelectForInsight(commodity)
                        else onNavigateToDetail(commodity.type.name)
                    }
                )
            }
        }
    }
}

@Composable
private fun TradingView(
    commodities: List<Commodity>,
    rankings: List<CommodityRanking>,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToDayTrader: ((String) -> Unit)? = null
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ElectricBlue.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("📈 Trading View", fontWeight = FontWeight.Bold)
                    Text(
                        "Tap a commodity to see charts with VWAP, EMA, RSI, MACD, ATR, and buy/sell markers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "⚠️ Daily data resolution — live intraday requires upgraded API plan",
                        style = MaterialTheme.typography.labelSmall,
                        color = HoldSignalColor
                    )
                }
            }
        }

        items(rankings.ifEmpty { commodities.map { CommodityRanking(it) } }) { ranking ->
            CommodityTradingCard(
                ranking = ranking,
                onClick = { onNavigateToDetail(ranking.commodity.type.name) },
                onDayTrader = onNavigateToDayTrader?.let { nav ->
                    { nav(ranking.commodity.type.name) }
                }
            )
        }
    }
}

