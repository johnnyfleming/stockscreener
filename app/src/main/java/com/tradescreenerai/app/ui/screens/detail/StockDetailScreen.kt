package com.tradescreenerai.app.ui.screens.detail

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
import com.tradescreenerai.app.data.model.ChartTool
import com.tradescreenerai.app.data.model.DrawnObject
import com.tradescreenerai.app.data.model.PricePoint
import com.tradescreenerai.app.data.model.SignalType
import com.tradescreenerai.app.ui.components.charts.*
import com.tradescreenerai.app.ui.components.*
import com.tradescreenerai.app.ui.components.cards.NewsCard
import com.tradescreenerai.app.ui.components.common.*
import com.tradescreenerai.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    symbol: String,
    onBack: () -> Unit,
    onLearn: (() -> Unit)? = null,
    onDayTrader: (() -> Unit)? = null,
    viewModel: StockDetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAllIndicators by remember { mutableStateOf(false) }
    var showCurrencyMenu by remember { mutableStateOf(false) }

    // ── Drawing tool dialogs ─────────────────────────────────────────────────
    var posEntryText by remember { mutableStateOf("") }
    var posSlText    by remember { mutableStateOf("") }
    var posTpText    by remember { mutableStateOf("") }
    var hlinePriceText by remember { mutableStateOf("") }
    var hlineLabelText by remember { mutableStateOf("") }

    LaunchedEffect(state.showPositionDialog) {
        if (state.showPositionDialog) {
            posEntryText = state.stock?.price?.let { "%.4f".format(it) } ?: ""
            posSlText = ""; posTpText = ""
        }
    }

    // Long/Short Position Dialog
    if (state.showPositionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAllDialogs() },
            title = {
                Text(
                    if (state.positionIsLong) "⬆ Long Position" else "⬇ Short Position",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (state.positionIsLong)
                            "Define your long trade. The chart will show green profit zone and red risk zone."
                        else
                            "Define your short trade. The chart will show the position zones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = posEntryText,
                        onValueChange = { posEntryText = it },
                        label = { Text("Entry Price") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                    OutlinedTextField(
                        value = posSlText,
                        onValueChange = { posSlText = it },
                        label = { Text(if (state.positionIsLong) "Stop Loss (below entry)" else "Stop Loss (above entry)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                    OutlinedTextField(
                        value = posTpText,
                        onValueChange = { posTpText = it },
                        label = { Text(if (state.positionIsLong) "Take Profit (above entry)" else "Take Profit (below entry)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                    // Live R:R preview
                    val e  = posEntryText.toDoubleOrNull() ?: 0.0
                    val sl = posSlText.toDoubleOrNull()    ?: 0.0
                    val tp = posTpText.toDoubleOrNull()    ?: 0.0
                    if (e > 0 && sl > 0 && tp > 0) {
                        val risk   = if (state.positionIsLong) e - sl else sl - e
                        val reward = if (state.positionIsLong) tp - e else e - tp
                        val rr     = if (risk > 0) reward / risk else 0.0
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = (if (rr >= 1.5) GainGreen else HoldSignalColor).copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Risk", style = MaterialTheme.typography.labelSmall, color = LossRed)
                                    Text("${"%.2f".format(risk / e * 100)}%", fontWeight = FontWeight.Bold, color = LossRed, style = MaterialTheme.typography.labelMedium)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Reward", style = MaterialTheme.typography.labelSmall, color = GainGreen)
                                    Text("+${"%.2f".format(reward / e * 100)}%", fontWeight = FontWeight.Bold, color = GainGreen, style = MaterialTheme.typography.labelMedium)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("R:R", style = MaterialTheme.typography.labelSmall)
                                    Text("1:${"%.2f".format(rr)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
                                        color = if (rr >= 2.0) GainGreen else if (rr >= 1.0) HoldSignalColor else LossRed)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addPosition(
                        isLong     = state.positionIsLong,
                        entry      = posEntryText.toDoubleOrNull() ?: 0.0,
                        stopLoss   = posSlText.toDoubleOrNull()    ?: 0.0,
                        takeProfit = posTpText.toDoubleOrNull()    ?: 0.0
                    )
                }) { Text("Add to Chart") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAllDialogs() }) { Text("Cancel") }
            }
        )
    }

    // Horizontal Line Dialog
    if (state.showHLineDialog) {
        LaunchedEffect(Unit) {
            hlinePriceText = state.stock?.price?.let { "%.4f".format(it) } ?: ""
            hlineLabelText = ""
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissAllDialogs() },
            title = { Text("➖ Horizontal Price Level", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Draw a horizontal line at a specific price level — useful for support, resistance, or target levels.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = hlinePriceText, onValueChange = { hlinePriceText = it },
                        label = { Text("Price") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                    OutlinedTextField(
                        value = hlineLabelText, onValueChange = { hlineLabelText = it },
                        label = { Text("Label (optional, e.g. Support)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addHorizontalLine(
                        hlinePriceText.toDoubleOrNull() ?: 0.0,
                        hlineLabelText
                    )
                }) { Text("Add Line") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAllDialogs() }) { Text("Cancel") }
            }
        )
    }

    // Fibonacci Dialog
    if (state.showFibDialog) {
        var fibHiText by remember { mutableStateOf("") }
        var fibLoText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissAllDialogs() },
            title = { Text("🌀 Fibonacci Retracement", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter the swing high and swing low to draw Fibonacci retracement levels (23.6%, 38.2%, 50%, 61.8%, 78.6%).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = fibHiText, onValueChange = { fibHiText = it },
                        label = { Text("Swing High Price") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                    OutlinedTextField(
                        value = fibLoText, onValueChange = { fibLoText = it },
                        label = { Text("Swing Low Price") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val hi = fibHiText.toDoubleOrNull() ?: 0.0
                    val lo = fibLoText.toDoubleOrNull() ?: 0.0
                    if (hi > 0 && lo > 0 && hi != lo) {
                        val hiIdx = state.chartEntries.indexOfFirst { it.high >= hi }.takeIf { it >= 0 }
                            ?: (state.chartEntries.size / 3)
                        val loIdx = state.chartEntries.indexOfFirst { it.low <= lo }.takeIf { it >= 0 }
                            ?: (state.chartEntries.size * 2 / 3)
                        val fib = com.tradescreenerai.app.data.model.DrawnObject.FibRetracement(
                            if (hi >= lo) hiIdx else loIdx, maxOf(hi, lo),
                            if (hi >= lo) loIdx else hiIdx, minOf(hi, lo)
                        )
                        viewModel.addFibRetracement(fib)
                    }
                }) { Text("Add Fibonacci") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAllDialogs() }) { Text("Cancel") }
            }
        )
    }

    LaunchedEffect(symbol) { viewModel.loadStock(symbol) }

    // Derived: price converted to selected currency
    val convertedPrice  = (state.stock?.price ?: 0.0) * state.currencyRate
    val convertedChange = (state.stock?.change ?: 0.0) * state.currencyRate
    val currSym         = state.currencySymbol

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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

        if (state.isLoading && state.stock == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }

        val stock = state.stock ?: return

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            // ── Header ────────────────────────────────────────────────────────
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(stock.symbol.take(2), style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stock.name.ifBlank { stock.symbol }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stock.symbol, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (stock.exchange.isNotBlank()) {
                            Text(" · ${stock.exchange}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // ── Price row with currency picker ────────────────────────
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatConvertedPrice(convertedPrice, currSym),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        // Currency picker button
                        Box {
                            AssistChip(
                                onClick = { showCurrencyMenu = true },
                                label = { Text(state.displayCurrency, style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = { Icon(Icons.Filled.CurrencyExchange, null, Modifier.size(14.dp)) },
                                modifier = Modifier.height(30.dp)
                            )
                            DropdownMenu(
                                expanded = showCurrencyMenu,
                                onDismissRequest = { showCurrencyMenu = false }
                            ) {
                                CURRENCY_SYMBOLS.keys.forEach { currency ->
                                    DropdownMenuItem(
                                        text = {
                                            Row {
                                                Text(CURRENCY_SYMBOLS[currency] ?: "", modifier = Modifier.width(24.dp))
                                                Text(currency)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setCurrency(currency)
                                            showCurrencyMenu = false
                                        },
                                        trailingIcon = if (state.displayCurrency == currency) ({
                                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }) else null
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PriceChangeChip(stock.changePercent)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${if (convertedChange >= 0) "+" else ""}${formatConvertedPrice(convertedChange, currSym)} today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

            // ── Traditional Signal Badge ───────────────────────────────────────
            item {
                Spacer(Modifier.height(10.dp))
                SignalBadge(state.technicalData.signal, Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            }

            // ── Day Trader Mode Button ──────────────────────────────────────
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

            // ── Chart ─────────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                // Chart type selector (Candle / Line)
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Candle", "Line").forEach { type ->
                        FilterChip(
                            selected = state.selectedChartType == type,
                            onClick  = { viewModel.selectChartType(type) },
                            label    = { Text(type, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                // ── Professional Chart Toolbar ─────────────────────────────
                ChartToolbar(
                    selectedTool      = state.selectedChartTool,
                    waitingForAnchor2 = state.waitingForAnchor2,
                    hoveredPrice      = state.hoveredPrice,
                    hasDrawings       = state.drawnObjects.isNotEmpty() ||
                                        state.drawAnchor1 != null,
                    onToolSelect      = { viewModel.selectChartTool(it) },
                    onSetAnchor1      = { viewModel.setAnchorPoint1() },
                    onSetAnchor2      = { viewModel.setAnchorPoint2() },
                    onUndo            = { viewModel.undoLastDrawing() },
                    onClear           = { viewModel.clearAllDrawings() },
                    modifier          = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(8.dp))

                when (state.selectedChartType) {
                    "Candle" -> CandlestickChart(
                        data = state.chartEntries,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        buySellSignals = state.buySellSignals,
                        supertrendLine = state.supertrendLine,
                        confluenceZones = if (state.indicatorVisibility.showConfluenceZones) state.confluenceZones else emptyList(),
                        candlePatterns = state.candlePatterns,
                        showSignalProgress = state.indicatorVisibility.showSignalProgress,
                        drawnObjects = state.drawnObjects,
                        onIndexSelected = { idx, price -> viewModel.onChartHover(idx, price) }
                    )
                    "Line" -> PriceLineChart(
                        data = state.chartEntries.map { PricePoint(it.timestamp, it.close) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                        buySellSignals = state.buySellSignals
                    )
                }
                Spacer(Modifier.height(8.dp))
                TimeRangeSelector(
                    ranges   = listOf("1D", "5D", "1W", "1M", "3M", "6M", "1Y", "ALL"),
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
                        patterns     = state.candlePatterns,
                        prediction   = state.patternPrediction,
                        currentPrice = state.chartEntries.lastOrNull()?.close ?: 0.0,
                        modifier     = Modifier.padding(horizontal = 16.dp)
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
                SectionHeader("Technical Indicators",
                    action = if (showAllIndicators) "Advanced ▲" else "Advanced ▼",
                    onAction = { showAllIndicators = !showAllIndicators })
                IndicatorTogglesPanel(
                    visibility = state.indicatorVisibility,
                    onToggle   = { viewModel.toggleIndicator(it) },
                    showAdvanced = showAllIndicators,
                    modifier   = Modifier.padding(horizontal = 16.dp)
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
                        // Always visible core
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

            // ── Key Stats ─────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Key Statistics")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        StatRow("Open", formatPrice(stock.open))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        StatRow("High", formatPrice(stock.high))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        StatRow("Low", formatPrice(stock.low))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        StatRow("Prev Close", formatPrice(stock.previousClose))
                        if (stock.volume > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("Volume", formatLargeNumber(stock.volume))
                        }
                        if (stock.marketCap > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("Market Cap", formatLargeNumber(stock.marketCap))
                        }
                        if (stock.pe > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("P/E Ratio", String.format(java.util.Locale.US, "%.2f", stock.pe))
                        }
                        if (stock.dividend > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("Dividend Yield", "${String.format(java.util.Locale.US, "%.2f", stock.dividend * 100)}%")
                        }
                        if (stock.sector.isNotBlank()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            StatRow("Sector", stock.sector)
                        }
                    }
                }
            }

            // ── Sentiment ─────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Social Sentiment")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    SentimentGauge(sentiment = state.sentiment, modifier = Modifier.padding(16.dp))
                }
            }

            // ── About ─────────────────────────────────────────────────────────
            if (stock.description.isNotBlank()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("About")
                    Text(stock.description, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            // ── Company News ──────────────────────────────────────────────────
            if (state.relatedNews.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("Company News")
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

/** Format a converted price with the appropriate currency symbol. */
private fun formatConvertedPrice(price: Double, currencySymbol: String): String {
    if (price == 0.0) return "${currencySymbol}0.00"
    return when {
        price >= 1.0  -> "$currencySymbol${String.format(java.util.Locale.US, "%,.2f", price)}"
        price >= 0.01 -> "$currencySymbol${String.format(java.util.Locale.US, "%.4f", price)}"
        else          -> "$currencySymbol${String.format(java.util.Locale.US, "%.6f", price)}"
    }
}

