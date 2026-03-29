package com.tradescreenerai.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradescreenerai.app.data.model.HeatmapCell
import com.tradescreenerai.app.data.repository.MarketDataRepository
import com.tradescreenerai.app.ui.theme.*
import com.tradescreenerai.app.util.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HeatmapState(
    val cells: List<HeatmapCell> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class HeatmapViewModel : ViewModel() {
    private val repo = MarketDataRepository()
    private val _state = MutableStateFlow(HeatmapState())
    val state = _state.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            when (val result = repo.getHeatmapData()) {
                is Resource.Success -> {
                    _state.value = _state.value.copy(
                        cells = result.data ?: emptyList(),
                        isLoading = false
                    )
                }
                is Resource.Error -> {
                    _state.value = _state.value.copy(
                        error = result.message,
                        isLoading = false
                    )
                }
                else -> {}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapScreen(
    onBack: () -> Unit,
    onNavigateToCryptoDetail: (String) -> Unit,
    viewModel: HeatmapViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Market Heatmap", fontWeight = FontWeight.Bold)
                        Text("Sized by market cap, colored by 24h change",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 80.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    items(state.cells, key = { it.id }) { cell ->
                        HeatmapTile(
                            cell = cell,
                            onClick = { onNavigateToCryptoDetail(cell.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapTile(cell: HeatmapCell, onClick: () -> Unit) {
    val color = when {
        cell.changePercent > 10 -> Color(0xFF00C853)
        cell.changePercent > 5 -> Color(0xFF00E676)
        cell.changePercent > 2 -> Color(0xFF69F0AE).copy(alpha = 0.7f)
        cell.changePercent > 0 -> Color(0xFF69F0AE).copy(alpha = 0.4f)
        cell.changePercent > -2 -> Color(0xFFFF8A80).copy(alpha = 0.4f)
        cell.changePercent > -5 -> Color(0xFFFF5252).copy(alpha = 0.7f)
        cell.changePercent > -10 -> Color(0xFFFF5252)
        else -> Color(0xFFD32F2F)
    }

    // Vary height slightly by market cap rank
    val h = when {
        cell.marketCap > 100_000_000_000 -> 80.dp
        cell.marketCap > 10_000_000_000 -> 70.dp
        else -> 60.dp
    }

    Box(
        modifier = Modifier
            .height(h)
            .fillMaxWidth()
            .background(color, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                cell.symbol,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${if (cell.changePercent >= 0) "+" else ""}${String.format("%.1f", cell.changePercent)}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }
    }
}

