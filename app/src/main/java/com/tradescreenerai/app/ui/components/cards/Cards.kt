package com.tradescreenerai.app.ui.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.model.NewsSentiment
import com.tradescreenerai.app.ui.components.charts.MiniSparkline
import com.tradescreenerai.app.ui.components.common.PriceChangeChip
import com.tradescreenerai.app.ui.components.common.formatPrice
import com.tradescreenerai.app.ui.theme.*
import androidx.compose.foundation.layout.Arrangement

@Composable
fun StockCard(
    stock: Stock,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isWatchlisted: Boolean = false,
    onWatchlistToggle: (() -> Unit)? = null,
    showSignal: Boolean = false,
    isOffline: Boolean = false
) {
    val signalColor = when {
        stock.changePercent > 5.0  -> GainGreen
        stock.changePercent > 0.0  -> GainGreen.copy(alpha = 0.7f)
        stock.changePercent < -5.0 -> LossRed
        else                       -> LossRed.copy(alpha = 0.7f)
    }
    val signalLabel = when {
        stock.changePercent > 5.0  -> "⬆ BUY"
        stock.changePercent > 0.0  -> "↗ BUY"
        stock.changePercent < -5.0 -> "⬇ SELL"
        else                       -> "↘ SELL"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Symbol badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stock.symbol.take(2),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Name and symbol
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stock.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isOffline) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = androidx.compose.ui.graphics.Color(0xFF1A237E).copy(alpha = 0.25f)
                        ) {
                            Text(
                                "OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = androidx.compose.ui.graphics.Color(0xFF90CAF9),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = stock.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Price and change
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatPrice(stock.price),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isOffline) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                if (isOffline) {
                    Text(
                        "last close",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                } else {
                    PriceChangeChip(stock.changePercent)
                }
            }

            // BUY/SELL signal badge — only when live data available
            if (showSignal && !isOffline) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = signalColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        signalLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = signalColor,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Watchlist star
            if (onWatchlistToggle != null) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onWatchlistToggle,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (isWatchlisted) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Watchlist",
                        tint = if (isWatchlisted) GoldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CryptoCard(
    crypto: Crypto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isWatchlisted: Boolean = false,
    onWatchlistToggle: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Crypto icon
            if (crypto.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(crypto.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = crypto.name,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = crypto.symbol.take(2),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Name, symbol, rank
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = crypto.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (crypto.rank > 0) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "#${crypto.rank}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = crypto.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Sparkline
            if (crypto.sparkline.isNotEmpty()) {
                MiniSparkline(
                    data = crypto.sparkline.takeLast(24),
                    isPositive = crypto.changePercent24h >= 0,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Price and change
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatPrice(crypto.price),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                PriceChangeChip(crypto.changePercent24h)
            }

            // Watchlist
            if (onWatchlistToggle != null) {
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onWatchlistToggle,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (isWatchlisted) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Watchlist",
                        tint = if (isWatchlisted) GoldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NewsCard(
    article: NewsArticle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // Thumbnail
            if (!article.imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(article.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = article.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = article.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Sentiment chip
                        val (sentimentText, sentimentColor) = when (article.sentiment) {
                            NewsSentiment.POSITIVE -> Pair("● Positive", com.tradescreenerai.app.ui.theme.GainGreen)
                            NewsSentiment.NEGATIVE -> Pair("● Negative", com.tradescreenerai.app.ui.theme.LossRed)
                            NewsSentiment.NEUTRAL  -> Pair("● Neutral",  com.tradescreenerai.app.ui.theme.HoldSignalColor)
                        }
                        Text(
                            text = sentimentText,
                            style = MaterialTheme.typography.labelSmall,
                            color = sentimentColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = formatTimeAgo(article.publishedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PortfolioCard(
    item: PortfolioItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatPrice(item.totalValue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val plColor = if (item.profitLoss >= 0) GainGreen else LossRed
                    Text(
                        text = "${if (item.profitLoss >= 0) "+" else ""}${formatPrice(item.profitLoss)} (${String.format(java.util.Locale.US, "%.2f", item.profitLossPercent)}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = plColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Qty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${item.quantity}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Avg Cost", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatPrice(item.buyPrice), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Current", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatPrice(item.currentPrice), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun formatTimeAgo(dateStr: String): String {
    return try {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd"
        )
        var date: java.util.Date? = null
        for (fmt in formats) {
            try {
                date = java.text.SimpleDateFormat(fmt, java.util.Locale.US).parse(dateStr)
                if (date != null) break
            } catch (_: Exception) {}
        }
        if (date == null) return dateStr

        val diff = System.currentTimeMillis() - date.time
        val minutes = diff / 60_000
        val hours = minutes / 60
        val days = hours / 24
        when {
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> java.text.SimpleDateFormat("MMM dd", java.util.Locale.US).format(date)
        }
    } catch (_: Exception) { dateStr }
}

