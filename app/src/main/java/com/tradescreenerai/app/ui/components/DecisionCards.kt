package com.tradescreenerai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.ui.theme.GainGreen
import com.tradescreenerai.app.ui.theme.LossRed

// ─────────────────────────────────────────────────────────────────────────────
//  1. Confluence Score Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ConfluenceScoreCard(
    result: ConfluenceResult,
    modifier: Modifier = Modifier
) {
    val scoreColor = when {
        result.score >= 70 -> GainGreen
        result.score >= 50 -> Color(0xFFFFC107)
        result.score >= 30 -> Color(0xFFFF9800)
        else -> LossRed
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Confluence Score", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(20.dp), color = scoreColor.copy(alpha = 0.15f)) {
                    Text(
                        "${result.score}/100 · ${result.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = scoreColor,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Score bar
            Box(
                modifier = Modifier.fillMaxWidth().height(8.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(result.score / 100f)
                        .fillMaxHeight()
                        .background(scoreColor, RoundedCornerShape(4.dp))
                )
            }

            Spacer(Modifier.height(10.dp))

            // Indicator breakdown
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(
                    "EMA" to result.emaScore,
                    "MACD" to result.macdScore,
                    "RSI" to result.rsiScore,
                    "Vol" to result.volumeScore,
                    "ATR" to result.atrScore,
                    "ADX" to result.adxScore
                ).forEach { (name, score) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val c = if (score > 0) GainGreen else if (score < 0) LossRed else Color.Gray
                        Text("$score", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  2. "Why This Trade" Card (reasons)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun WhyThisTradeCard(
    reasons: List<String>,
    strategy: StrategyType?,
    modifier: Modifier = Modifier
) {
    if (reasons.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("💡 Why This Trade", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (strategy != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "${strategy.emoji} ${strategy.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            reasons.forEach { reason ->
                Row(modifier = Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("•", color = GainGreen, fontWeight = FontWeight.Bold)
                    Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  3. Risk Overlay Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun RiskOverlayCard(
    risk: RiskOverlay,
    modifier: Modifier = Modifier
) {
    if (risk.entryPrice <= 0) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🛡️ Risk Management", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RiskPill("Entry", formatPriceCompact(risk.entryPrice), MaterialTheme.colorScheme.onSurface)
                RiskPill("Stop Loss", formatPriceCompact(risk.stopLoss), LossRed)
                RiskPill("Take Profit", formatPriceCompact(risk.takeProfit), GainGreen)
            }

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("R:R Ratio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${String.format("%.1f", risk.riskRewardRatio)}:1",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
                        color = if (risk.riskRewardRatio >= 2) GainGreen else Color(0xFFFFC107))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Risk %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${String.format("%.1f", risk.riskPct)}%",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
                        color = if (risk.riskPct < 3) GainGreen else LossRed)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ATR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatPriceCompact(risk.atrValue),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RiskPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  4. Multi-Timeframe Row
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MultiTimeframeRow(
    trends: List<TimeframeTrend>,
    modifier: Modifier = Modifier
) {
    if (trends.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📊 Multi-Timeframe", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                trends.forEach { tf ->
                    val (color, emoji) = when (tf.direction) {
                        SignalType.BUY  -> GainGreen to "🟢"
                        SignalType.SELL -> LossRed to "🔴"
                        else -> Color.Gray to "⚪"
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tf.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(emoji, style = MaterialTheme.typography.titleMedium)
                        Text(tf.description, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  5. Backtest Results Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun BacktestCard(
    result: BacktestResult,
    modifier: Modifier = Modifier
) {
    if (result.totalTrades == 0) return

    val wrColor = if (result.winRate >= 50) GainGreen else LossRed

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📈 Signal Backtest", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(20.dp), color = wrColor.copy(alpha = 0.15f)) {
                    Text("${String.format("%.0f", result.winRate)}% win rate",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold, color = wrColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // Win rate bar
            Box(
                modifier = Modifier.fillMaxWidth().height(8.dp)
                    .background(LossRed.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((result.winRate / 100f).toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(GainGreen.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatColumn("Trades", "${result.totalTrades}")
                StatColumn("Avg Win", "+${String.format("%.1f", result.avgGainPct)}%", GainGreen)
                StatColumn("Avg Loss", "${String.format("%.1f", result.avgLossPct)}%", LossRed)
                StatColumn("Best", "+${String.format("%.1f", result.bestTradePct)}%", GainGreen)
            }

            // Per-strategy breakdown
            if (result.perStrategy.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(Modifier.height(8.dp))
                Text("By Strategy", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                result.perStrategy.forEach { (strat, pair) ->
                    val (count, wr) = pair
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${strat.emoji} ${strat.displayName}", style = MaterialTheme.typography.labelSmall)
                        Text("$count trades · ${String.format("%.0f", wr)}% win",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (wr >= 50) GainGreen else LossRed,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  6. Strategy Filter Chips
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyFilterChips(
    selected: StrategyType?,
    onSelect: (StrategyType?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StrategyType.entries.forEach { strat ->
            FilterChip(
                selected = strat == selected,
                onClick = { onSelect(if (strat == selected) null else strat) },
                label = { Text("${strat.emoji} ${strat.displayName}", style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
private fun formatPriceCompact(price: Double): String = when {
    price >= 1000 -> "\$${String.format("%,.0f", price)}"
    price >= 1    -> "\$${String.format("%.2f", price)}"
    price >= 0.01 -> "\$${String.format("%.4f", price)}"
    else          -> "\$${String.format("%.6f", price)}"
}

