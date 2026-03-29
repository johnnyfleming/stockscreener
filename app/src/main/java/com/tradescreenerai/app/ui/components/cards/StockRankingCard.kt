package com.tradescreenerai.app.ui.components.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradescreenerai.app.data.model.RankedStock
import com.tradescreenerai.app.data.model.StockHorizon
import com.tradescreenerai.app.ui.theme.*

@Composable
fun StockRankingCard(
    rank: Int,
    rankedStock: RankedStock,
    onClick: () -> Unit,
    onDayTrader: (() -> Unit)? = null,
    onNews: (() -> Unit)? = null
) {
    val stock = rankedStock.stock
    var showAi by remember { mutableStateOf(false) }

    val scoreColor = when {
        rankedStock.score >= 75 -> GainGreen
        rankedStock.score >= 55 -> HoldSignalColor
        rankedStock.score >= 35 -> ElectricBlue
        else -> LossRed
    }
    val priceColor = if (stock.changePercent >= 0) GainGreen else LossRed
    val sparkColor = if (rankedStock.sparkline.size >= 2 &&
        rankedStock.sparkline.last() >= rankedStock.sparkline.first()) GainGreen else LossRed

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // ── Row 1: rank · symbol · sparkline · price · score ─────────────
            Row(verticalAlignment = Alignment.CenterVertically) {

                // Rank badge
                Surface(
                    shape = CircleShape,
                    color = if (rank <= 3) GoldAccent.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "#$rank",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (rank <= 3) GoldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Symbol & name
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stock.symbol, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (rankedStock.horizon == StockHorizon.SHORT_TERM)
                                BuySignalColor.copy(alpha = 0.12f)
                            else ElectricBlue.copy(alpha = 0.12f)
                        ) {
                            Text(
                                rankedStock.horizon.emoji,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                color = if (rankedStock.horizon == StockHorizon.SHORT_TERM)
                                    BuySignalColor else ElectricBlue
                            )
                        }
                    }
                    if (stock.name.isNotBlank() && stock.name != stock.symbol) {
                        Text(
                            stock.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Mini sparkline (30-day)
                if (rankedStock.sparkline.size >= 5) {
                    MiniSparkline(
                        prices = rankedStock.sparkline,
                        color  = sparkColor,
                        modifier = Modifier.width(52.dp).height(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }

                // Price & change
                Column(horizontalAlignment = Alignment.End) {
                    val priceText = if (stock.price > 0) "$${String.format("%.2f", stock.price)}" else "—"
                    Text(priceText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    val changePct = stock.changePercent
                    Text(
                        "${if (changePct >= 0) "+" else ""}${String.format("%.2f", changePct)}%",
                        color = priceColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Score
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = scoreColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        "${rankedStock.score}",
                        fontWeight = FontWeight.Bold,
                        color = scoreColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // ── Confluence progress bar ───────────────────────────────────────
            if (rankedStock.confluenceScore > 0) {
                Spacer(Modifier.height(6.dp))
                val confColor = when {
                    rankedStock.confluenceScore >= 65 -> GainGreen
                    rankedStock.confluenceScore >= 45 -> HoldSignalColor
                    else -> LossRed
                }
                val confLabel = when {
                    rankedStock.confluenceScore >= 70 -> "Strong Bullish"
                    rankedStock.confluenceScore >= 55 -> "Bullish"
                    rankedStock.confluenceScore >= 45 -> "Neutral"
                    rankedStock.confluenceScore >= 30 -> "Bearish"
                    else -> "Strong Bearish"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Confluence", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(70.dp))
                    LinearProgressIndicator(
                        progress = { rankedStock.confluenceScore / 100f },
                        modifier = Modifier.weight(1f).height(4.dp).padding(horizontal = 4.dp),
                        color = confColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        confLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = confColor,
                        modifier = Modifier.width(80.dp),
                        maxLines = 1
                    )
                }
            }

            // ── Reasons chips ─────────────────────────────────────────────────
            if (rankedStock.reasons.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rankedStock.reasons.take(2).forEach { reason ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                reason,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ── Action bar: AI expand · Day Trader · News ─────────────────────
            if (rankedStock.aiInsight.isNotBlank() || onDayTrader != null || onNews != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // AI toggle button
                    if (rankedStock.aiInsight.isNotBlank()) {
                        TextButton(
                            onClick = { showAi = !showAi },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Filled.SmartToy, contentDescription = null,
                                modifier = Modifier.size(14.dp), tint = ElectricBlue)
                            Spacer(Modifier.width(3.dp))
                            Text("AI Analysis", style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
                            Icon(
                                if (showAi) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null, modifier = Modifier.size(14.dp), tint = ElectricBlue
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // News button
                    if (onNews != null) {
                        IconButton(onClick = onNews, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Newspaper, contentDescription = "News",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp))
                        }
                    }

                    // Day Trader button
                    if (onDayTrader != null) {
                        IconButton(onClick = onDayTrader, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Bolt, contentDescription = "Day Trade",
                                tint = HoldSignalColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ── AI Analysis panel (expandable) ────────────────────────────────
            AnimatedVisibility(visible = showAi && rankedStock.aiInsight.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = ElectricBlue.copy(alpha = 0.06f)
                ) {
                    Row(modifier = Modifier.padding(10.dp)) {
                        Icon(Icons.Filled.SmartToy, contentDescription = null,
                            tint = ElectricBlue, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            rankedStock.aiInsight,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniSparkline(
    prices: List<Double>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (prices.size < 2) return
    val minP = prices.min()
    val maxP = prices.max()
    val range = (maxP - minP).coerceAtLeast(0.001)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stepX = w / (prices.size - 1).toFloat()

        val path = Path()
        prices.forEachIndexed { i, price ->
            val x = i * stepX
            val y = h - ((price - minP) / range * h).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 1.8.dp.toPx()))

        // Dot at last price
        val lastX = (prices.size - 1) * stepX
        val lastY = h - ((prices.last() - minP) / range * h).toFloat()
        drawCircle(color = color, radius = 2.5.dp.toPx(), center = Offset(lastX, lastY))
    }
}
