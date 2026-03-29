package com.tradescreenerai.app.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tradescreenerai.app.data.model.SignalType
import com.tradescreenerai.app.ui.components.charts.*
import com.tradescreenerai.app.ui.components.*
import com.tradescreenerai.app.ui.components.cards.NewsCard
import com.tradescreenerai.app.ui.components.common.*
import com.tradescreenerai.app.ui.theme.*
import com.tradescreenerai.app.util.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoDetailScreen(
    coinId: String,
    onBack: () -> Unit,
    onLearn: (() -> Unit)? = null,
    onDayTrader: (() -> Unit)? = null,
    viewModel: CryptoDetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAllIndicators by remember { mutableStateOf(false) }

    LaunchedEffect(coinId) {
        viewModel.loadCoin(coinId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                // Day Trader Mode button
                if (onDayTrader != null) {
                    IconButton(onClick = onDayTrader) {
                        Icon(Icons.Filled.Bolt, "Day Trader Mode", tint = HoldSignalColor)
                    }
                }
                if (onLearn != null) {
                    IconButton(onClick = onLearn) {
                        Icon(Icons.Filled.School, "Learn", tint = MaterialTheme.colorScheme.primary)
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

        // ── Loading spinner (first load only) ────────────────────────────────
        if (state.isLoading && state.crypto == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        // ── Error / rate-limit state ──────────────────────────────────────────
        if (!state.isLoading && state.crypto == null) {
            Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = state.error ?: "Failed to load data",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (state.error?.contains("429") == true ||
                        state.error?.contains("Rate Limit") == true) {
                        Text(
                            "CoinGecko rate limit reached — please wait a moment then retry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                    Button(onClick = { viewModel.retryLoad(coinId) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            }
            return
        }

        val crypto = state.crypto ?: return

        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (crypto.imageUrl.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(crypto.imageUrl).crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(crypto.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                if (crypto.rank > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    ) {
                                        Text("#${crypto.rank}", style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            Text(crypto.symbol, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Price
                    Text(
                        formatPrice(crypto.price),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PriceChangeChip(crypto.changePercent24h)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${if (crypto.change24h >= 0) "+" else ""}${formatPrice(crypto.change24h)} today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ===== SMART SIGNAL SCORE =====
            item {
                Spacer(Modifier.height(16.dp))
                SmartScoreCard(
                    score = state.smartScore,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ===== AI INSIGHT =====
            if (state.aiInsight.isNotBlank()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    AiInsightCard(
                        insight = state.aiInsight,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // ===== TRADITIONAL BUY/SELL SIGNAL =====
            item {
                Spacer(Modifier.height(10.dp))
                SignalBadge(
                    signal = state.technicalData.signal,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
            }

            // ===== DAY TRADER MODE BUTTON =====
            if (onDayTrader != null) {
                item {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onDayTrader,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, HoldSignalColor.copy(alpha = 0.4f)
                        )
                    ) {
                        Icon(Icons.Filled.Bolt, null, tint = HoldSignalColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("⚡ Day Trader Mode", fontWeight = FontWeight.Bold, color = HoldSignalColor)
                    }
                }
            }

            // ===== CHART =====
            item {
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.padding(horizontal = 20.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Line", "Candle").forEach { type ->
                        FilterChip(
                            selected = state.selectedChartType == type,
                            onClick  = { viewModel.selectChartType(type) },
                            label    = { Text(type, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                when (state.selectedChartType) {
                    "Line" -> PriceLineChart(
                        data = state.priceHistory,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        buySellSignals = state.buySellSignals,
                        referenceTimestamps = state.chartEntries.map { it.timestamp }
                    )
                    "Candle" -> CandlestickChart(
                        data = state.chartEntries,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        buySellSignals = state.buySellSignals,
                        supertrendLine = state.supertrendLine,
                        candlePatterns = state.candlePatterns
                    )
                }

                Spacer(Modifier.height(8.dp))
                TimeRangeSelector(
                    ranges   = Constants.TIME_RANGES,
                    selected = state.selectedTimeRange,
                    onSelect = { viewModel.selectTimeRange(it) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ===== AI PATTERN ANALYSIS =====
            if (state.candlePatterns.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    PatternAnalysisCard(
                        patterns     = state.candlePatterns,
                        prediction   = state.patternPrediction,
                        currentPrice = state.chartEntries.lastOrNull()?.close
                            ?: state.crypto?.price ?: 0.0,
                        modifier     = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // ===== CONFLUENCE SCORE =====
            item {
                Spacer(Modifier.height(12.dp))
                ConfluenceScoreCard(
                    result = state.confluenceScore,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ===== WHY THIS TRADE =====
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

            // ===== RISK OVERLAY =====
            item {
                Spacer(Modifier.height(8.dp))
                RiskOverlayCard(
                    risk = state.riskOverlay,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ===== MULTI-TIMEFRAME =====
            item {
                Spacer(Modifier.height(8.dp))
                MultiTimeframeRow(
                    trends = state.timeframeTrends,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ===== BACKTEST =====
            item {
                Spacer(Modifier.height(8.dp))
                BacktestCard(
                    result = state.backtestResult,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ===== INDICATOR TOGGLES =====
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Technical Indicators",
                    action = if (showAllIndicators) "Show Less" else "Show All",
                    onAction = { showAllIndicators = !showAllIndicators })
                IndicatorTogglesPanel(
                    visibility = state.indicatorVisibility,
                    onToggle   = { viewModel.toggleIndicator(it) },
                    modifier   = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ===== INDICATOR VALUES =====
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
                            StatRow("RSI (14)", String.format("%.2f", td.rsi))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showMACD) {
                            StatRow("MACD", String.format("%.4f", td.macd))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("MACD Signal", String.format("%.4f", td.macdSignal))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("MACD Histogram", String.format("%.4f", td.macdHistogram))
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
                        if (v.showBollingerBands && td.bollingerUpper > 0) {
                            StatRow("BB Upper", formatPrice(td.bollingerUpper))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("BB Lower", formatPrice(td.bollingerLower))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showVWAP && td.vwap > 0) {
                            StatRow("VWAP", formatPrice(td.vwap))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showATR && td.atr > 0) {
                            StatRow("ATR (14)", String.format("%.4f", td.atr))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showADX && td.adx > 0) {
                            StatRow("ADX (14)", String.format("%.2f", td.adx))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("+DI / -DI", "${String.format("%.2f", td.plusDI)} / ${String.format("%.2f", td.minusDI)}")
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showStochastic) {
                            StatRow("Stoch %K / %D", "${String.format("%.2f", td.stochK)} / ${String.format("%.2f", td.stochD)}")
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showOBV && td.obv != 0.0) {
                            StatRow("OBV", formatLargeNumber(td.obv))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (v.showVolume && td.volumeAvg20 > 0) {
                            StatRow("Vol Avg (20)", formatLargeNumber(td.volumeAvg20))
                        }
                    }
                }
            }

            // ===== SIGNAL REASONS =====
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

            // ===== SMART SCORE INSIGHTS =====
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

            // ===== KEY STATS =====
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Key Statistics")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        StatRow("Market Cap", formatLargeNumber(crypto.marketCap))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        StatRow("24h Volume", formatLargeNumber(crypto.volume24h))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        StatRow("24h High", formatPrice(crypto.high24h))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        StatRow("24h Low", formatPrice(crypto.low24h))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        StatRow("Circulating Supply", formatLargeNumber(crypto.circulatingSupply))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        crypto.maxSupply?.let {
                            StatRow("Max Supply", formatLargeNumber(it))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        StatRow("All-Time High", formatPrice(crypto.ath))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        StatRow("From ATH", "${String.format("%.2f", crypto.athChangePercent)}%")
                    }
                }
            }

            // ===== SENTIMENT =====
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Social Sentiment")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    SentimentGauge(
                        sentiment = state.sentiment,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // ===== RELATED NEWS =====
            if (state.relatedNews.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("Related News")
                }
                items(state.relatedNews) { article ->
                    NewsCard(
                        article = article,
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url)))
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

