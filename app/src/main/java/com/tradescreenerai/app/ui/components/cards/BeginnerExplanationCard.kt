package com.tradescreenerai.app.ui.components.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradescreenerai.app.data.model.BeginnerInsight
import com.tradescreenerai.app.ui.theme.*

/**
 * Reusable beginner-friendly explanation card.
 * Shows 4 key pieces of information in an easy-to-understand format.
 */
@Composable
fun BeginnerExplanationCard(
    insight: BeginnerInsight,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val riskColor = when (insight.riskLevel) {
        "High" -> LossRed
        "Medium" -> HoldSignalColor
        else -> GainGreen
    }
    val riskEmoji = when (insight.riskLevel) {
        "High" -> "🔴"
        "Medium" -> "🟡"
        else -> "🟢"
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
        // What is happening
        if (insight.whatIsHappening.isNotBlank()) {
            Row(verticalAlignment = Alignment.Top) {
                Text("📊", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        "What's happening",
                        fontWeight = FontWeight.SemiBold,
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
                    )
                    Text(
                        insight.whatIsHappening,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Why it matters
        if (insight.whyItMatters.isNotBlank()) {
            Row(verticalAlignment = Alignment.Top) {
                Text("💡", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        "Why it matters",
                        fontWeight = FontWeight.SemiBold,
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
                    )
                    Text(
                        insight.whyItMatters,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Time horizon
        if (insight.timeHorizon.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏱️", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Outlook: ",
                    fontWeight = FontWeight.SemiBold,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
                )
                Text(
                    insight.timeHorizon,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Key risk
        if (insight.keyRisk.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = riskColor.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("$riskEmoji", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            "Risk: ${insight.riskLevel}",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall,
                            color = riskColor
                        )
                        Text(
                            insight.keyRisk,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

