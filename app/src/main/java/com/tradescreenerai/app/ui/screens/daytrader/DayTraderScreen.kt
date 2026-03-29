package com.tradescreenerai.app.ui.screens.daytrader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.domain.TechnicalAnalysis
import com.tradescreenerai.app.ui.components.charts.CandlestickChart
import com.tradescreenerai.app.ui.components.charts.OverlayLine
import com.tradescreenerai.app.ui.components.charts.PriceLineChart
import com.tradescreenerai.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayTraderScreen(
    symbol: String,
    assetType: String,
    onBack: () -> Unit,
    viewModel: DayTraderViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showAlertSettings by remember { mutableStateOf(false) }

    LaunchedEffect(symbol, assetType) {
        viewModel.loadAsset(symbol, assetType)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "⚡ Day Trader",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            // Riskiness badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = riskinessColor(state.settings.riskiness).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "${state.settings.riskiness.emoji} ${state.settings.riskiness.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = riskinessColor(state.settings.riskiness),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            state.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    // Live indicator
                    if (!state.isLoading && state.entries.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF00C853).copy(alpha = 0.2f)
                        ) {
                            Text(
                                "● LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00C853),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = { showAlertSettings = !showAlertSettings }) {
                        Icon(Icons.Filled.NotificationsActive, "Alerts",
                            modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Filled.Tune, "Settings",
                            modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { viewModel.manualRefresh() },
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading)
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else
                            Icon(Icons.Filled.Refresh, "Refresh", modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ── Market Session Banner ─────────────────────────────────────
            item(key = "session") {
                MarketSessionBanner(state.session)
            }

            // ── Price Header ──────────────────────────────────────────────
            item(key = "price") {
                PriceHeader(state)
            }

            // ── Interval Chips ────────────────────────────────────────────
            item(key = "intervals") {
                IntervalChipRow(
                    selected = state.settings.interval,
                    onSelect = { viewModel.updateInterval(it) }
                )
            }

            // ── Riskiness Selector ────────────────────────────────────────
            item(key = "riskiness") {
                RiskinessSelector(
                    selected = state.settings.riskiness,
                    onSelect = { viewModel.updateRiskiness(it) }
                )
            }

            // ── Alert banners ─────────────────────────────────────────────
            if (state.recentAlerts.isNotEmpty()) {
                item(key = "alerts") {
                    AlertBanners(
                        alerts = state.recentAlerts,
                        onDismiss = { viewModel.dismissAlert(it) }
                    )
                }
            }

            // ── Settings panel (expandable) ───────────────────────────────
            item(key = "settings") {
                AnimatedVisibility(
                    visible = showSettings,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    SettingsPanel(
                        settings = state.settings,
                        onToggleIndicator = { viewModel.toggleIndicator(it) },
                        onChartTypeChange = { viewModel.updateChartType(it) }
                    )
                }
            }

            // ── Alert Settings panel ──────────────────────────────────────
            item(key = "alert_settings") {
                AnimatedVisibility(
                    visible = showAlertSettings,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    AlertSettingsPanel(
                        settings = state.settings,
                        onToggle = { viewModel.toggleAlert(it) }
                    )
                }
            }

            // ── Offline / Daily-fallback banner ───────────────────────────
            if (state.isUsingDailyFallback || state.session.type == MarketSessionType.CLOSED) {
                item(key = "offline_banner") {
                    Surface(
                        color = androidx.compose.ui.graphics.Color(0xFF1A237E).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🌙", style = MaterialTheme.typography.bodyMedium)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (state.isUsingDailyFallback) "Market Offline · Daily Chart"
                                    else "Market Closed",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color(0xFF90CAF9)
                                )
                                Text(
                                    if (state.isUsingDailyFallback)
                                        "No intraday data available · showing last 90 days of daily candles · all indicators & trend analysis still work"
                                    else
                                        "Intraday candles refresh when the market reopens",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Chart Area ────────────────────────────────────────────────
            item(key = "chart") {
                // derivedStateOf ensures ChartSection only recomposes when chart-relevant
                // data changes, NOT on every nextRefreshIn tick (every 1 second).
                val chartEntries  by remember { derivedStateOf { state.entries } }
                val chartSignals  by remember { derivedStateOf { state.signals } }
                val chartSettings by remember { derivedStateOf { state.settings } }
                val chartStateKey by remember { derivedStateOf { state.chartStateKey } }
                ChartSection(
                    entries       = chartEntries,
                    signals       = chartSignals,
                    settings      = chartSettings,
                    chartStateKey = chartStateKey
                )
            }

            // ── Indicator Summary ─────────────────────────────────────────
            if (state.entries.size >= 26) {
                item(key = "indicators") {
                    IndicatorSummaryCard(state)
                }
            }

            // ── Latest Bar Status (Signal Watch) ──────────────────────────
            if (state.entries.size >= 35 && !state.isUsingDailyFallback) {
                item(key = "latest_bar_status") {
                    val statusVal by remember { derivedStateOf { state.latestBarStatus } }
                    statusVal?.let { LatestBarStatusCard(it) }
                }
            }

            // ── Backtest Results ──────────────────────────────────────────
            if (state.backtestResult != null && !state.isUsingDailyFallback) {
                item(key = "backtest") {
                    val btResult by remember { derivedStateOf { state.backtestResult } }
                    btResult?.let { BacktestCard(it, state.settings.riskiness) }
                }
            }

            // ── Live Signals ──────────────────────────────────────────────
            if (state.signals.isNotEmpty() && state.settings.indicators.showSignals) {
                item(key = "signals_header") {
                    val strongCount = state.signals.count { it.strengthLabel == DayTraderSignalStrength.STRONG }
                    val mediumCount = state.signals.count { it.strengthLabel == DayTraderSignalStrength.MEDIUM }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Bolt, null, tint = HoldSignalColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Signals (${state.signals.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (strongCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = GainGreen.copy(alpha = 0.18f)) {
                                Text("$strongCount Strong", style = MaterialTheme.typography.labelSmall,
                                    color = GainGreen, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                        if (mediumCount > 0) {
                            Spacer(Modifier.width(4.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = HoldSignalColor.copy(alpha = 0.18f)) {
                                Text("$mediumCount Medium", style = MaterialTheme.typography.labelSmall,
                                    color = HoldSignalColor, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Refresh in ${state.nextRefreshIn}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                itemsIndexed(
                    state.signals.reversed().take(10),
                    key = { _, sig -> "sig_${sig.index}_${sig.isBuy}" }
                ) { _, signal ->
                    SignalCard(signal = signal)
                }
            }

            // ── Loading / Error / Empty ───────────────────────────────────
            if (state.isLoading && state.entries.isEmpty()) {
                item(key = "loading") {
                    Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Loading ${state.settings.interval.label} data…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (state.error != null && state.entries.isEmpty()) {
                item(key = "error") {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (state.error!!.startsWith("🌙")) "🌙" else "⚠️",
                                style = MaterialTheme.typography.displaySmall
                            )
                            Text(
                                state.error ?: "Error",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (state.error!!.startsWith("🌙")) {
                                Text(
                                    "You can still view the chart with daily data.\nAll indicators and trend analysis work offline.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Button(onClick = { viewModel.manualRefresh() }) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Try Again")
                            }
                        }
                    }
                }
            }
            if (state.entries.isNotEmpty() && state.signals.isEmpty() && !state.isLoading) {
                item(key = "no_signals") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Info, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("No signals right now",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text("Try adjusting riskiness to High or switching timeframe.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Market Session Banner
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MarketSessionBanner(session: MarketSessionInfo) {
    val (bgColor, textColor) = when (session.type) {
        MarketSessionType.REGULAR -> GainGreen.copy(alpha = 0.12f) to GainGreen
        MarketSessionType.PRE_MARKET, MarketSessionType.AFTER_HOURS -> HoldSignalColor.copy(alpha = 0.12f) to HoldSignalColor
        MarketSessionType.CRYPTO_24H -> Color(0xFF00D4FF).copy(alpha = 0.12f) to Color(0xFF00D4FF)
        MarketSessionType.CLOSED -> LossRed.copy(alpha = 0.12f) to LossRed
    }
    val icon = when (session.type) {
        MarketSessionType.REGULAR -> Icons.Filled.PlayCircle
        MarketSessionType.PRE_MARKET, MarketSessionType.AFTER_HOURS -> Icons.Filled.Schedule
        MarketSessionType.CRYPTO_24H -> Icons.Filled.AllInclusive
        MarketSessionType.CLOSED -> Icons.Filled.PauseCircle
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = textColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                if (session.countdownLabel.isNotEmpty()) {
                    Text(
                        session.countdownLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.8f)
                    )
                }
            }
            if (session.isExtendedHours) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = textColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        "Extended",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (session.exchangeName.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    session.exchangeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Price Header
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PriceHeader(state: DayTraderState) {
    if (state.currentPrice <= 0) return
    val isPositive = state.priceChangePct >= 0
    val changeColor = if (isPositive) GainGreen else LossRed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            formatPrice(state.currentPrice),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                null, tint = changeColor, modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "${if (isPositive) "+" else ""}${formatPrice(state.priceChange)} (${String.format("%.2f", state.priceChangePct)}%)",
                style = MaterialTheme.typography.bodyMedium,
                color = changeColor,
                fontWeight = FontWeight.SemiBold
            )
            if (state.vwap > 0) {
                Spacer(Modifier.width(12.dp))
                Text(
                    "VWAP ${formatPrice(state.vwap)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.lastUpdated > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "· ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(state.lastUpdated))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Interval Chip Row
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun IntervalChipRow(
    selected: DayTraderInterval,
    onSelect: (DayTraderInterval) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DayTraderInterval.entries.forEach { interval ->
            FilterChip(
                selected = interval == selected,
                onClick = { onSelect(interval) },
                label = {
                    Text(
                        interval.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (interval == selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                modifier = Modifier.height(30.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Riskiness Selector
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RiskinessSelector(
    selected: RiskinessLevel,
    onSelect: (RiskinessLevel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RiskinessLevel.entries.forEach { level ->
            val isSelected = level == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(level) },
                label = {
                    Text(
                        "${level.emoji} ${level.label}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = riskinessColor(level).copy(alpha = 0.15f),
                    selectedLabelColor = riskinessColor(level)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Alert Banners
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AlertBanners(
    alerts: List<DayTraderAlert>,
    onDismiss: (Int) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        alerts.forEachIndexed { index, alert ->
            val color = when (alert.type) {
                DayTraderAlertType.BUY_TRIGGER -> GainGreen
                DayTraderAlertType.SELL_TRIGGER -> LossRed
                DayTraderAlertType.VWAP_CROSS -> ElectricBlue
                DayTraderAlertType.VOLUME_SPIKE -> HoldSignalColor
                DayTraderAlertType.RSI_OVERBOUGHT -> LossRed
                DayTraderAlertType.RSI_OVERSOLD -> GainGreen
                DayTraderAlertType.MARKET_OPEN -> GainGreen
                DayTraderAlertType.MARKET_CLOSE_15M -> HoldSignalColor
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.NotificationsActive, null, tint = color, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(alert.type.label, style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = color)
                        Text(alert.message, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(
                        onClick = { onDismiss(index) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Filled.Close, "Dismiss", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Live Chart Section
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ChartSection(
    entries: List<ChartEntry>,
    signals: List<DayTraderSignal>,
    settings: DayTraderSettings,
    chartStateKey: Long
) {
    if (entries.isEmpty()) return

    val closes   = remember(entries.size, entries.lastOrNull()?.close) { entries.map { it.close } }

    // ── Convert DayTraderSignal → BuySellSignal for chart rendering ──────────
    val chartSignals: List<BuySellSignal> = remember(signals) {
        signals.map { sig ->
            BuySellSignal(
                index    = sig.index.coerceIn(0, entries.lastIndex),
                isBuy    = sig.isBuy,
                strength = when {
                    sig.confidence >= 80 -> 3
                    sig.confidence >= 60 -> 2
                    else                 -> 1
                }
            )
        }
    }

    // ── Build overlay lines: EMA9 / EMA20 / EMA50 / VWAP ────────────────────
    val overlayLines: List<OverlayLine> = remember(entries.size, closes.lastOrNull(), settings.indicators) {
        val lines = mutableListOf<OverlayLine>()
        if (settings.indicators.showEMA9 && closes.size >= 9)
            lines += OverlayLine(
                points = TechnicalAnalysis.calculateEMA(closes, 9)
                    .mapIndexed { i, v -> (i + 8) to v },
                color  = Color(0xFF00BCD4), label = "EMA 9"
            )
        if (settings.indicators.showEMA20 && closes.size >= 20)
            lines += OverlayLine(
                points = TechnicalAnalysis.calculateEMA(closes, 20)
                    .mapIndexed { i, v -> (i + 19) to v },
                color  = Color(0xFFFF9800), label = "EMA 20"
            )
        if (settings.indicators.showEMA50 && closes.size >= 50)
            lines += OverlayLine(
                points = TechnicalAnalysis.calculateEMA(closes, 50)
                    .mapIndexed { i, v -> (i + 49) to v },
                color  = Color(0xFF9C27B0), label = "EMA 50"
            )
        if (settings.indicators.showVWAP) {
            var sumTPV = 0.0; var sumVol = 0.0
            lines += OverlayLine(
                points = entries.mapIndexed { i, e ->
                    val tp = (e.high + e.low + e.close) / 3.0
                    sumTPV += tp * e.volume; sumVol += e.volume
                    i to (if (sumVol > 0) sumTPV / sumVol else 0.0)
                },
                color = Color(0xFF00D4FF), label = "VWAP", width = 2f
            )
        }
        lines
    }

    // ── RSI(14) series ────────────────────────────────────────────────────────
    val rsiSeries: List<Pair<Int, Double>> = remember(entries.size, closes.lastOrNull()) {
        if (!settings.indicators.showRSI || entries.size < 15) emptyList()
        else (14 until entries.size).map { i ->
            i to TechnicalAnalysis.calculateRSI(closes.subList(i - 14, i + 1), 14)
        }
    }

    // ── MACD histogram series ─────────────────────────────────────────────────
    val macdHistSeries: List<Pair<Int, Double>> = remember(entries.size, closes.lastOrNull()) {
        if (!settings.indicators.showMACD || closes.size < 35) emptyList()
        else {
            val ema12Full = TechnicalAnalysis.calculateEMA(closes, 12) // size = closes.size - 11
            val ema26Full = TechnicalAnalysis.calculateEMA(closes, 26) // size = closes.size - 25
            val macdLine  = ema12Full.drop(14).zip(ema26Full) { a, b -> a - b }
            val sigLine   = TechnicalAnalysis.calculateEMA(macdLine, 9)
            (8 until macdLine.size).mapNotNull { i ->
                val barIdx = 25 + i
                if (barIdx < entries.size) barIdx to (macdLine[i] - sigLine[i - 8]) else null
            }
        }
    }

    // ── PricePoint list for line-chart mode ───────────────────────────────────
    val pricePoints: List<PricePoint> = remember(entries.size, closes.lastOrNull()) {
        entries.map { PricePoint(timestamp = it.timestamp, price = it.close) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {

            // ── Header ───────────────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${settings.chartType} · ${settings.interval.label} · ${entries.size} bars",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Candlestick", "Line").forEach { type ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (settings.chartType == type)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent
                        ) {
                            Text(
                                if (type == "Candlestick") "🕯️" else "📈",
                                style    = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // ── Main chart (live-keyed on data size so last candle updates) ──
            if (settings.chartType == "Candlestick") {
                CandlestickChart(
                    data           = entries,
                    showVolume     = settings.indicators.showVolume,
                    buySellSignals = chartSignals,
                    overlayLines   = overlayLines,
                    chartStateKey  = chartStateKey,
                    modifier       = Modifier.fillMaxWidth()
                )
            } else {
                PriceLineChart(
                    data           = pricePoints,
                    buySellSignals = chartSignals,
                    modifier       = Modifier.fillMaxWidth()
                )
            }

            // ── Overlay line legend ───────────────────────────────────────────
            if (overlayLines.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    overlayLines.forEach { line ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(line.color)
                            )
                            Text(
                                line.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = line.color,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ── RSI sub-panel ─────────────────────────────────────────────────
            if (rsiSeries.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                RsiPanel(rsiSeries = rsiSeries, totalBars = entries.size)
            }

            // ── MACD sub-panel ────────────────────────────────────────────────
            if (macdHistSeries.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                MacdPanel(histSeries = macdHistSeries, totalBars = entries.size)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  RSI(14) Sub-panel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RsiPanel(rsiSeries: List<Pair<Int, Double>>, totalBars: Int) {
    val onSurfVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "RSI (14)",
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = onSurfVariant
            )
            val lastRsi   = rsiSeries.lastOrNull()?.second ?: 50.0
            val rsiColor  = when { lastRsi > 70 -> LossRed; lastRsi < 30 -> GainGreen; else -> onSurfVariant }
            Text(
                "%.1f".format(lastRsi),
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = rsiColor
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 4.dp)
        ) {
            val w = size.width; val h = size.height
            val n = totalBars.coerceAtLeast(1)
            val slotW = w / n

            fun xFor(idx: Int) = (idx + 0.5f) * slotW
            fun yFor(rsi: Double) = (h * (1.0 - rsi / 100.0)).toFloat().coerceIn(0f, h)

            // Overbought / oversold bands
            val yOb = yFor(70.0); val yOs = yFor(30.0)
            drawRect(Color(0xFFFF5252).copy(alpha = 0.07f), Offset(0f, 0f), Size(w, yOb))
            drawRect(Color(0xFF00E676).copy(alpha = 0.07f), Offset(0f, yOs), Size(w, h - yOs))

            // Guide lines
            for ((lvl, a) in listOf(70.0 to 0.28f, 50.0 to 0.14f, 30.0 to 0.28f)) {
                val y = yFor(lvl)
                drawLine(Color.Gray.copy(alpha = a), Offset(0f, y), Offset(w, y), 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
            }

            // RSI line
            if (rsiSeries.size >= 2) {
                val path = Path()
                rsiSeries.forEachIndexed { j, (idx, v) ->
                    val px = xFor(idx); val py = yFor(v)
                    if (j == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, Color(0xFFBA68C8),
                    style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            // Level labels
            val lp = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(110, 170, 170, 170); textSize = 18f; isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText("70", 4f, yOb - 2f, lp)
            drawContext.canvas.nativeCanvas.drawText("30", 4f, yOs + 16f, lp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MACD Histogram Sub-panel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MacdPanel(histSeries: List<Pair<Int, Double>>, totalBars: Int) {
    val onSurfVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "MACD Histogram",
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = onSurfVariant
            )
            val lastH     = histSeries.lastOrNull()?.second ?: 0.0
            val histColor = if (lastH >= 0) GainGreen else LossRed
            Text(
                "%.5f".format(lastH),
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = histColor
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 4.dp)
        ) {
            val w = size.width; val h = size.height
            if (histSeries.isEmpty()) return@Canvas
            val n      = totalBars.coerceAtLeast(1)
            val slotW  = w / n
            val midY   = h / 2f
            val maxAbs = histSeries.maxOf { kotlin.math.abs(it.second) }.coerceAtLeast(1e-9)
            val barW   = (slotW * 0.7f).coerceIn(1f, 14f)

            fun xFor(idx: Int) = (idx + 0.5f) * slotW
            fun barHeight(v: Double) = (kotlin.math.abs(v) / maxAbs * midY).toFloat()

            drawLine(Color.Gray.copy(alpha = 0.22f), Offset(0f, midY), Offset(w, midY), 1f)

            for ((idx, hist) in histSeries) {
                val cx  = xFor(idx)
                val bh  = barHeight(hist)
                val col = if (hist >= 0) GainGreen else LossRed
                if (hist >= 0)
                    drawRect(col.copy(alpha = 0.72f), Offset(cx - barW / 2f, midY - bh), Size(barW, bh))
                else
                    drawRect(col.copy(alpha = 0.72f), Offset(cx - barW / 2f, midY), Size(barW, bh))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Indicator Summary Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun IndicatorSummaryCard(state: DayTraderState) {
    val td = state.technicalData
    val ind = state.settings.indicators

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "📊 Indicators",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            val items = mutableListOf<Triple<String, String, Color>>()
            if (ind.showRSI) {
                val rsiColor = when {
                    td.rsi > 70 -> LossRed
                    td.rsi < 30 -> GainGreen
                    else -> MaterialTheme.colorScheme.onSurface
                }
                items.add(Triple("RSI", String.format("%.1f", td.rsi), rsiColor))
            }
            if (ind.showMACD) {
                val macdColor = if (td.macdHistogram > 0) GainGreen else LossRed
                items.add(Triple("MACD", String.format("%.4f", td.macd), macdColor))
            }
            if (ind.showVWAP && state.vwap > 0) {
                val vwapColor = if (state.currentPrice > state.vwap) GainGreen else LossRed
                items.add(Triple("VWAP", formatPrice(state.vwap), vwapColor))
            }
            if (ind.showEMA9) items.add(Triple("EMA 9", formatPrice(td.ema12), MaterialTheme.colorScheme.onSurface)) // closest to 9
            if (ind.showEMA20) items.add(Triple("EMA 20", formatPrice(td.ema20), MaterialTheme.colorScheme.onSurface))
            if (ind.showEMA50) items.add(Triple("EMA 50", formatPrice(td.ema50), MaterialTheme.colorScheme.onSurface))
            if (ind.showATR) items.add(Triple("ATR", String.format("%.4f", td.atr), MaterialTheme.colorScheme.onSurface))
            if (ind.showVolume) items.add(Triple("Vol Avg", formatVolume(td.volumeAvg20), MaterialTheme.colorScheme.onSurface))

            // Grid layout: 2 columns
            val chunked = items.chunked(2)
            chunked.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (label, value, color) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold, color = color)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Signal Card — rich version with pattern, strength, confirmation breakdown
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SignalCard(signal: DayTraderSignal) {
    val color = if (signal.isBuy) GainGreen else LossRed
    val strengthColor = when (signal.strengthLabel) {
        DayTraderSignalStrength.STRONG -> GainGreen
        DayTraderSignalStrength.MEDIUM -> HoldSignalColor
        DayTraderSignalStrength.WEAK   -> Color(0xFFFFEB3B)
    }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: BUY/SELL · Pattern · Strength · [EARLY] · Time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.22f)) {
                    Text(signal.label,
                        style      = MaterialTheme.typography.labelMedium,
                        color      = color,
                        fontWeight = FontWeight.Black,
                        modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("${signal.patternEmoji} ${signal.patternName}",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f),
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis)
                Spacer(Modifier.width(4.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = strengthColor.copy(alpha = 0.18f)) {
                    Text("${signal.strengthLabel.emoji} ${signal.strengthLabel.label}",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = strengthColor,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                if (signal.isEarlySignal) {
                    Spacer(Modifier.width(4.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = HoldSignalColor.copy(alpha = 0.2f)) {
                        Text("EARLY",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = HoldSignalColor,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(signal.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(5.dp))

            // Row 2: Score bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Score ${signal.confidence}/100",
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = color,
                    modifier   = Modifier.width(84.dp))
                Box(modifier = Modifier.weight(1f).height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))) {
                    Box(modifier = Modifier.fillMaxHeight()
                        .fillMaxWidth(signal.confidence / 100f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color))
                }
            }

            Spacer(Modifier.height(6.dp))

            // Row 3: Entry / SL / TP / R:R
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Entry", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatPrice(signal.entryPrice), style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Stop Loss", style = MaterialTheme.typography.labelSmall, color = LossRed)
                    Text(formatPrice(signal.stopLoss), style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = LossRed)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Take Profit", style = MaterialTheme.typography.labelSmall, color = GainGreen)
                    Text(formatPrice(signal.takeProfit), style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = GainGreen)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("R:R", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(String.format("%.1f", signal.riskReward), style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                }
            }

            // Row 4: Why this signal fired
            if (signal.reasons.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()) {
                    Text("✅ " + signal.reasons.joinToString(" · "),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis)
                }
            }

            // Row 5: Expand/collapse confirmation layer breakdown
            if (signal.confirmationLayers.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                TextButton(onClick = { expanded = !expanded },
                    modifier = Modifier.height(24.dp), contentPadding = PaddingValues(0.dp)) {
                    Text(if (expanded) "Hide breakdown ▲" else "Show confirmation layers ▼",
                        style = MaterialTheme.typography.labelSmall, color = color)
                }
                if (expanded) {
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        signal.confirmationLayers.forEach { layer ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (layer.passed) "✅" else "❌",
                                    style    = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(20.dp))
                                Text("${layer.name}: ${layer.detail}",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = if (layer.passed) MaterialTheme.colorScheme.onSurface
                                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Settings Panel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsPanel(
    settings: DayTraderSettings,
    onToggleIndicator: (String) -> Unit,
    onChartTypeChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("⚙️ Indicators & Chart", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            // Chart type
            Text("Chart Type", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                listOf("Candlestick", "Line").forEach { type ->
                    FilterChip(
                        selected = settings.chartType == type,
                        onClick = { onChartTypeChange(type) },
                        label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f).height(30.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text("Overlay Indicators", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                val overlays = listOf(
                    "VWAP" to settings.indicators.showVWAP,
                    "EMA9" to settings.indicators.showEMA9,
                    "EMA20" to settings.indicators.showEMA20,
                    "EMA50" to settings.indicators.showEMA50,
                    "Volume" to settings.indicators.showVolume
                )
                overlays.forEach { (key, active) ->
                    FilterChip(
                        selected = active,
                        onClick = { onToggleIndicator(key) },
                        label = { Text(key, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Text("Panel Indicators", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                val panels = listOf(
                    "MACD" to settings.indicators.showMACD,
                    "RSI" to settings.indicators.showRSI,
                    "ATR" to settings.indicators.showATR,
                    "Signals" to settings.indicators.showSignals
                )
                panels.forEach { (key, active) ->
                    FilterChip(
                        selected = active,
                        onClick = { onToggleIndicator(key) },
                        label = { Text(key, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Alert Settings Panel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AlertSettingsPanel(
    settings: DayTraderSettings,
    onToggle: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("🔔 Alert Settings", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            val alerts = listOf(
                Triple("buy", "Buy Signal", settings.alertOnBuy),
                Triple("sell", "Sell Signal", settings.alertOnSell),
                Triple("vwap", "VWAP Cross", settings.alertVWAPCross),
                Triple("volume", "Volume Spike", settings.alertVolumeSpike),
                Triple("rsi", "RSI Overbought/Oversold", settings.alertRSIExtreme),
                Triple("open", "Market Open", settings.alertMarketOpen),
                Triple("close15m", "Market Close 15m", settings.alertMarketClose15m)
            )

            alerts.forEach { (key, label, active) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f))
                    Switch(
                        checked = active,
                        onCheckedChange = { onToggle(key) },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Latest Bar Status Card — live signal watch for the current candle
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LatestBarStatusCard(status: LatestBarStatus) {
    val signalColor = if (status.isBullishPattern) GainGreen else LossRed
    val cardBg = when {
        status.confirmedSignal -> signalColor.copy(alpha = 0.08f)
        status.hasPattern      -> HoldSignalColor.copy(alpha = 0.07f)
        else                   -> MaterialTheme.colorScheme.surfaceVariant
    }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔍 Signal Watch — Current Bar",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f))
                if (status.confirmedSignal) {
                    Surface(shape = RoundedCornerShape(5.dp), color = signalColor.copy(alpha = 0.22f)) {
                        Text("CONFIRMED ${if (status.isBullishPattern) "BUY" else "SELL"}",
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color      = signalColor,
                            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            if (!status.hasPattern) {
                Text("${status.patternEmoji} ${status.blockedReasons.firstOrNull() ?: "Waiting for a candlestick pattern…"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            // Pattern + direction badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${status.patternEmoji} ${status.patternName}",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = signalColor)
                Spacer(Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = signalColor.copy(alpha = 0.15f)) {
                    Text(if (status.isBullishPattern) "Bullish" else "Bearish",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = signalColor,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            Spacer(Modifier.height(6.dp))

            // Score progress bar
            val scoreColor = when {
                status.score >= status.scoreNeeded                    -> GainGreen
                status.score >= (status.scoreNeeded * 0.75).toInt()  -> HoldSignalColor
                else                                                  -> LossRed
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Score ${status.score}/${status.scoreNeeded}",
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = scoreColor,
                    modifier   = Modifier.width(92.dp))
                Box(modifier = Modifier.weight(1f).height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))) {
                    Box(modifier = Modifier.fillMaxHeight()
                        .fillMaxWidth((status.score.toFloat() / 100f).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(scoreColor))
                }
            }

            // Blocked reasons
            if (status.blockedReasons.isNotEmpty() && !status.confirmedSignal) {
                Spacer(Modifier.height(5.dp))
                status.blockedReasons.forEach { reason ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 1.dp)) {
                        Text("⛔ ", style = MaterialTheme.typography.labelSmall)
                        Text(reason, style = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                    }
                }
            }

            // Near-miss hints — shown when a signal was close but didn't fire
            if (status.nearMissLayers.isNotEmpty() && !status.confirmedSignal) {
                Spacer(Modifier.height(4.dp))
                status.nearMissLayers.forEach { hint ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 1.dp)) {
                        Text("💡 ", style = MaterialTheme.typography.labelSmall)
                        Text(hint,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.weight(1f))
                    }
                }
            }

            // Expand/collapse confirmation checklist
            if (status.layers.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                TextButton(onClick = { expanded = !expanded },
                    modifier = Modifier.height(26.dp), contentPadding = PaddingValues(0.dp)) {
                    Text(if (expanded) "Hide checklist ▲" else "Show confirmation checklist ▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                if (expanded) {
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        status.layers.forEach { layer ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (layer.passed) "✅" else "❌",
                                    style    = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(22.dp))
                                Text("${layer.name}: ${layer.detail}",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = if (layer.passed) MaterialTheme.colorScheme.onSurface
                                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Backtest Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BacktestCard(result: DayTraderBacktestResult, mode: RiskinessLevel) {
    val winColor = when {
        result.winRate >= 0.55 -> GainGreen
        result.winRate >= 0.45 -> HoldSignalColor
        else                   -> LossRed
    }
    val expColor = if (result.expectancy > 0) GainGreen else LossRed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📊 Backtest · ${result.totalSignals} signals",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(6.dp), color = riskinessColor(mode).copy(alpha = 0.15f)) {
                    Text("${mode.emoji} ${mode.label}",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = riskinessColor(mode),
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Spacer(Modifier.height(8.dp))

            if (result.totalSignals == 0) {
                Text("No completed signals in this dataset — try Aggressive mode or a longer timeframe.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            // Win-rate bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Win Rate", style = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(66.dp))
                Box(modifier = Modifier.weight(1f).height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(LossRed.copy(alpha = 0.18f))) {
                    Box(modifier = Modifier.fillMaxHeight()
                        .fillMaxWidth(result.winRate.toFloat().coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(GainGreen.copy(alpha = 0.6f)))
                }
                Spacer(Modifier.width(8.dp))
                Text("${(result.winRate * 100).toInt()}%",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color      = winColor)
            }
            Spacer(Modifier.height(6.dp))

            // Stats grid (2 columns)
            val stats = listOf(
                Triple("Wins",       result.wins.toString(),                              GainGreen),
                Triple("Losses",     result.losses.toString(),                            LossRed),
                Triple("Avg Gain",   "+${"%.2f".format(result.avgGainPct)}%",             GainGreen),
                Triple("Avg Loss",   "-${"%.2f".format(result.avgLossPct)}%",             LossRed),
                Triple("Expectancy", "${"%.2f".format(result.expectancy)}% per trade",   expColor)
            )
            stats.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (label, value, color) ->
                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold, color = color)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────
private fun riskinessColor(level: RiskinessLevel): Color = when (level) {
    RiskinessLevel.LOW -> Color(0xFF4CAF50)
    RiskinessLevel.MEDIUM -> Color(0xFFFFC107)
    RiskinessLevel.HIGH -> Color(0xFFFF5722)
}

private fun formatPrice(price: Double): String = when {
    price >= 1_000 -> "$${"%,.0f".format(price)}"
    price >= 1.0 -> "$${"%,.2f".format(price)}"
    price >= 0.01 -> "$${"%,.4f".format(price)}"
    else -> "$${"%,.6f".format(price)}"
}

private fun formatVolume(vol: Long): String = when {
    vol >= 1_000_000_000 -> "${"%.1f".format(vol / 1_000_000_000.0)}B"
    vol >= 1_000_000 -> "${"%.1f".format(vol / 1_000_000.0)}M"
    vol >= 1_000 -> "${"%.1f".format(vol / 1_000.0)}K"
    else -> vol.toString()
}

