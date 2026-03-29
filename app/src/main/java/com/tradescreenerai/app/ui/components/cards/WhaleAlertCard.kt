package com.tradescreenerai.app.ui.components.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tradescreenerai.app.data.model.WhaleTransaction
import com.tradescreenerai.app.data.model.WhaleType
import com.tradescreenerai.app.ui.theme.*

@Composable
fun WhaleAlertSection(
    whales: List<WhaleTransaction>,
    modifier: Modifier = Modifier
) {
    if (whales.isEmpty()) return

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("🐋", style = MaterialTheme.typography.titleMedium)
            Text(
                "Whale Activity",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFFFF9800).copy(alpha = 0.15f)
            ) {
                Text(
                    "LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(whales) { whale ->
                WhaleAlertCard(whale = whale)
            }
        }
    }
}

@Composable
private fun WhaleAlertCard(whale: WhaleTransaction) {
    val context = LocalContext.current
    val isBullish = whale.type == WhaleType.LARGE_BUY || whale.type == WhaleType.EXCHANGE_OUTFLOW
    val typeColor = if (isBullish) GainGreen else LossRed
    val typeLabel = when (whale.type) {
        WhaleType.LARGE_BUY -> "Large Buy"
        WhaleType.LARGE_SELL -> "Large Sell"
        WhaleType.EXCHANGE_INFLOW -> "Exchange In"
        WhaleType.EXCHANGE_OUTFLOW -> "Exchange Out"
    }
    val typeIcon = if (isBullish) Icons.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.width(180.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (whale.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(whale.imageUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(
                    whale.symbol,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = typeColor.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(12.dp))
                    Text(
                        typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = typeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                formatWhaleVolume(whale.amountUsd),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatWhaleVolume(vol: Double): String = when {
    vol >= 1_000_000_000 -> "$${String.format("%.1f", vol / 1_000_000_000)}B"
    vol >= 1_000_000 -> "$${String.format("%.1f", vol / 1_000_000)}M"
    vol >= 1_000 -> "$${String.format("%.0f", vol / 1_000)}K"
    else -> "$${String.format("%.0f", vol)}"
}

