package com.tradescreenerai.app.ui.screens.news

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradescreenerai.app.data.model.NewsSentiment
import com.tradescreenerai.app.ui.components.cards.NewsCard
import com.tradescreenerai.app.ui.components.common.EmptyState
import com.tradescreenerai.app.ui.components.common.ShimmerCard
import com.tradescreenerai.app.ui.theme.GainGreen
import com.tradescreenerai.app.ui.theme.HoldSignalColor
import com.tradescreenerai.app.ui.theme.LossRed
import java.text.SimpleDateFormat
import java.util.*

/** Popular tickers shown as quick-filter chips in the News tab. */
private val QUICK_SYMBOLS = listOf("AAPL", "TSLA", "NVDA", "MSFT", "AMZN", "META", "GOOG", "SPY", "BTC", "ETH")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    viewModel: NewsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val categories = listOf("All", "Stocks", "Crypto", "Economy")

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "News",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                if (state.lastRefreshed > 0L) {
                    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                    Text(
                        "Updated ${fmt.format(Date(state.lastRefreshed))}  •  refreshes in ${state.nextRefreshIn}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { viewModel.loadNews() }) {
                if (state.isLoading)
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else
                    Icon(Icons.Filled.Refresh, "Refresh")
            }
        }

        // ── Search bar ────────────────────────────────────────────────────────
        var searchText by remember { mutableStateOf("") }
        OutlinedTextField(
            value = searchText,
            onValueChange = {
                searchText = it
                if (it.length > 2) viewModel.searchNews(it)
                else if (it.isEmpty()) { viewModel.clearSymbolFilter(); viewModel.loadNews() }
            },
            placeholder = { Text("Search news or ticker…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (searchText.isNotEmpty()) {{
                IconButton(onClick = { searchText = ""; viewModel.clearSymbolFilter(); viewModel.loadNews() }) {
                    Icon(Icons.Filled.Clear, "Clear")
                }
            }} else null,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.large
        )

        Spacer(Modifier.height(8.dp))

        // ── Quick symbol filter chips ─────────────────────────────────────────
        Text(
            "Filter by stock",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QUICK_SYMBOLS.forEach { sym ->
                FilterChip(
                    selected = state.selectedSymbol == sym,
                    onClick = {
                        if (state.selectedSymbol == sym) viewModel.clearSymbolFilter()
                        else { searchText = ""; viewModel.filterBySymbol(sym) }
                    },
                    label = { Text(sym, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // ── Category chips (shown only when no symbol is selected) ────────────
        if (state.selectedSymbol == null) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = state.activeCategory == cat,
                        onClick = { viewModel.setCategory(cat) },
                        label = { Text(cat) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── News list ─────────────────────────────────────────────────────────
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.isLoading) {
                items(5) { ShimmerCard() }
            } else if (state.articles.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Newspaper,
                        title = "No news found",
                        subtitle = "Try adjusting your search or selecting a different ticker",
                        actionLabel = "Refresh",
                        onAction = { viewModel.loadNews() }
                    )
                }
            } else {
                items(state.articles, key = { it.url }) { article ->
                    // Show sentiment dot alongside card
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Sentiment indicator dot on the left edge
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = when (article.sentiment) {
                                NewsSentiment.POSITIVE -> GainGreen
                                NewsSentiment.NEGATIVE -> LossRed
                                NewsSentiment.NEUTRAL  -> Color.Gray.copy(alpha = 0.4f)
                            },
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(Modifier.width(6.dp))
                        NewsCard(
                            article = article,
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url)))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
