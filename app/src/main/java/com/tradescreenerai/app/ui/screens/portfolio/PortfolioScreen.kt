package com.tradescreenerai.app.ui.screens.portfolio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradescreenerai.app.data.model.AssetType
import com.tradescreenerai.app.ui.components.cards.PortfolioCard
import com.tradescreenerai.app.ui.components.common.*
import com.tradescreenerai.app.ui.theme.*

@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Portfolio",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Filled.Add, "Add Holding")
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Portfolio summary card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Total Value",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            formatPrice(state.totalValue),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val plColor = if (state.totalPL >= 0) GainGreen else LossRed
                            Icon(
                                if (state.totalPL >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                tint = plColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${if (state.totalPL >= 0) "+" else ""}${formatPrice(state.totalPL)} (${String.format("%.2f", state.totalPLPercent)}%)",
                                style = MaterialTheme.typography.titleMedium,
                                color = plColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Mini allocation chart
                        if (state.items.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            AllocationChart(state.items.map { it.symbol to it.totalValue })
                        }
                    }
                }
            }

            // Holdings
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Holdings")
            }

            if (state.items.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.PieChart,
                        title = "No holdings yet",
                        subtitle = "Add your first holding to start tracking",
                        actionLabel = "Add Holding",
                        onAction = { viewModel.showAddDialog() }
                    )
                }
            } else {
                items(state.items, key = { it.symbol }) { item ->
                    PortfolioCard(
                        item = item,
                        onClick = { }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // Add holding dialog
    if (state.showAddDialog) {
        AddHoldingDialog(
            onDismiss = { viewModel.hideAddDialog() },
            onAdd = { symbol, name, type, qty, price ->
                viewModel.addHolding(symbol, name, type, qty, price)
            }
        )
    }
}

@Composable
private fun AllocationChart(allocations: List<Pair<String, Double>>) {
    val colors = listOf(ElectricBlue, AccentTeal, AccentPurple, GainGreen, HoldSignalColor, NeonBlue, LossRed)
    val total = allocations.sumOf { it.second }
    if (total <= 0) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(80.dp)) {
            var startAngle = -90f
            allocations.forEachIndexed { index, (_, value) ->
                val sweep = (value / total * 360).toFloat()
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 16f),
                    topLeft = Offset(12f, 12f),
                    size = Size(size.width - 24f, size.height - 24f)
                )
                startAngle += sweep
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            allocations.take(5).forEachIndexed { index, (symbol, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(8.dp)) {
                        drawCircle(colors[index % colors.size])
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$symbol ${String.format("%.1f", value / total * 100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun AddHoldingDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, AssetType, Double, Double) -> Unit
) {
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var buyPrice by remember { mutableStateOf("") }
    var isCrypto by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Holding") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Type: ", style = MaterialTheme.typography.bodyMedium)
                    FilterChip(
                        selected = !isCrypto,
                        onClick = { isCrypto = false },
                        label = { Text("Stock") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = isCrypto,
                        onClick = { isCrypto = true },
                        label = { Text("Crypto") }
                    )
                }
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it.uppercase() },
                    label = { Text(if (isCrypto) "Coin ID (e.g. bitcoin)" else "Symbol (e.g. AAPL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = buyPrice,
                    onValueChange = { buyPrice = it },
                    label = { Text("Buy Price ($)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantity.toDoubleOrNull() ?: 0.0
                    val price = buyPrice.toDoubleOrNull() ?: 0.0
                    if (symbol.isNotBlank() && qty > 0 && price > 0) {
                        onAdd(symbol, name.ifBlank { symbol }, if (isCrypto) AssetType.CRYPTO else AssetType.STOCK, qty, price)
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

