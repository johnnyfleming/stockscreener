package com.tradescreenerai.app.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tradescreenerai.app.data.model.CommodityType
import com.tradescreenerai.app.ui.components.common.SectionHeader
import com.tradescreenerai.app.ui.theme.GainGreen
import com.tradescreenerai.app.ui.theme.LossRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToStockDetail: (String) -> Unit,
    onNavigateToCryptoDetail: (String) -> Unit,
    onNavigateToCommodityDetail: (String) -> Unit = {},
    viewModel: SearchViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.search(it) },
                placeholder = { Text("Search stocks, crypto, commodities…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Filled.Clear, "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (state.isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            // ── Stocks ────────────────────────────────────────────────────────
            if (state.stockResults.isNotEmpty()) {
                item { SectionHeader("📈 Stocks") }
                items(state.stockResults) { stock ->
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stock.symbol, fontWeight = FontWeight.Bold)
                                if (stock.price > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "$${String.format(java.util.Locale.US, "%.2f", stock.price)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        supportingContent = {
                            if (stock.name != stock.symbol && stock.name.isNotBlank()) {
                                Text(
                                    stock.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        leadingContent = {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        stock.symbol.take(2),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                if (stock.changePercent != 0.0) {
                                    Text(
                                        "${if (stock.changePercent >= 0) "▲" else "▼"} ${
                                            String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(stock.changePercent))
                                        }%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (stock.changePercent >= 0) GainGreen else LossRed,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (stock.exchange.isNotBlank()) {
                                    Text(stock.exchange, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        modifier = Modifier.clickable { onNavigateToStockDetail(stock.symbol) }
                    )
                }
            }

            // ── Cryptocurrencies ──────────────────────────────────────────────
            if (state.cryptoResults.isNotEmpty()) {
                item { SectionHeader("🪙 Cryptocurrencies") }
                items(state.cryptoResults) { crypto ->
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(crypto.name, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    crypto.symbol,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingContent = {
                            if (crypto.imageUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(crypto.imageUrl).crossfade(true).build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    modifier = Modifier.size(40.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(crypto.symbol.take(1), fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        },
                        trailingContent = {
                            if (crypto.rank > 0) {
                                Text("#${crypto.rank}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.clickable { onNavigateToCryptoDetail(crypto.id) }
                    )
                }
            }

            // ── Commodities ───────────────────────────────────────────────────
            if (state.commodityResults.isNotEmpty()) {
                item { SectionHeader("🏗️ Commodities") }
                items(state.commodityResults) { ct ->
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ct.emoji, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(8.dp))
                                Text(ct.displayName, fontWeight = FontWeight.Medium)
                            }
                        },
                        supportingContent = {
                            Text("${ct.category.displayName} · ${ct.unit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingContent = {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(ct.etfTicker,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.clickable { onNavigateToCommodityDetail(ct.name) }
                    )
                }
            }

            // ── Empty state ───────────────────────────────────────────────────
            if (state.query.length >= 2 && !state.isSearching &&
                state.stockResults.isEmpty() && state.cryptoResults.isEmpty() && state.commodityResults.isEmpty()
            ) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.SearchOff, contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(12.dp))
                            Text("No results found", style = MaterialTheme.typography.titleMedium)
                            Text("Try a different search term", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
