package com.tradescreenerai.app.ui.screens.alerts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradescreenerai.app.data.model.AssetType
import com.tradescreenerai.app.data.model.IndicatorAlert
import com.tradescreenerai.app.data.model.IndicatorAlertType
import com.tradescreenerai.app.ui.components.common.EmptyState
import com.tradescreenerai.app.ui.components.common.formatPrice
import com.tradescreenerai.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    viewModel: AlertsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Price Alerts", "Indicator Alerts", "🔔 Notifications")

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Alerts", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                if (selectedTab == 0) {
                    IconButton(onClick = { viewModel.showAddDialog() }) {
                        Icon(Icons.Filled.Add, "Add Alert")
                    }
                } else if (selectedTab == 1) {
                    IconButton(onClick = { viewModel.showAddIndicatorDialog() }) {
                        Icon(Icons.Filled.Add, "Add Indicator Alert")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        // Tabs
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        when (selectedTab) {
            0 -> PriceAlertsTab(state = state, viewModel = viewModel)
            1 -> IndicatorAlertsTab(state = state, viewModel = viewModel)
            2 -> NotificationsTab(state = state, viewModel = viewModel)
        }
    }

    // Price alert dialog
    if (state.showAddDialog) {
        AddAlertDialog(
            onDismiss = { viewModel.hideAddDialog() },
            onAdd = { symbol, name, target, isAbove, type ->
                viewModel.addAlert(symbol, name, target, isAbove, type)
            }
        )
    }

    // Indicator alert dialog
    if (state.showAddIndicatorDialog) {
        AddIndicatorAlertDialog(
            onDismiss = { viewModel.hideAddIndicatorDialog() },
            onAdd = { symbol, alertType, assetType ->
                viewModel.addIndicatorAlert(symbol, symbol, alertType, assetType)
            }
        )
    }
}

// ─── Price Alerts Tab ─────────────────────────────────────────────────────────

@Composable
private fun PriceAlertsTab(state: AlertsState, viewModel: AlertsViewModel) {
    if (state.alerts.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.NotificationsActive,
            title = "No price alerts",
            subtitle = "Get notified when a price hits your target",
            actionLabel = "Create Alert",
            onAction = { viewModel.showAddDialog() },
            modifier = Modifier.padding(top = 48.dp)
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.alerts, key = { it.id }) { alert ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (alert.isAbove) Icons.AutoMirrored.Filled.TrendingUp
                            else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (alert.isAbove) GainGreen else LossRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                alert.symbol,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "${if (alert.isAbove) "Above" else "Below"} ${formatPrice(alert.targetPrice)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = alert.isActive,
                            onCheckedChange = { viewModel.toggleAlert(alert.id) }
                        )
                        IconButton(onClick = { viewModel.removeAlert(alert.id) }) {
                            Icon(Icons.Filled.Delete, "Remove", tint = LossRed.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

// ─── Indicator Alerts Tab ─────────────────────────────────────────────────────

@Composable
private fun IndicatorAlertsTab(state: AlertsState, viewModel: AlertsViewModel) {
    if (state.indicatorAlerts.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.ShowChart,
            title = "No indicator alerts",
            subtitle = "Get notified on EMA crossovers, RSI extremes, MACD signals and more",
            actionLabel = "Create Indicator Alert",
            onAction = { viewModel.showAddIndicatorDialog() },
            modifier = Modifier.padding(top = 48.dp)
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.indicatorAlerts, key = { it.id }) { alert ->
                IndicatorAlertCard(alert = alert, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun IndicatorAlertCard(alert: IndicatorAlert, viewModel: AlertsViewModel) {
    val (color, icon) = indicatorAlertStyle(alert.alertType)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    alert.symbol,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    alert.alertType.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
                Text(
                    alert.assetType.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = alert.isActive,
                onCheckedChange = { viewModel.toggleIndicatorAlert(alert.id) }
            )
            IconButton(onClick = { viewModel.removeIndicatorAlert(alert.id) }) {
                Icon(Icons.Filled.Delete, "Remove", tint = LossRed.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun indicatorAlertStyle(type: IndicatorAlertType): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.vector.ImageVector> {
    return when (type) {
        IndicatorAlertType.EMA_CROSSOVER     -> Pair(GainGreen,     Icons.AutoMirrored.Filled.TrendingUp)
        IndicatorAlertType.RSI_OVERBOUGHT    -> Pair(LossRed,       Icons.AutoMirrored.Filled.TrendingDown)
        IndicatorAlertType.RSI_OVERSOLD      -> Pair(GainGreen,     Icons.AutoMirrored.Filled.TrendingUp)
        IndicatorAlertType.MACD_CROSSOVER    -> Pair(ElectricBlue,  Icons.AutoMirrored.Filled.ShowChart)
        IndicatorAlertType.BOLLINGER_BREAKOUT -> Pair(HoldSignalColor, Icons.Filled.BarChart)
        IndicatorAlertType.VWAP_CROSS        -> Pair(AccentTeal,    Icons.Filled.SwapVert)
        IndicatorAlertType.VOLUME_SPIKE      -> Pair(AccentPurple,  Icons.AutoMirrored.Filled.VolumeUp)
    }
}

// ─── Add Price Alert Dialog ───────────────────────────────────────────────────

@Composable
private fun AddAlertDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Double, Boolean, AssetType) -> Unit
) {
    var symbol by remember { mutableStateOf("") }
    var targetPrice by remember { mutableStateOf("") }
    var isAbove by remember { mutableStateOf(true) }
    var isCrypto by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Price Alert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Type: ")
                    FilterChip(selected = !isCrypto, onClick = { isCrypto = false }, label = { Text("Stock") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = isCrypto, onClick = { isCrypto = true }, label = { Text("Crypto") })
                }
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it.uppercase() },
                    label = { Text("Symbol / Coin ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetPrice,
                    onValueChange = { targetPrice = it },
                    label = { Text("Target Price ($)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Alert when price goes: ")
                    FilterChip(selected = isAbove, onClick = { isAbove = true }, label = { Text("Above") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !isAbove, onClick = { isAbove = false }, label = { Text("Below") })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val target = targetPrice.toDoubleOrNull() ?: 0.0
                if (symbol.isNotBlank() && target > 0) {
                    onAdd(symbol, symbol, target, isAbove, if (isCrypto) AssetType.CRYPTO else AssetType.STOCK)
                }
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─── Add Indicator Alert Dialog ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddIndicatorAlertDialog(
    onDismiss: () -> Unit,
    onAdd: (String, IndicatorAlertType, AssetType) -> Unit
) {
    var symbol by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(IndicatorAlertType.EMA_CROSSOVER) }
    var isCrypto by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Indicator Alert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Type: ")
                    FilterChip(selected = !isCrypto, onClick = { isCrypto = false }, label = { Text("Stock") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = isCrypto, onClick = { isCrypto = true }, label = { Text("Crypto") })
                }
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it.uppercase() },
                    label = { Text("Symbol / Coin ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Dropdown for alert type
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Alert Condition") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        IndicatorAlertType.entries.forEach { alertType ->
                            DropdownMenuItem(
                                text = { Text(alertType.displayName) },
                                onClick = { selectedType = alertType; expanded = false }
                            )
                        }
                    }
                }
                // Brief explanation of selected type
                Text(
                    alertTypeDescription(selectedType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (symbol.isNotBlank()) {
                    onAdd(symbol, selectedType, if (isCrypto) AssetType.CRYPTO else AssetType.STOCK)
                }
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun alertTypeDescription(type: IndicatorAlertType): String = when (type) {
    IndicatorAlertType.EMA_CROSSOVER      -> "Alert when the 50 EMA crosses above or below the 200 EMA (Golden/Death Cross)."
    IndicatorAlertType.RSI_OVERBOUGHT     -> "Alert when RSI rises above 70 — price may be overbought."
    IndicatorAlertType.RSI_OVERSOLD       -> "Alert when RSI falls below 30 — price may be oversold and could bounce."
    IndicatorAlertType.MACD_CROSSOVER     -> "Alert when MACD line crosses above or below the signal line."
    IndicatorAlertType.BOLLINGER_BREAKOUT -> "Alert when price breaks outside the Bollinger Bands."
    IndicatorAlertType.VWAP_CROSS         -> "Alert when price crosses above or below the VWAP line."
    IndicatorAlertType.VOLUME_SPIKE       -> "Alert when volume is more than 2× the 20-period average."
}

// ─── Notifications Settings Tab ────────────────────────────────────────────────

@Composable
private fun NotificationsTab(state: AlertsState, viewModel: AlertsViewModel) {
    val prefs = state.notificationPrefs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ElectricBlue.copy(alpha = 0.08f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📲 Push Notifications", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Control which events send a system notification to your phone. " +
                    "Make sure to allow notifications in your device settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Market Session Notifications ──────────────────────────────────────
        NotifSettingCard(
            emoji = "🔔",
            title = "Market Session Alerts",
            subtitle = "Penny stocks & NYSE hours",
            description = "Receive notifications at:\n" +
                "  • ⏰ 9:25 AM ET — Market opens in 5 min\n" +
                "  • 🟢 9:30 AM ET — Market is now open\n" +
                "  • ⏰ 3:55 PM ET — Market closes in 5 min\n" +
                "  • 🔴 4:00 PM ET — Market is now closed",
            enabled = prefs.marketSessionEnabled,
            onToggle = { viewModel.toggleMarketSessionNotifs() },
            accentColor = GainGreen
        )

        // ── Price Alert Notifications ─────────────────────────────────────────
        NotifSettingCard(
            emoji = "📈",
            title = "Price Alert Notifications",
            subtitle = "Stocks, Crypto & Commodities",
            description = "Get a push notification when any of your price alerts trigger. " +
                "Alerts are checked every 15 minutes in the background.",
            enabled = prefs.priceAlertsEnabled,
            onToggle = { viewModel.togglePriceAlertNotifs() },
            accentColor = ElectricBlue
        )

        // ── News Notifications ────────────────────────────────────────────────
        NotifSettingCard(
            emoji = "📰",
            title = "Breaking News Alerts",
            subtitle = "Top US business headlines",
            description = "Get notified when a new top business headline is detected. " +
                "Checks every 15 minutes — only fires when a new article appears.",
            enabled = prefs.urgentNewsEnabled,
            onToggle = { viewModel.toggleNewsNotifs() },
            accentColor = HoldSignalColor
        )

        // ── Info footer ───────────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Filled.Info, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Background checks require an internet connection. " +
                    "Battery optimisation on your device may occasionally delay notifications " +
                    "by a few minutes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NotifSettingCard(
    emoji: String,
    title: String,
    subtitle: String,
    description: String,
    enabled: Boolean,
    onToggle: () -> Unit,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = if (enabled) 0.15f else 0.06f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(emoji, style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor.copy(alpha = if (enabled) 1f else 0.5f)
                    )
                }
                Switch(checked = enabled, onCheckedChange = { onToggle() })
            }
            if (enabled) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(Modifier.height(10.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }
        }
    }
}

