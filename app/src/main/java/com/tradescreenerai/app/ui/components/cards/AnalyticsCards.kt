package com.tradescreenerai.app.ui.components.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.ui.theme.*

/**
 * Risk Score badge — shows color-coded risk level for an asset.
 */
@Composable
fun RiskScoreBadge(
    risk: RiskScoreData,
    modifier: Modifier = Modifier
) {
    val color = when {
        risk.score >= 80 -> LossRed
        risk.score >= 60 -> Color(0xFFFF9800)
        risk.score >= 40 -> Color(0xFFFFEB3B)
        risk.score >= 20 -> Color(0xFF8BC34A)
        else -> GainGreen
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Shield, null, tint = color, modifier = Modifier.size(20.dp))
                Column {
                    Text("Risk Score", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${risk.score}/100 — ${risk.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = color)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniRiskChip("Vol", risk.volatilityScore, color)
                MiniRiskChip("DD", risk.drawdownScore, color)
                MiniRiskChip("Liq", risk.liquidityScore, color)
            }
        }
    }
}

@Composable
private fun MiniRiskChip(label: String, score: Double, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Text("${score.toInt()}", style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, color = accentColor)
    }
}

/**
 * Multi-Timeframe Confluence strip — shows alignment across daily/weekly/monthly signals.
 */
@Composable
fun MultiTimeframeStrip(
    signal: MultiTimeframeSignal,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Layers, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp))
                Text("Multi-Timeframe Analysis",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))

            // Signal strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TimeframeChip("Daily", signal.daily)
                TimeframeChip("Weekly", signal.weekly)
                TimeframeChip("Monthly", signal.monthly)
            }

            Spacer(Modifier.height(8.dp))

            // Overall label
            val overallColor = when (signal.overallSignal) {
                SignalType.BUY -> GainGreen
                SignalType.SELL -> LossRed
                else -> Color(0xFFFFEB3B)
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = overallColor.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        signal.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = overallColor
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Confluence: ${signal.confluenceScore}/3",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeframeChip(label: String, signalType: SignalType) {
    val color = when (signalType) {
        SignalType.BUY -> GainGreen
        SignalType.SELL -> LossRed
        SignalType.HOLD -> Color(0xFFFFEB3B)
    }
    val icon = when (signalType) {
        SignalType.BUY -> Icons.Filled.ArrowUpward
        SignalType.SELL -> Icons.Filled.ArrowDownward
        SignalType.HOLD -> Icons.Filled.HorizontalRule
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Text(
                signalType.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

/**
 * Smart Money Flow mini card — shows whether institutional money is flowing in or out.
 */
@Composable
fun SmartMoneyFlowCard(
    flowValue: Double,
    modifier: Modifier = Modifier
) {
    val isBullish = flowValue > 0
    val color = if (isBullish) GainGreen else LossRed
    val label = when {
        flowValue > 5 -> "Strong Institutional Buying"
        flowValue > 2 -> "Smart Money Accumulating"
        flowValue > 0 -> "Slight Buy-Side Flow"
        flowValue > -2 -> "Slight Sell-Side Flow"
        flowValue > -5 -> "Smart Money Distributing"
        else -> "Strong Institutional Selling"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (isBullish) Icons.Filled.AccountBalance else Icons.Filled.AccountBalance,
                null, tint = color, modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Smart Money Flow",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = color)
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = color.copy(alpha = 0.15f)
            ) {
                Text(
                    "${if (flowValue >= 0) "+" else ""}${String.format("%.1f", flowValue)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = color,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

