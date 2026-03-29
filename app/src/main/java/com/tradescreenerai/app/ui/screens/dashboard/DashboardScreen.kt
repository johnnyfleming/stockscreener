package com.tradescreenerai.app.ui.screens.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.ui.components.cards.CryptoCard
import com.tradescreenerai.app.ui.components.cards.NewsCard
import com.tradescreenerai.app.ui.components.cards.StockCard
import com.tradescreenerai.app.ui.components.cards.WhaleAlertSection
import com.tradescreenerai.app.ui.components.common.*
import com.tradescreenerai.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToCryptoDetail: (String) -> Unit,
    onNavigateToStockDetail: (String) -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToHeatmap: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // ===== HEADER =====
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Markets",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        java.text.SimpleDateFormat("EEEE, MMM dd", java.util.Locale.US)
                            .format(java.util.Date()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onNavigateToAlerts) {
                        Icon(Icons.Outlined.Notifications, "Alerts")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, "Settings")
                    }
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Outlined.Search, "Search")
                    }
                }
            }
        }

        // ===== MARKET OVERVIEW PILLS =====
        if (state.cryptos.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.cryptos.take(5)) { crypto ->
                        MarketPill(crypto = crypto, onClick = { onNavigateToCryptoDetail(crypto.id) })
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ===== FEAR & GREED INDEX =====
        if (state.fearGreed != null) {
            item {
                FearGreedWidget(
                    data = state.fearGreed!!,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        // ===== HEATMAP BUTTON =====
        item {
            Card(
                onClick = onNavigateToHeatmap,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.GridView, null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Market Heatmap", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Visual overview of market performance", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // ===== WHALE ALERTS =====
        if (state.whaleAlerts.isNotEmpty()) {
            item {
                WhaleAlertSection(
                    whales = state.whaleAlerts,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // ===== TOP CRYPTOS =====
        item { SectionHeader("Top Cryptocurrencies", action = "See All") }

        if (state.isLoading && state.cryptos.isEmpty()) {
            items(3) {
                ShimmerCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        } else {
            items(state.cryptos.take(8)) { crypto ->
                CryptoCard(
                    crypto = crypto,
                    onClick = { onNavigateToCryptoDetail(crypto.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // ===== TRENDING CRYPTO =====
        if (state.trendingCryptos.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("🔥 Trending")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.trendingCryptos) { crypto ->
                        TrendingChip(crypto = crypto, onClick = { onNavigateToCryptoDetail(crypto.id) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ===== TOP GAINERS =====
        if (state.topGainers.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("📈 Top Gainers")
            }
            items(state.topGainers.take(5)) { stock ->
                StockCard(
                    stock = stock,
                    onClick = { onNavigateToStockDetail(stock.symbol) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // ===== TOP LOSERS =====
        if (state.topLosers.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("📉 Top Losers")
            }
            items(state.topLosers.take(5)) { stock ->
                StockCard(
                    stock = stock,
                    onClick = { onNavigateToStockDetail(stock.symbol) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // ===== LATEST NEWS =====
        if (state.latestNews.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Latest News", action = "More")
            }
            items(state.latestNews) { article ->
                NewsCard(
                    article = article,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.url))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // Error message
        if (state.error != null && state.cryptos.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.WifiOff,
                    title = "Unable to load data",
                    subtitle = state.error ?: "Check your internet connection",
                    actionLabel = "Retry",
                    onAction = { viewModel.refresh() },
                    modifier = Modifier.padding(top = 48.dp)
                )
            }
        }
    }
}

@Composable
private fun MarketPill(crypto: Crypto, onClick: () -> Unit) {
    val isPositive = crypto.changePercent24h >= 0
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPositive)
                GainGreenBg else LossRedBg
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (crypto.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(crypto.imageUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    crypto.symbol,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                formatPrice(crypto.price),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            PriceChangeChip(crypto.changePercent24h)
        }
    }
}

@Composable
private fun TrendingChip(crypto: Crypto, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (crypto.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(crypto.imageUrl).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(8.dp))
            }
            Column {
                Text(crypto.symbol, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(crypto.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

