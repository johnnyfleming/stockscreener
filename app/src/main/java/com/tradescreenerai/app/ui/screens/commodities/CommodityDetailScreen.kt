package com.tradescreenerai.app.ui.screens.commodities

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradescreenerai.app.data.model.PricePoint
import com.tradescreenerai.app.data.model.SignalType
import com.tradescreenerai.app.ui.components.*
import com.tradescreenerai.app.ui.components.cards.NewsCard
import com.tradescreenerai.app.ui.components.cards.BeginnerExplanationCard
import com.tradescreenerai.app.ui.components.charts.*
import com.tradescreenerai.app.ui.components.common.*
import com.tradescreenerai.app.ui.theme.*
import com.tradescreenerai.app.util.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommodityDetailScreen(
    commodityTypeStr: String,
    onBack: () -> Unit,
    onDayTrader: (() -> Unit)? = null,
    viewModel: CommodityDetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAllIndicators by remember { mutableStateOf(false) }

    LaunchedEffect(commodityTypeStr) { viewModel.loadCommodity(commodityTypeStr) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
            actions = {
                if (onDayTrader != null) {
                    IconButton(onClick = onDayTrader) {
                        Icon(
                            Icons.Filled.Bolt,
                            contentDescription = "Day Trader",
                            tint = ElectricBlue
                        )
                    }
                }
                IconButton(onClick = { viewModel.toggleWatchlist() }) {
                    Icon(
                        if (state.isInWatchlist) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        "Watchlist",
                        tint = if (state.isInWatchlist) GoldAccent else MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        if (state.isLoading && state.commodity == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }

        val type = state.commodityType ?: return

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {

            // ── Header ────────────────────────────────────────────────────────
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    // Emoji badge (replaces stock icon)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(type.emoji, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(type.displayName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(type.name, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" · ${type.unit}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" · ${type.category.displayName}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))
                    val price = (state.commodity?.price?.takeIf { it > 0 }) ?: (state.chartEntries.lastOrNull()?.close ?: 0.0)
                    Text(formatPrice(price), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val changePct = state.commodity?.changePct ?: 0.0
                        PriceChangeChip(changePct)
                        Spacer(Modifier.width(8.dp))
                        val change = state.commodity?.change ?: 0.0
                        Text(
                            "${if (change >= 0) "+" else ""}${formatPrice(change)} today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Extra commodity performance row
                    state.commodity?.let { c ->
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PerformancePill("Week", c.weeklyChange)
                            PerformancePill("Month", c.monthlyChange)
                        }
                    }
                }
            }

            // ── Smart Signal Score ─────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                SmartScoreCard(
                    score = state.smartScore,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ── AI Insight ────────────────────────────────────────────────────
            if (state.aiInsight.isNotBlank()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    AiInsightCard(
                        insight = state.aiInsight,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // ── Signal Badge ──────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(10.dp))
                SignalBadge(
                    state.technicalData.signal,
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
            }

            // ── Chart ─────────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Candle", "Line").forEach { chartType ->
                        FilterChip(
                            selected = state.selectedChartType == chartType,
                            onClick = { viewModel.selectChartType(chartType) },
                            label = { Text(chartType, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                when (state.selectedChartType) {
                    "Candle" -> CandlestickChart(
                        data = state.chartEntries,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        buySellSignals = state.buySellSignals,
                        supertrendLine = state.supertrendLine,
                        confluenceZones = if (state.indicatorVisibility.showConfluenceZones) state.confluenceZones else emptyList(),
                        candlePatterns = state.candlePatterns,
                        showSignalProgress = state.indicatorVisibility.showSignalProgress
                    )
                    "Line" -> PriceLineChart(
                        data = state.chartEntries.map { PricePoint(it.timestamp, it.close) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                        buySellSignals = state.buySellSignals
                    )
                }
                Spacer(Modifier.height(8.dp))
                TimeRangeSelector(
                    ranges = Constants.TIME_RANGES,
                    selected = state.selectedTimeRange,
                    onSelect = { viewModel.selectTimeRange(it) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ── Quick Insight Panel ────────────────────────────────────────────
            if (state.quickInsight.trend.isNotBlank()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    QuickInsightPanel(
                        insight = state.quickInsight,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // ── AI Pattern Analysis Card ───────────────────────────────────────
            if (state.candlePatterns.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    PatternAnalysisCard(
                        patterns = state.candlePatterns,
                        prediction = state.patternPrediction,
                        currentPrice = state.chartEntries.lastOrNull()?.close ?: 0.0,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // ── Confluence Score ───────────────────────────────────────────────
            item {
                Spacer(Modifier.height(12.dp))
                ConfluenceScoreCard(
                    result = state.confluenceScore,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ── Why This Trade ─────────────────────────────────────────────────
            if (state.confluenceScore.reasons.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    WhyThisTradeCard(
                        reasons = state.confluenceScore.reasons,
                        strategy = state.strategy,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // ── Risk Overlay ───────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                RiskOverlayCard(
                    risk = state.riskOverlay,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ── Multi-Timeframe ────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                MultiTimeframeRow(
                    trends = state.timeframeTrends,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ── Backtest ───────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                BacktestCard(
                    result = state.backtestResult,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ── Indicator Toggles ──────────────────────────────────────────────
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader(
                    "Technical Indicators",
                    action = if (showAllIndicators) "Advanced ▲" else "Advanced ▼",
                    onAction = { showAllIndicators = !showAllIndicators }
                )
                IndicatorTogglesPanel(
                    visibility = state.indicatorVisibility,
                    onToggle = { viewModel.toggleIndicator(it) },
                    showAdvanced = showAllIndicators,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ── Indicator Values Card ──────────────────────────────────────────
            item {
                val td = state.technicalData
                val v  = state.indicatorVisibility
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (v.showRSI) {
                            StatRow("RSI (14)", String.format(java.util.Locale.US, "%.2f", td.rsi))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showMACD) {
                            StatRow("MACD", String.format(java.util.Locale.US, "%.4f", td.macd))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("MACD Signal", String.format(java.util.Locale.US, "%.4f", td.macdSignal))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("MACD Histogram", String.format(java.util.Locale.US, "%.4f", td.macdHistogram))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showEMA20 && td.ema20 > 0) {
                            StatRow("EMA 20", formatPrice(td.ema20))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showEMA50 && td.ema50 > 0) {
                            StatRow("EMA 50", formatPrice(td.ema50))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showEMA200 && td.ema200 > 0) {
                            StatRow("EMA 200", formatPrice(td.ema200))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showSMA20 && td.sma20 > 0) {
                            StatRow("SMA 20", formatPrice(td.sma20))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (td.sma50 > 0) {
                            StatRow("SMA 50", formatPrice(td.sma50))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (showAllIndicators && td.sma200 > 0) {
                            StatRow("SMA 200", formatPrice(td.sma200))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showBollingerBands && td.bollingerUpper > 0) {
                            StatRow("BB Upper", formatPrice(td.bollingerUpper))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("BB Middle", formatPrice(td.bollingerMiddle))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("BB Lower", formatPrice(td.bollingerLower))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showVWAP && td.vwap > 0) {
                            StatRow("VWAP", formatPrice(td.vwap))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showATR && td.atr > 0) {
                            StatRow("ATR (14)", String.format(java.util.Locale.US, "%.4f", td.atr))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showADX && td.adx > 0) {
                            StatRow("ADX (14)", String.format(java.util.Locale.US, "%.2f", td.adx))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("+DI", String.format(java.util.Locale.US, "%.2f", td.plusDI))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("-DI", String.format(java.util.Locale.US, "%.2f", td.minusDI))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showStochastic) {
                            StatRow("Stoch %K", String.format(java.util.Locale.US, "%.2f", td.stochK))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("Stoch %D", String.format(java.util.Locale.US, "%.2f", td.stochD))
                        }
                    }
                }
            }

            // ── Signal Analysis ────────────────────────────────────────────────
            if (state.technicalData.signal.reasons.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    SectionHeader("Signal Analysis")
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        state.technicalData.signal.reasons.forEach { reason ->
                            val color = when (state.technicalData.signal.type) {
                                SignalType.BUY  -> GainGreen
                                SignalType.SELL -> LossRed
                                SignalType.HOLD -> HoldSignalColor
                            }
                            Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Circle, null, tint = color, modifier = Modifier.size(8.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(reason, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // ── Smart Score Insights ───────────────────────────────────────────
            if (state.smartScore.insights.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    SectionHeader("Score Breakdown")
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        state.smartScore.insights.forEach { insight ->
                            Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Circle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(8.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(insight, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // ── Key Statistics (commodity-specific) ───────────────────────────
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Key Statistics")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        state.commodity?.let { c ->
                            StatRow("Price", formatPrice(c.price))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("Daily Change", "${if (c.changePct >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.2f", c.changePct)}%")
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("Weekly Change", "${if (c.weeklyChange >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.2f", c.weeklyChange)}%")
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("Monthly Change", "${if (c.monthlyChange >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.2f", c.monthlyChange)}%")
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        StatRow("Unit", type.unit)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        StatRow("Category", type.category.displayName)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        StatRow("Data Source", "Alpha Vantage (Daily)")
                    }
                }
            }

            // ── Seasonal Context ───────────────────────────────────────────────
            if (state.seasonalContext.isNotBlank()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("Seasonal Context")
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            state.seasonalContext,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // ── Beginner Explanation ──────────────────────────────────────────
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("📚 Beginner Explanation")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    BeginnerExplanationCard(
                        insight = state.beginnerInsight,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // ── About ─────────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("About ${type.displayName}")
                Text(
                    type.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ── Related News ──────────────────────────────────────────────────
            if (state.relatedNews.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("Market News")
                }
                items(state.relatedNews) { article ->
                    NewsCard(
                        article = article,
                        onClick = {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url))) } catch (_: Exception) {}
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PerformancePill(label: String, changePct: Double) {
    val color = if (changePct >= 0) GainGreen else LossRed
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.10f)) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${if (changePct >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.1f", changePct)}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
