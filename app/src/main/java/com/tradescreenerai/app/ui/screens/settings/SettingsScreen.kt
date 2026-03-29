package com.tradescreenerai.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradescreenerai.app.BuildConfig
import com.tradescreenerai.app.ui.theme.*

// ─── Main composable ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAlerts: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── 1. Notifications ──────────────────────────────────────────────
            item {
                SettingsSection(title = "Notifications", icon = Icons.Filled.Notifications, iconColor = ElectricBlue) {
                    ToggleRow(
                        label = "Enable All Notifications",
                        checked = s.notifAllEnabled,
                        bold = true
                    ) { viewModel.toggle { it.copy(notifAllEnabled = !it.notifAllEnabled) } }
                    Divider()
                    ToggleRow("Buy Signal Notifications", s.notifBuySignals, enabled = s.notifAllEnabled) {
                        viewModel.toggle { it.copy(notifBuySignals = !it.notifBuySignals) }
                    }
                    Divider()
                    ToggleRow("Sell Signal Notifications", s.notifSellSignals, enabled = s.notifAllEnabled) {
                        viewModel.toggle { it.copy(notifSellSignals = !it.notifSellSignals) }
                    }
                    Divider()
                    ToggleRow("Price Alert Notifications", s.notifPriceAlerts, enabled = s.notifAllEnabled) {
                        viewModel.toggle { it.copy(notifPriceAlerts = !it.notifPriceAlerts) }
                    }
                    Divider()
                    ToggleRow("Market Open Notification", s.notifMarketOpen, enabled = s.notifAllEnabled) {
                        viewModel.toggle { it.copy(notifMarketOpen = !it.notifMarketOpen) }
                    }
                    Divider()
                    ToggleRow("Market Close Notification", s.notifMarketClose, enabled = s.notifAllEnabled) {
                        viewModel.toggle { it.copy(notifMarketClose = !it.notifMarketClose) }
                    }
                    Divider()
                    ToggleRow("High Volatility Alert", s.notifHighVolatility, enabled = s.notifAllEnabled) {
                        viewModel.toggle { it.copy(notifHighVolatility = !it.notifHighVolatility) }
                    }
                    Divider()
                    ToggleRow("Sound", s.notifSound, enabled = s.notifAllEnabled) {
                        viewModel.toggle { it.copy(notifSound = !it.notifSound) }
                    }
                    Divider()
                    ToggleRow("Vibration", s.notifVibration, enabled = s.notifAllEnabled) {
                        viewModel.toggle { it.copy(notifVibration = !it.notifVibration) }
                    }
                }
            }

            // ── 2. Trading Signals ────────────────────────────────────────────
            item {
                SettingsSection(title = "Trading Signals", icon = Icons.Filled.BubbleChart, iconColor = GainGreen) {
                    DropdownRow(
                        label = "Default Signal Mode",
                        current = s.signalMode,
                        options = listOf("Aggressive", "Balanced", "Conservative"),
                        onSelect = { viewModel.toggle { it.copy(signalMode = this) } }
                    )
                    Divider()
                    ToggleRow("Show Confidence Score", s.showConfidenceScore) {
                        viewModel.toggle { it.copy(showConfidenceScore = !it.showConfidenceScore) }
                    }
                    Divider()
                    ToggleRow("Show Signal Reasons", s.showSignalReasons) {
                        viewModel.toggle { it.copy(showSignalReasons = !it.showSignalReasons) }
                    }
                    Divider()
                    ToggleRow("Show Rejected Signal Diagnostics", s.showRejectedDiagnostics) {
                        viewModel.toggle { it.copy(showRejectedDiagnostics = !it.showRejectedDiagnostics) }
                    }
                }
            }

            // ── 3. Chart Settings ─────────────────────────────────────────────
            item {
                SettingsSection(title = "Chart Settings", icon = Icons.AutoMirrored.Filled.ShowChart, iconColor = AccentTeal) {
                    DropdownRow(
                        label = "Default Timeframe",
                        current = s.defaultChartTimeframe,
                        options = listOf("1m", "5m", "15m", "1H", "4H", "1D", "1W"),
                        onSelect = { viewModel.toggle { it.copy(defaultChartTimeframe = this) } }
                    )
                    Divider()
                    ToggleRow("Candlestick Chart", s.chartShowCandlesticks) {
                        viewModel.toggle { it.copy(chartShowCandlesticks = !it.chartShowCandlesticks) }
                    }
                    Divider()
                    ToggleRow("Volume Bars", s.chartShowVolume) {
                        viewModel.toggle { it.copy(chartShowVolume = !it.chartShowVolume) }
                    }
                    Divider()
                    ToggleRow("Moving Averages", s.chartShowMovingAverages) {
                        viewModel.toggle { it.copy(chartShowMovingAverages = !it.chartShowMovingAverages) }
                    }
                    Divider()
                    ToggleRow("RSI Indicator", s.chartShowRSI) {
                        viewModel.toggle { it.copy(chartShowRSI = !it.chartShowRSI) }
                    }
                    Divider()
                    ToggleRow("MACD Indicator", s.chartShowMACD) {
                        viewModel.toggle { it.copy(chartShowMACD = !it.chartShowMACD) }
                    }
                    Divider()
                    ToggleRow("Support & Resistance Lines", s.chartShowSupportResistance) {
                        viewModel.toggle { it.copy(chartShowSupportResistance = !it.chartShowSupportResistance) }
                    }
                    Divider()
                    ToggleRow("Auto-Jump to Latest Candle", s.chartAutoJumpToLatest) {
                        viewModel.toggle { it.copy(chartAutoJumpToLatest = !it.chartAutoJumpToLatest) }
                    }
                }
            }

            // ── 4. Appearance ─────────────────────────────────────────────────
            item {
                SettingsSection(title = "Appearance", icon = Icons.Filled.Palette, iconColor = AccentPurple) {
                    SegmentedRow(
                        label = "Theme",
                        options = listOf("Light", "Dark", "System"),
                        selected = s.themeMode.replaceFirstChar { it.uppercase() },
                        onSelect = { viewModel.toggle { s -> s.copy(themeMode = this.lowercase()) } }
                    )
                    Divider()
                    ToggleRow("Compact Mode", s.compactMode) {
                        viewModel.toggle { it.copy(compactMode = !it.compactMode) }
                    }
                    Divider()
                    ToggleRow("Larger Text", s.largerTextMode) {
                        viewModel.toggle { it.copy(largerTextMode = !it.largerTextMode) }
                    }
                }
            }

            // ── 5. Market Preferences ─────────────────────────────────────────
            item {
                SettingsSection(title = "Market Preferences", icon = Icons.Filled.Public, iconColor = HoldSignalColor) {
                    DropdownRow(
                        label = "Default Market",
                        current = s.defaultMarket,
                        options = listOf("Stocks", "Crypto", "Forex", "Commodities"),
                        onSelect = { viewModel.toggle { it.copy(defaultMarket = this) } }
                    )
                    Divider()
                    ToggleRow("Pre-Market Data", s.showPreMarketData) {
                        viewModel.toggle { it.copy(showPreMarketData = !it.showPreMarketData) }
                    }
                    Divider()
                    ToggleRow("After-Hours Data", s.showAfterHoursData) {
                        viewModel.toggle { it.copy(showAfterHoursData = !it.showAfterHoursData) }
                    }
                    Divider()
                    DropdownRow(
                        label = "Time Zone Display",
                        current = s.timeZoneDisplay,
                        options = listOf("ET", "UTC", "Local"),
                        onSelect = { viewModel.toggle { it.copy(timeZoneDisplay = this) } }
                    )
                    Divider()
                    DropdownRow(
                        label = "Currency Display",
                        current = s.currencyDisplay,
                        options = listOf("USD", "EUR", "GBP", "JPY", "CAD", "AUD"),
                        onSelect = { viewModel.toggle { it.copy(currencyDisplay = this) } }
                    )
                }
            }

            // ── 6. Alerts ─────────────────────────────────────────────────────
            item {
                SettingsSection(title = "Alerts", icon = Icons.Filled.NotificationsActive, iconColor = LossRed) {
                    ActionRow(label = "Manage Price Alerts", onClick = onNavigateToAlerts)
                    Divider()
                    ActionRow(label = "Manage Watchlist Alerts", onClick = onNavigateToAlerts)
                    Divider()
                    ToggleRow("Allow Repeated Alerts", s.allowRepeatedAlerts) {
                        viewModel.toggle { it.copy(allowRepeatedAlerts = !it.allowRepeatedAlerts) }
                    }
                    Divider()
                    DropdownRow(
                        label = "Snooze Duration",
                        current = "${s.snoozeDurationMinutes} min",
                        options = listOf("5 min", "15 min", "30 min", "1 hour", "2 hours"),
                        onSelect = {
                            val mins = when (this) {
                                "5 min"   -> 5
                                "15 min"  -> 15
                                "1 hour"  -> 60
                                "2 hours" -> 120
                                else      -> 30
                            }
                            viewModel.toggle { it.copy(snoozeDurationMinutes = mins) }
                        }
                    )
                }
            }

            // ── 7. Account & App ──────────────────────────────────────────────
            item {
                SettingsSection(title = "Account & App", icon = Icons.Filled.ManageAccounts, iconColor = SoftBlue) {
                    ActionRow(
                        label = "Edit Profile",
                        icon = Icons.Filled.Person,
                        onClick = { /* future: navigate to profile */ }
                    )
                    Divider()
                    ActionRow(
                        label = "Subscription / Plan",
                        icon = Icons.Filled.Star,
                        onClick = { /* future: subscription screen */ }
                    )
                    Divider()
                    ActionRow(
                        label = "Privacy Policy",
                        icon = Icons.Filled.PrivacyTip,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/privacy"))
                            context.startActivity(intent)
                        }
                    )
                    Divider()
                    ActionRow(
                        label = "Terms & Conditions",
                        icon = Icons.Filled.Description,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/terms"))
                            context.startActivity(intent)
                        }
                    )
                    Divider()
                    ActionRow(
                        label = "Clear Cache",
                        icon = Icons.Filled.DeleteSweep,
                        labelColor = LossRed,
                        onClick = { showClearCacheDialog = true }
                    )
                    Divider()
                    ActionRow(
                        label = "Reset All Settings to Default",
                        icon = Icons.Filled.RestartAlt,
                        labelColor = LossRed,
                        onClick = { showResetDialog = true }
                    )
                    Divider()
                    InfoRow(label = "App Version", value = BuildConfig.VERSION_NAME)
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // ── Reset confirmation dialog ─────────────────────────────────────────────
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Filled.RestartAlt, null, tint = LossRed) },
            title = { Text("Reset Settings?") },
            text = { Text("All settings will be restored to their defaults. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.resetToDefaults(); showResetDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed)
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Clear cache confirmation dialog ───────────────────────────────────────
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            icon = { Icon(Icons.Filled.DeleteSweep, null, tint = LossRed) },
            title = { Text("Clear Cache?") },
            text = { Text("Cached data will be cleared. The app may reload data on next launch.") },
            confirmButton = {
                Button(
                    onClick = {
                        context.cacheDir.deleteRecursively()
                        showClearCacheDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed)
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ─── Section Wrapper ──────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = iconColor
            )
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

// ─── Row: Toggle (Switch) ─────────────────────────────────────────────────────

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    bold: Boolean = false,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = if (bold) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = enabled
        )
    }
}

// ─── Row: Dropdown ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownRow(
    label: String,
    current: String,
    options: List<String>,
    onSelect: String.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        current,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { option.onSelect(); expanded = false },
                        leadingIcon = if (option == current) ({
                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp))
                        }) else null
                    )
                }
            }
        }
    }
}

// ─── Row: Segmented (for theme selection) ─────────────────────────────────────

@Composable
private fun SegmentedRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: String.() -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { option.onSelect() },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(option, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

// ─── Row: Action (navigate or trigger) ───────────────────────────────────────

@Composable
private fun ActionRow(
    label: String,
    icon: ImageVector? = null,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = labelColor.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor
        )
        Icon(
            Icons.Filled.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ─── Row: Info (read-only) ────────────────────────────────────────────────────

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Internal divider ─────────────────────────────────────────────────────────

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    )
}

