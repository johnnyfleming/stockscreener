package com.tradescreenerai.app.ui.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradescreenerai.app.data.model.Commodity
import com.tradescreenerai.app.ui.theme.*

@Composable
fun CommodityCard(
    commodity: Commodity,
    showBeginnerInfo: Boolean = false,
    onClick: () -> Unit
) {
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
                    Text(commodity.unit, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Price
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$${String.format("%.2f", commodity.price)}",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${if (commodity.changePct >= 0) "+" else ""}${String.format("%.2f", commodity.changePct)}%",
                        color = if (commodity.changePct >= 0) GainGreen else LossRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Performance bars
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PerformancePill("Day", commodity.changePct)
                PerformancePill("Week", commodity.weeklyChange)
                PerformancePill("Month", commodity.monthlyChange)
            }

            // Beginner info
            if (showBeginnerInfo) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GoldAccent.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "📚 ${commodity.type.description}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PerformancePill(label: String, changePct: Double) {
    val color = if (changePct >= 0) GainGreen else LossRed
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.10f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${if (changePct >= 0) "+" else ""}${String.format("%.1f", changePct)}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

