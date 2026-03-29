package com.tradescreenerai.app.ui.screens.signals

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tradescreenerai.app.data.model.BuySignalConfig
import com.tradescreenerai.app.data.model.StrategyType
import com.tradescreenerai.app.ui.components.charts.MiniSparkline
import com.tradescreenerai.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuySignalsScreen(
    onNavigateToCryptoDetail: (String) -> Unit,
    viewModel: BuySignalsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ── Notification permission (Android 13+) ────────────────────────────────
    var showPermissionRationale by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showPermissionRationale = true
    }
    LaunchedEffect(state.config.notificationsEnabled) {
        if (state.config.notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            if (perm != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var showConfig by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Buy Signals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // LIVE pulsing badge
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (!state.isLoading) androidx.compose.ui.graphics.Color(0xFF00C853).copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        if (!state.isLoading) "● LIVE" else "○ Loading…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (!state.isLoading) androidx.compose.ui.graphics.Color(0xFF00C853)
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                if (state.lastScanned > 0 && !state.isLoading) {
                                    Text(
                                        "Updated ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(state.lastScanned))}  •  refresh in ${state.nextRefreshIn}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (state.newSignalCount > 0) {
                        BadgedBox(badge = { Badge { Text("${state.newSignalCount}") } }) {
                            Icon(Icons.Filled.Notifications, contentDescription = "New signals")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { showConfig = !showConfig }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configure")
                    }
                    IconButton(onClick = { viewModel.scan() }, enabled = !state.isLoading) {
                        if (state.isLoading)
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Config panel (expandable) ─────────────────────────────────────
            AnimatedVisibility(
                visible = showConfig,
                enter   = expandVertically(),
                exit    = shrinkVertically()
            ) {
                ConfigPanel(
                    config    = state.config,
                    onApply   = { newCfg ->
                        viewModel.applyConfigAndRescan(newCfg)
                        showConfig = false
                    },
                    modifier  = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ── Permission rationale banner ───────────────────────────────────
            if (showPermissionRationale) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.NotificationsOff, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Notification permission denied. Grant it in Settings to receive buy signal alerts.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showPermissionRationale = false }) {
                            Icon(Icons.Filled.Close, "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ── Content ───────────────────────────────────────────────────────
            when {
                state.isLoading && state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator()
                            Text("Scanning ${state.config.pool} coins…", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Filled.Warning, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                            Text(state.error ?: "Error", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { viewModel.scan() }) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
                state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.AutoMirrored.Filled.TrendingDown, null, modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Text("No signals found right now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Scanned ${state.config.pool} — no Supertrend, RSI, EMA or momentum signals detected.\nTry 'Fast' sensitivity or scan again soon.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            OutlinedButton(onClick = { showConfig = true }) {
                                Icon(Icons.Filled.Settings, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Adjust Settings")
                            }
                        }
                    }
                }
                else -> {
                    // Header count
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = GainGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "${state.items.size} in BUY zone",
                                style = MaterialTheme.typography.labelMedium,
                                color = GainGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "• ${state.config.pool}  •  ${state.config.speed} speed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // ── Strategy filter chips ─────────────────────────────────
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = state.selectedStrategy == null,
                            onClick = { viewModel.selectStrategy(null) },
                            label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(30.dp)
                        )
                        StrategyType.entries.forEach { strat ->
                            FilterChip(
                                selected = state.selectedStrategy == strat,
                                onClick = { viewModel.selectStrategy(if (state.selectedStrategy == strat) null else strat) },
                                label = { Text("${strat.emoji} ${strat.displayName}", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }

                    // ── Filtered items ────────────────────────────────────────
                    val filteredItems = if (state.selectedStrategy != null) {
                        state.items.filter { it.strategy == state.selectedStrategy }
                    } else state.items

                    if (filteredItems.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No signals match this filter", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement   = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredItems, key = { it.crypto.id }) { item ->
                                BuySignalCard(
                                    item    = item,
                                    onClick = { onNavigateToCryptoDetail(item.crypto.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Individual buy signal card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BuySignalCard(
    item: BuySignalItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPositive = item.crypto.changePercent24h >= 0
    val changeColor = if (isPositive) GainGreen else LossRed
    var expanded by remember { mutableStateOf(false) }

    val scoreColor = when {
        item.confluenceScore >= 70 -> GainGreen
        item.confluenceScore >= 50 -> androidx.compose.ui.graphics.Color(0xFFFFC107)
        item.confluenceScore >= 30 -> androidx.compose.ui.graphics.Color(0xFFFF9800)
        else -> LossRed
    }

    Card(
        onClick  = { if (item.reasons.isNotEmpty()) expanded = !expanded else onClick() },
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Coin header
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.crypto.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.crypto.imageUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.crypto.symbol,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.crypto.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Confluence score badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = scoreColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        "${item.confluenceScore}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scoreColor,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Strategy + BUY tags
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = GainGreen.copy(alpha = 0.2f)
                ) {
                    Text("BUY", style = MaterialTheme.typography.labelSmall,
                        color = GainGreen, fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
                if (item.strategy != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text("${item.strategy.emoji} ${item.strategy.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                }
                if (item.riskOverlay.riskRewardRatio > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                    ) {
                        Text("R:R ${"%.1f".format(item.riskOverlay.riskRewardRatio)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Price
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text(
                    formatPrice(item.crypto.price),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(
                        if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = changeColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "${if (isPositive) "+" else ""}${"%.2f".format(item.crypto.changePercent24h)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = changeColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Confluence label
            Text(
                "${item.confluenceLabel} · Vol: ${formatVolume(item.crypto.volume24h)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(4.dp))

            // Signal reason chip
            if (item.reason.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = GainGreen.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "📡 ${item.reason}",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = GainGreen,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            // ── Expandable "Why This Trade" ──────────────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text("💡 Why This Trade", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    item.reasons.forEach { reason ->
                        Text("• $reason", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (item.riskOverlay.entryPrice > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("🛡️ SL: ${formatPrice(item.riskOverlay.stopLoss)} · TP: ${formatPrice(item.riskOverlay.takeProfit)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                        Text("View Details →", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Mini sparkline
            if (item.sparkline.isNotEmpty() && !expanded) {
                MiniSparkline(
                    data       = item.sparkline,
                    isPositive = true,
                    modifier   = Modifier.fillMaxWidth().height(40.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Config panel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ConfigPanel(
    config: BuySignalConfig,
    onApply: (BuySignalConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var pool          by remember(config) { mutableStateOf(config.pool) }
    var speed         by remember(config) { mutableStateOf(config.speed) }
    var notifEnabled  by remember(config) { mutableStateOf(config.notificationsEnabled) }

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Scanner Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            // Pool selector
            Text("Coin Pool", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SegmentedOptionRow(
                options   = listOf("Top 20", "Top 50", "Top 100"),
                selected  = pool,
                onSelect  = { pool = it }
            )

            // Speed selector
            Text("Sensitivity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SegmentedOptionRow(
                options   = listOf("Fast", "Medium", "Slow"),
                selected  = speed,
                onSelect  = { speed = it }
            )

            // Notifications toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Switch(checked = notifEnabled, onCheckedChange = { notifEnabled = it })
            }

            // Sensitivity hint
            val hint = when (speed) {
                "Fast"   -> "ATR 7 / ×2.0 — more signals, noisier"
                "Medium" -> "ATR 10 / ×3.0 — balanced"
                else     -> "ATR 14 / ×4.0 — fewer but stronger signals"
            }
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))

            Button(
                onClick  = { onApply(BuySignalConfig(pool, speed, notifEnabled)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Apply & Scan")
            }
        }
    }
}

@Composable
private fun SegmentedOptionRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { opt ->
            val isSel = opt == selected
            FilterChip(
                selected = isSel,
                onClick  = { onSelect(opt) },
                label    = { Text(opt, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.weight(1f).height(32.dp)
            )
        }
    }
}

// ── Price formatter (shared helper, avoids pulling in the full detail screen) ──
private fun formatPrice(price: Double): String = when {
    price >= 1_000 -> "$${"%,.0f".format(price)}"
    price >= 1.0   -> "$${"%,.2f".format(price)}"
    price >= 0.01  -> "$${"%,.4f".format(price)}"
    else           -> "$${"%,.6f".format(price)}"
}

private fun formatVolume(vol: Long): String = when {
    vol >= 1_000_000_000 -> "${"%.1f".format(vol / 1_000_000_000.0)}B"
    vol >= 1_000_000     -> "${"%.1f".format(vol / 1_000_000.0)}M"
    vol >= 1_000         -> "${"%.1f".format(vol / 1_000.0)}K"
    else                 -> vol.toString()
}

