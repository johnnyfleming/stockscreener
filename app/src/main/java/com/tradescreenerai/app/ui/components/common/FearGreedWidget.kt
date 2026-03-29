package com.tradescreenerai.app.ui.components.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradescreenerai.app.data.model.FearGreedIndex
import com.tradescreenerai.app.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fear & Greed Index semicircular gauge widget.
 * Shows a gradient arc from red (Extreme Fear) → green (Extreme Greed)
 * with a needle pointer and numeric label.
 */
@Composable
fun FearGreedWidget(
    data: FearGreedIndex,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateFloatAsState(
        targetValue = data.value.toFloat(),
        animationSpec = tween(1200),
        label = "fear_greed_anim"
    )

    val gaugeColor = when {
        data.value < 20 -> LossRed
        data.value < 40 -> Color(0xFFFF9800)
        data.value < 60 -> Color(0xFFFFEB3B)
        data.value < 80 -> Color(0xFF8BC34A)
        else -> GainGreen
    }

    val changeFromYesterday = data.value - data.previousClose
    val changeFromWeek = data.value - data.weekAgo

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Fear & Greed Index",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            // Gauge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasW = size.width
                    val canvasH = size.height
                    val radius = minOf(canvasW / 2, canvasH) - 20f
                    val centerX = canvasW / 2
                    val centerY = canvasH - 10f

                    // Background arc
                    val arcRect = androidx.compose.ui.geometry.Rect(
                        left = centerX - radius,
                        top = centerY - radius,
                        right = centerX + radius,
                        bottom = centerY + radius
                    )

                    // Gradient arc segments
                    val segments = listOf(
                        Color(0xFFFF1744) to 36f,  // Extreme Fear
                        Color(0xFFFF9100) to 36f,  // Fear
                        Color(0xFFFFEA00) to 36f,  // Neutral
                        Color(0xFF76FF03) to 36f,  // Greed
                        Color(0xFF00E676) to 36f   // Extreme Greed
                    )
                    var startAngle = 180f
                    for ((color, sweep) in segments) {
                        drawArc(
                            color = color.copy(alpha = 0.3f),
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(arcRect.left, arcRect.top),
                            size = Size(arcRect.width, arcRect.height),
                            style = Stroke(width = 16f, cap = StrokeCap.Round)
                        )
                        startAngle += sweep
                    }

                    // Needle
                    val needleAngle = 180f + (animatedValue / 100f * 180f)
                    val needleRad = Math.toRadians(needleAngle.toDouble())
                    val needleLen = radius - 30f
                    val nx = centerX + (needleLen * cos(needleRad)).toFloat()
                    val ny = centerY + (needleLen * sin(needleRad)).toFloat()

                    drawLine(
                        color = gaugeColor,
                        start = Offset(centerX, centerY),
                        end = Offset(nx, ny),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                    // Center dot
                    drawCircle(gaugeColor, radius = 6f, center = Offset(centerX, centerY))
                    drawCircle(Color.White, radius = 3f, center = Offset(centerX, centerY))
                }
            }

            // Value & label
            Text(
                "${data.value}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = gaugeColor
            )
            Text(
                data.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = gaugeColor
            )

            Spacer(Modifier.height(8.dp))

            // Change indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ChangeChip("24h", changeFromYesterday)
                ChangeChip("7d", changeFromWeek)
            }
        }
    }
}

@Composable
private fun ChangeChip(label: String, change: Int) {
    val color = when {
        change > 0 -> GainGreen
        change < 0 -> LossRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val prefix = if (change > 0) "+" else ""

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "$prefix$change",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

