package com.tradescreenerai.app.ui.components.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.ui.theme.*

// ==================== SIGNAL BADGE ====================

@Composable
fun SignalBadge(signal: Signal, modifier: Modifier = Modifier) {
    val (color, icon) = when (signal.type) {
        SignalType.BUY -> Pair(BuySignalColor, Icons.AutoMirrored.Filled.TrendingUp)
        SignalType.SELL -> Pair(SellSignalColor, Icons.AutoMirrored.Filled.TrendingDown)
        SignalType.HOLD -> Pair(HoldSignalColor, Icons.AutoMirrored.Filled.TrendingFlat)
    }
    val strengthText = when (signal.strength) {
        SignalStrength.STRONG -> "Strong"
        SignalStrength.MODERATE -> "Moderate"
        SignalStrength.WEAK -> "Weak"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$strengthText ${signal.type.name}",
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ==================== PRICE CHANGE CHIP ====================

@Composable
fun PriceChangeChip(changePercent: Double, modifier: Modifier = Modifier) {
    val isPositive = changePercent >= 0
    val color = if (isPositive) GainGreen else LossRed
    val bgColor = if (isPositive) GainGreenBg else LossRedBg
    val arrow = if (isPositive) "▲" else "▼"

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = "$arrow ${String.format("%.2f", kotlin.math.abs(changePercent))}%",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ==================== SENTIMENT GAUGE ====================

@Composable
fun SentimentGauge(sentiment: SentimentData, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Bullish", style = MaterialTheme.typography.labelSmall, color = GainGreen)
            Text("Bearish", style = MaterialTheme.typography.labelSmall, color = LossRed)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(sentiment.bullishPercent.coerceAtLeast(1.0).toFloat())
                    .fillMaxHeight()
                    .background(GainGreen)
            )
            Box(
                modifier = Modifier
                    .weight(sentiment.bearishPercent.coerceAtLeast(1.0).toFloat())
                    .fillMaxHeight()
                    .background(LossRed)
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${String.format("%.0f", sentiment.bullishPercent)}%",
                style = MaterialTheme.typography.labelSmall,
                color = GainGreen
            )
            Text(
                "${sentiment.totalMentions} mentions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${String.format("%.0f", sentiment.bearishPercent)}%",
                style = MaterialTheme.typography.labelSmall,
                color = LossRed
            )
        }
    }
}

// ==================== STAT ROW ====================

@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ==================== SHIMMER LOADING ====================

@Composable
fun ShimmerEffect(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing)),
        label = "shimmer_anim"
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
    )
}

@Composable
fun ShimmerCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ShimmerEffect(Modifier.fillMaxWidth(0.5f).height(16.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerEffect(Modifier.fillMaxWidth(0.3f).height(24.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerEffect(Modifier.fillMaxWidth().height(12.dp))
        }
    }
}

// ==================== SECTION HEADER ====================

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (action != null) {
            TextButton(onClick = { onAction?.invoke() }) {
                Text(action, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ==================== EMPTY STATE ====================

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onAction?.invoke() }) {
                Text(actionLabel)
            }
        }
    }
}

// ==================== FORMAT HELPERS ====================

fun formatPrice(price: Double): String {
    return when {
        price >= 1.0 -> "$${String.format("%,.2f", price)}"
        price >= 0.01 -> "$${String.format("%.4f", price)}"
        else -> "$${String.format("%.6f", price)}"
    }
}

fun formatLargeNumber(value: Long): String {
    return when {
        value >= 1_000_000_000_000 -> "${String.format("%.2f", value / 1_000_000_000_000.0)}T"
        value >= 1_000_000_000 -> "${String.format("%.2f", value / 1_000_000_000.0)}B"
        value >= 1_000_000 -> "${String.format("%.2f", value / 1_000_000.0)}M"
        value >= 1_000 -> "${String.format("%.1f", value / 1_000.0)}K"
        else -> value.toString()
    }
}

fun formatLargeNumber(value: Double): String {
    return formatLargeNumber(value.toLong())
}

// ==================== SMART SCORE CARD ====================

@Composable
fun SmartScoreCard(score: SmartSignalScore, modifier: Modifier = Modifier) {
    val scoreColor = when {
        score.score >= 80 -> GainGreen
        score.score >= 60 -> GainGreenLight
        score.score >= 40 -> HoldSignalColor
        score.score >= 20 -> LossRedLight
        else              -> LossRed
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Smart Signal Score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(score.label, style = MaterialTheme.typography.bodyMedium,
                        color = scoreColor, fontWeight = FontWeight.SemiBold)
                }
                Surface(
                    shape = CircleShape,
                    color = scoreColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(2.dp, scoreColor.copy(alpha = 0.5f)),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${score.score}", style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black, color = scoreColor)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val bars = listOf(
                Triple("Trend",     score.trendScore,         35.0),
                Triple("Momentum",  score.momentumScore,      25.0),
                Triple("Volume",    score.volumeScore,        15.0),
                Triple("Volatility",score.volatilityScore,    15.0),
                Triple("Strength",  score.trendStrengthScore, 10.0)
            )
            bars.forEach { (label, value, max) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(72.dp))
                    Box(
                        Modifier.weight(1f).height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        val fraction = (value / max).coerceIn(0.0, 1.0).toFloat()
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(fraction)
                                .clip(RoundedCornerShape(3.dp))
                                .background(scoreColor)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("${value.toInt()}/${max.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ==================== AI INSIGHT CARD ====================

@Composable
fun AiInsightCard(insight: String, modifier: Modifier = Modifier) {
    if (insight.isBlank()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("AI Insight", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            insight.split("\n").forEach { line ->
                if (line.isNotBlank()) {
                    Text("· $line", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
    }
}

// ==================== QUICK INSIGHT PANEL ====================

@Composable
fun QuickInsightPanel(insight: QuickInsightData, modifier: Modifier = Modifier) {
    if (insight.trend.isBlank()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = HoldSignalColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Quick Insight",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(8.dp))
            InsightRow(insight.trend)
            InsightRow(insight.momentum)
            InsightRow(insight.volume)
            insight.alert?.let { InsightRow(it) }
        }
    }
}

@Composable
private fun InsightRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

// ==================== INDICATOR TOGGLE PANEL ====================

@Composable
fun IndicatorTogglesPanel(
    visibility: IndicatorVisibility,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    showAdvanced: Boolean = false
) {
    val groups = listOf(
        "Trend"      to listOf("EMA20", "EMA50", "EMA200", "SMA20", "VWAP"),
        "Momentum"   to listOf("RSI", "MACD", "Stoch"),
        "Volatility" to listOf("BB", "ATR"),
        "Volume"     to listOf("Volume", "OBV", "ADX")
    )
    val overlayGroup = "Overlays" to listOf("Confluence", "SigProgress")

    val isActive = { key: String ->
        when (key) {
            "EMA20"       -> visibility.showEMA20
            "EMA50"       -> visibility.showEMA50
            "EMA200"      -> visibility.showEMA200
            "SMA20"       -> visibility.showSMA20
            "MACD"        -> visibility.showMACD
            "RSI"         -> visibility.showRSI
            "BB"          -> visibility.showBollingerBands
            "VWAP"        -> visibility.showVWAP
            "ATR"         -> visibility.showATR
            "ADX"         -> visibility.showADX
            "Stoch"       -> visibility.showStochastic
            "OBV"         -> visibility.showOBV
            "Volume"      -> visibility.showVolume
            "Confluence"  -> visibility.showConfluenceZones
            "SigProgress" -> visibility.showSignalProgress
            else          -> false
        }
    }

    val displayLabel = { key: String ->
        when (key) {
            "SigProgress" -> "Trade Lines"
            "Confluence"  -> "Confluence"
            else          -> key
        }
    }

    Column(modifier = modifier) {
        // Basic always-visible group
        val basicGroup = "Basic" to listOf("EMA20", "EMA50", "Volume")
        Text(
            basicGroup.first,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            items(basicGroup.second) { key ->
                FilterChip(
                    selected = isActive(key),
                    onClick = { onToggle(key) },
                    label = { Text(displayLabel(key), style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Overlay group (Confluence, Signal Progress)
        Text(
            overlayGroup.first,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            items(overlayGroup.second) { key ->
                FilterChip(
                    selected = isActive(key),
                    onClick = { onToggle(key) },
                    label = { Text(displayLabel(key), style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Advanced indicators (shown when showAdvanced)
        if (showAdvanced) {
            groups.forEach { (groupName, keys) ->
                Text(
                    groupName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 4.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    items(keys) { key ->
                        FilterChip(
                            selected = isActive(key),
                            onClick = { onToggle(key) },
                            label = { Text(key, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}
