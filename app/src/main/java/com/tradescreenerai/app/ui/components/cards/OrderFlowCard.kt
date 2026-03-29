package com.tradescreenerai.app.ui.components.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradescreenerai.app.data.model.OrderFlowData
import com.tradescreenerai.app.ui.theme.*

/**
 * Order Flow Imbalance Card — shows buy vs sell pressure
 * like institutional traders see on Level 2/DOM screens.
 * Only ~1% of retail trading apps expose this kind of analysis.
 */
@Composable
fun OrderFlowCard(
    orderFlow: OrderFlowData,
    modifier: Modifier = Modifier
) {
    val animBuy by animateFloatAsState(
        targetValue = (orderFlow.buyPressure / 100f).toFloat(),
        animationSpec = tween(800),
        label = "buy_anim"
    )
    val animSell by animateFloatAsState(
        targetValue = (orderFlow.sellPressure / 100f).toFloat(),
        animationSpec = tween(800),
        label = "sell_anim"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.ShowChart, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp))
                    Text(
                        "Order Flow Imbalance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFE040FB).copy(alpha = 0.15f)
                ) {
                    Text(
                        "PRO",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE040FB),
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Imbalance label
            Text(
                orderFlow.imbalanceLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(12.dp))

            // Buy vs Sell pressure bars
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Buy pressure bar (grows from left)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "Buy ${String.format("%.1f", orderFlow.buyPressure)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = GainGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                        // Background
                        drawRoundRect(
                            color = GainGreen.copy(alpha = 0.1f),
                            cornerRadius = CornerRadius(8f),
                            size = Size(size.width, size.height)
                        )
                        // Filled from right
                        val barW = size.width * animBuy
                        drawRoundRect(
                            color = GainGreen.copy(alpha = 0.6f),
                            topLeft = Offset(size.width - barW, 0f),
                            cornerRadius = CornerRadius(8f),
                            size = Size(barW, size.height)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    "VS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )

                Spacer(Modifier.width(8.dp))

                // Sell pressure bar (grows from right)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        "Sell ${String.format("%.1f", orderFlow.sellPressure)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = LossRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                        drawRoundRect(
                            color = LossRed.copy(alpha = 0.1f),
                            cornerRadius = CornerRadius(8f),
                            size = Size(size.width, size.height)
                        )
                        val barW = size.width * animSell
                        drawRoundRect(
                            color = LossRed.copy(alpha = 0.6f),
                            cornerRadius = CornerRadius(8f),
                            size = Size(barW, size.height)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Additional metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricChip("Volume Δ", formatDelta(orderFlow.volumeDelta), orderFlow.volumeDelta >= 0)
                MetricChip("Large Orders", "${String.format("%.1f", orderFlow.largeOrderRatio)}%", orderFlow.largeOrderRatio > 15)
                MetricChip(
                    "Net Flow",
                    if (orderFlow.netFlow >= 0) "+${String.format("%.0f", orderFlow.netFlow)}"
                    else String.format("%.0f", orderFlow.netFlow),
                    orderFlow.netFlow >= 0
                )
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, isPositive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isPositive) GainGreen else LossRed
        )
    }
}

private fun formatDelta(delta: Long): String = when {
    delta >= 1_000_000 -> "+${String.format("%.1f", delta / 1_000_000.0)}M"
    delta >= 1_000 -> "+${String.format("%.0f", delta / 1_000.0)}K"
    delta <= -1_000_000 -> "${String.format("%.1f", delta / 1_000_000.0)}M"
    delta <= -1_000 -> "${String.format("%.0f", delta / 1_000.0)}K"
    else -> "$delta"
}

