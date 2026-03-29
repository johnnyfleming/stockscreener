package com.tradescreenerai.app.ui.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradescreenerai.app.data.model.CommodityRanking
import com.tradescreenerai.app.data.model.SignalType
import com.tradescreenerai.app.ui.theme.*

@Composable
fun CommodityTradingCard(
    ranking: CommodityRanking,
    onClick: () -> Unit,
    onDayTrader: (() -> Unit)? = null
) {
    val commodity = ranking.commodity
    val signalColor = when (ranking.signal) {
        SignalType.BUY -> GainGreen
        SignalType.SELL -> LossRed
        SignalType.HOLD -> HoldSignalColor
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Emoji + Name
                Text(commodity.emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(commodity.name, fontWeight = FontWeight.Bold)
                    Text(
                        commodity.unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Signal badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = signalColor.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (ranking.signal == SignalType.SELL) Icons.AutoMirrored.Filled.TrendingDown
                            else Icons.AutoMirrored.Filled.TrendingUp,
                            null,
                            tint = signalColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            ranking.signal.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            color = signalColor
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Score
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        ranking.score >= 70 -> GainGreen.copy(alpha = 0.12f)
                        ranking.score >= 45 -> HoldSignalColor.copy(alpha = 0.12f)
                        else -> LossRed.copy(alpha = 0.12f)
                    }
                ) {
                    Text(
                        "${ranking.score}",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = when {
                            ranking.score >= 70 -> GainGreen
                            ranking.score >= 45 -> HoldSignalColor
                            else -> LossRed
                        }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Price row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "$${String.format("%.2f", commodity.price)}",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${if (commodity.changePct >= 0) "+" else ""}${String.format("%.2f", commodity.changePct)}% today",
                    color = if (commodity.changePct >= 0) GainGreen else LossRed,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Reasons
            if (ranking.reasons.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                ranking.reasons.take(2).forEach { reason ->
                    Text(
                        "• $reason",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Day Trader button
            if (onDayTrader != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDayTrader,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ElectricBlue
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "⚡ Day Trader",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ElectricBlue
                    )
                }
            }
        }
    }
}

