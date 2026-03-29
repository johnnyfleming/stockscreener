package com.tradescreenerai.app.ui.components.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradescreenerai.app.data.model.PredictionData
import com.tradescreenerai.app.data.model.PredictionDirection
import com.tradescreenerai.app.ui.theme.*

@Composable
fun PredictionCard(
    prediction: PredictionData,
    currentPrice: Double,
    modifier: Modifier = Modifier
) {
    val dirColor = when (prediction.direction) {
        PredictionDirection.BULLISH -> GainGreen
        PredictionDirection.BEARISH -> LossRed
        PredictionDirection.NEUTRAL -> Color(0xFFFFEB3B)
    }
    val dirIcon = when (prediction.direction) {
        PredictionDirection.BULLISH -> Icons.Filled.TrendingUp
        PredictionDirection.BEARISH -> Icons.Filled.TrendingDown
        PredictionDirection.NEUTRAL -> Icons.Filled.TrendingFlat
    }
    val dirLabel = when (prediction.direction) {
        PredictionDirection.BULLISH -> "BULLISH"
        PredictionDirection.BEARISH -> "BEARISH"
        PredictionDirection.NEUTRAL -> "NEUTRAL"
    }

    val animatedConfidence by animateFloatAsState(
        targetValue = (prediction.confidence / 100f).toFloat(),
        animationSpec = tween(1000),
        label = "confidence_anim"
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
                    Text("🤖", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "AI Prediction",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = dirColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(dirIcon, null, tint = dirColor, modifier = Modifier.size(16.dp))
                        Text(
                            dirLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = dirColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Confidence bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Confidence", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(modifier = Modifier.weight(1f).height(8.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Background
                        drawLine(
                            Color.Gray.copy(alpha = 0.2f),
                            start = Offset(0f, size.height / 2),
                            end = Offset(size.width, size.height / 2),
                            strokeWidth = size.height,
                            cap = StrokeCap.Round
                        )
                        // Filled
                        drawLine(
                            dirColor,
                            start = Offset(0f, size.height / 2),
                            end = Offset(size.width * animatedConfidence, size.height / 2),
                            strokeWidth = size.height,
                            cap = StrokeCap.Round
                        )
                    }
                }
                Text(
                    "${prediction.confidence.toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = dirColor
                )
            }

            Spacer(Modifier.height(12.dp))

            // Price targets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PriceTargetChip("24h", prediction.target24h, currentPrice, dirColor)
                PriceTargetChip("7d", prediction.target7d, currentPrice, dirColor)
                PriceTargetChip("30d", prediction.target30d, currentPrice, dirColor)
            }

            Spacer(Modifier.height(10.dp))

            // Risk level
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val riskColor = when (prediction.riskLevel) {
                    "High" -> LossRed
                    "Medium" -> Color(0xFFFF9800)
                    else -> GainGreen
                }
                Icon(Icons.Filled.Shield, null, tint = riskColor, modifier = Modifier.size(14.dp))
                Text("Risk: ${prediction.riskLevel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = riskColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Reasons
            if (prediction.reasons.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                prediction.reasons.forEach { reason ->
                    Text(
                        "• $reason",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceTargetChip(label: String, target: Double, current: Double, color: Color) {
    val pct = if (current > 0) (target - current) / current * 100 else 0.0
    val prefix = if (pct >= 0) "+" else ""
    val fmt = if (target >= 1) "$${String.format("%,.2f", target)}"
              else "$${String.format("%,.6f", target)}"

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(fmt, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(
            "$prefix${String.format("%.1f", pct)}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

