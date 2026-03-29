package com.tradescreenerai.app.ui.components.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradescreenerai.app.data.model.BuySellSignal
import com.tradescreenerai.app.data.model.CandlePattern
import com.tradescreenerai.app.data.model.ChartEntry
import com.tradescreenerai.app.data.model.ChartTool
import com.tradescreenerai.app.data.model.ConfluenceType
import com.tradescreenerai.app.data.model.ConfluenceZone
import com.tradescreenerai.app.data.model.DrawnObject
import com.tradescreenerai.app.data.model.PricePoint
import com.tradescreenerai.app.data.model.SupertrendLinePoint
import com.tradescreenerai.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
//  Custom overlay line drawn on top of the candlestick / line chart
//  (e.g. EMA 9, EMA 20, EMA 50, VWAP)
// ─────────────────────────────────────────────────────────────────────────────
data class OverlayLine(
    val points: List<Pair<Int, Double>>,  // candle-index → price value
    val color: Color,
    val label: String = "",
    val width: Float = 1.5f
)

// ─────────────────────────────────────────────────────────────────────────────
//  Internal transform state (zoom + horizontal pan)
// ─────────────────────────────────────────────────────────────────────────────
private data class ChartTransform(
    val scale: Float = 1f,      // 1× … 12×  (horizontal magnification)
    val panOffset: Float = 0f   // ≤ 0  (pixels shifted left)
)

// Layout constants
private const val Y_PAD  = 56f   // pixels reserved on the left for Y-axis labels
private const val X_PAD  = 8f    // right padding
private const val V_PAD  = 14f   // top/bottom vertical padding inside the canvas

// ─────────────────────────────────────────────────────────────────────────────
//  Shared gesture handler used by both chart types
//  Detects single-finger scrub OR two-finger pinch/pan.
// ─────────────────────────────────────────────────────────────────────────────
private fun Modifier.chartGestures(
    transformState: MutableState<ChartTransform>,
    onScrub: (x: Float, canvasW: Float) -> Unit,
    maxScale: Float = 12f
): Modifier = this.pointerInput(Unit) {
    val w = size.width.toFloat()
    awaitEachGesture {
        // Wait for ANY first touch
        awaitFirstDown(requireUnconsumed = false)

        var prevDist    = 0f
        var prevCentX   = 0f

        while (true) {
            val event   = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break

            val t = transformState.value
            val chartW  = w - Y_PAD - X_PAD
            val spacing = chartW * t.scale / 1f   // spacing per unit data-fraction (normalised)
            // ^ we use data.size from the closure passed via onScrub

            when (pressed.size) {
                // ── Single finger ── continuous scrub ────────────────────────
                1 -> {
                    val x = pressed[0].position.x
                    onScrub(x, w)
                    pressed[0].consume()
                    prevDist  = 0f
                    prevCentX = x
                }
                // ── Two fingers ── pinch-to-zoom + pan ───────────────────────
                else -> {
                    val p1   = pressed[0].position
                    val p2   = pressed[1].position
                    val dist = (p2 - p1).getDistance()
                    val centX = (p1.x + p2.x) / 2f

                    if (prevDist > 0f) {
                        val zoom     = dist / prevDist
                        val panDelta = centX - prevCentX
                        val newScale = (t.scale * zoom).coerceIn(1f, maxScale)
                        val maxPan   = -(newScale - 1f) * (w - Y_PAD - X_PAD)
                        transformState.value = if (newScale <= 1.01f) ChartTransform()
                        else ChartTransform(
                            scale     = newScale,
                            panOffset = (t.panOffset + panDelta).coerceIn(maxPan, 0f)
                        )
                    }
                    prevDist  = dist
                    prevCentX = centX
                    pressed.forEach { it.consume() }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Price Line Chart
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PriceLineChart(
    data: List<PricePoint>,
    modifier: Modifier = Modifier,
    fillGradient: Boolean = true,
    showGrid: Boolean = true,
    animated: Boolean = true,
    buySellSignals: List<BuySellSignal> = emptyList(),
    // Timestamps of the data the signal indices refer to (e.g. chartEntries).
    // Leave empty when data is already derived from the same source (indices match).
    referenceTimestamps: List<Long> = emptyList()
) {
    if (data.isEmpty()) return

    // -1 means no selection; ≥0 means a data index is highlighted
    var selectedIndex   by remember(data) { mutableIntStateOf(-1) }
    val transformState   = remember(data) { mutableStateOf(ChartTransform()) }
    val transform       by transformState

    val prices = remember(data) { data.map { it.price } }
    val isPositive      = prices.last() >= prices.first()
    val lineColor       = if (isPositive) GainGreen else LossRed

    val animProgress by animateFloatAsState(
        targetValue    = 1f,
        animationSpec  = tween(if (animated) 900 else 0),
        label          = "line_anim"
    )

    // Theme colours must be read here (Composable scope), not inside Canvas
    val onSurfVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // Pre-compute: signal index → nearest priceHistory index (via timestamp matching)
    val mappedSignals: List<Pair<Int, Boolean>> = remember(buySellSignals, data, referenceTimestamps) {
        buySellSignals.mapNotNull { sig ->
            val lineIdx = if (referenceTimestamps.isEmpty() || sig.index >= referenceTimestamps.size) {
                // Indices already refer to this data (e.g. stock line chart)
                sig.index.takeIf { it in data.indices }
            } else {
                // Map via timestamp: find closest price-history point
                val refTs = referenceTimestamps[sig.index]
                data.indices.minByOrNull { i ->
                    val diff = data[i].timestamp - refTs
                    if (diff < 0) -diff else diff
                }
            }
            lineIdx?.let { it to sig.isBuy }
        }
    }

    Column(modifier = modifier) {

        // ── Tooltip row ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (selectedIndex in data.indices) {
                val pt = data[selectedIndex]
                val fmtPrice = if (pt.price >= 1.0) "\$${"%,.2f".format(pt.price)}"
                               else "\$${"%,.6f".format(pt.price)}"
                val fmtTime  = java.text.SimpleDateFormat("MMM dd  HH:mm", java.util.Locale.US)
                    .format(java.util.Date(pt.timestamp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = lineColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            fmtPrice,
                            style    = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color    = lineColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        fmtTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfVariant
                    )
                }
            } else {
                Text(
                    "Drag · Pinch to zoom",
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfVariant.copy(alpha = 0.28f)
                )
            }
        }

        // ── Chart canvas ─────────────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .chartGestures(
                    transformState = transformState,
                    onScrub        = { touchX, canvasW ->
                        val t      = transformState.value
                        val sp     = (canvasW - Y_PAD - X_PAD) * t.scale / data.size.coerceAtLeast(1)
                        val raw    = ((-t.panOffset + touchX - Y_PAD) / sp).toInt()
                        selectedIndex = raw.coerceIn(0, data.size - 1)
                    },
                    maxScale = 8f
                )
        ) {
            val w = size.width
            val h = size.height
            val t = transform

            val chartW  = w - Y_PAD - X_PAD
            val spacing = chartW * t.scale / data.size.coerceAtLeast(1)

            // Visible index range
            val startIdx = ((-t.panOffset) / spacing).toInt().coerceIn(0, data.size - 1)
            val endIdx   = ((chartW - t.panOffset) / spacing + 2f).toInt()
                .coerceIn(startIdx + 1, data.size)

            // Coordinate helpers – capture startIdx/spacing in closure
            fun xFor(i: Int)   = Y_PAD + i * spacing + t.panOffset
            fun clampX(x: Float) = x.coerceIn(Y_PAD, w - X_PAD)

            // Y scale is based on visible price range only (zoomed view auto-adjusts)
            val visiblePrices = prices.subList(startIdx, endIdx)
            val minP  = visiblePrices.min()
            val maxP  = visiblePrices.max()
            val range = (maxP - minP).coerceAtLeast(0.01)

            fun yFor(p: Double): Float =
                V_PAD + (h - 2 * V_PAD) * (1f - ((p - minP) / range).toFloat())

            // ── Grid lines + Y-axis labels ────────────────────────────────────
            val labelPaint = android.graphics.Paint().apply {
                color     = android.graphics.Color.argb(140, 136, 153, 170)
                textSize  = 24f
                isAntiAlias = true
            }
            for (step in 0..4) {
                val price = minP + range * (4 - step) / 4.0
                val y     = yFor(price)
                drawLine(Color.Gray.copy(alpha = 0.1f), Offset(Y_PAD, y), Offset(w, y), 1f)
                val label = if (price >= 1.0) "\$${"%,.2f".format(price)}"
                            else "\$${"%,.5f".format(price)}"
                drawContext.canvas.nativeCanvas.drawText(label, 2f, y + 8f, labelPaint)
            }

            // ── Build price path for visible + animated portion ───────────────
            val path      = Path()
            val fillPath  = Path()
            val pts       = mutableListOf<Offset>()

            val drawEnd = startIdx + ((endIdx - startIdx) * animProgress).toInt().coerceAtLeast(1)
            for (i in startIdx until drawEnd.coerceAtMost(endIdx)) {
                val x = xFor(i)
                val y = yFor(prices[i])
                pts.add(Offset(x, y))
                if (i == startIdx) { path.moveTo(x, y); fillPath.moveTo(x, y) }
                else               { path.lineTo(x, y); fillPath.lineTo(x, y) }
            }

            // Fill gradient
            if (fillGradient && pts.size >= 2) {
                fillPath.lineTo(pts.last().x, h - V_PAD)
                fillPath.lineTo(pts.first().x, h - V_PAD)
                fillPath.close()
                drawPath(
                    fillPath,
                    Brush.verticalGradient(
                        listOf(lineColor.copy(0.38f), lineColor.copy(0.06f), Color.Transparent),
                        startY = 0f, endY = h
                    )
                )
            }

            // Main line
            drawPath(
                path, lineColor,
                style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // ── Buy / Sell signal labels (line chart) ────────────────────────
            if (mappedSignals.isNotEmpty()) {
                val signalLinePaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    strokeWidth = 1.2f
                    style       = android.graphics.Paint.Style.STROKE
                    pathEffect  = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f)
                }
                val pillBgPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.FILL
                }
                val pillTextPaint = android.graphics.Paint().apply {
                    isAntiAlias    = true
                    isFakeBoldText = true
                    textAlign      = android.graphics.Paint.Align.CENTER
                    color          = android.graphics.Color.WHITE
                    textSize       = 22f
                }
                for ((lineIdx, isBuy) in mappedSignals) {
                    if (lineIdx !in startIdx until drawEnd.coerceAtMost(endIdx)) continue
                    val localIdx = lineIdx - startIdx
                    if (localIdx !in pts.indices) continue
                    val dot        = pts[localIdx]
                    val badgeColor = if (isBuy) GainGreen else LossRed
                    val label      = if (isBuy) "BUY" else "SELL"

                    // Subtle vertical dashed guide
                    signalLinePaint.color = badgeColor.copy(alpha = 0.28f).toArgb()
                    val vLinePath = android.graphics.Path()
                    vLinePath.moveTo(dot.x, V_PAD)
                    vLinePath.lineTo(dot.x, h - V_PAD)
                    drawContext.canvas.nativeCanvas.drawPath(vLinePath, signalLinePaint)

                    // Pill dimensions
                    val pillTw = pillTextPaint.measureText(label)
                    val pillW  = pillTw + 18f
                    val pillH  = 28f
                    val cx     = dot.x
                    val pillCy = if (isBuy) dot.y + pillH / 2 + 8f else dot.y - pillH / 2 - 8f

                    // Shadow / glow
                    pillBgPaint.color = badgeColor.copy(alpha = 0.20f).toArgb()
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        android.graphics.RectF(cx - pillW / 2 - 3f, pillCy - pillH / 2 - 3f,
                            cx + pillW / 2 + 3f, pillCy + pillH / 2 + 3f),
                        8f, 8f, pillBgPaint
                    )
                    // Filled pill
                    pillBgPaint.color = badgeColor.copy(alpha = 0.92f).toArgb()
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        android.graphics.RectF(cx - pillW / 2, pillCy - pillH / 2,
                            cx + pillW / 2, pillCy + pillH / 2),
                        6f, 6f, pillBgPaint
                    )
                    // Text
                    drawContext.canvas.nativeCanvas.drawText(
                        label, cx, pillCy + pillTextPaint.textSize * 0.35f, pillTextPaint)
                }
            }

            // ── Scrubber overlay ──────────────────────────────────────────────
            val selLocal = selectedIndex - startIdx
            if (selLocal in pts.indices) {
                val dot  = pts[selLocal]
                val dash = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))

                // Vertical guide line (full height)
                drawLine(
                    lineColor.copy(alpha = 0.55f),
                    Offset(dot.x, V_PAD), Offset(dot.x, h - V_PAD),
                    strokeWidth = 1.5f, pathEffect = dash
                )
                // Horizontal guide line
                drawLine(
                    lineColor.copy(alpha = 0.22f),
                    Offset(Y_PAD, dot.y), Offset(w, dot.y),
                    strokeWidth = 1f, pathEffect = dash
                )
                // Outer glow ring
                drawCircle(lineColor.copy(alpha = 0.22f), radius = 14f, center = dot)
                // Filled dot
                drawCircle(lineColor, radius = 6f, center = dot)
                drawCircle(Color.White, radius = 3f, center = dot)

                // Live price label on Y-axis
                drawContext.canvas.nativeCanvas.drawText(
                    if (prices[selectedIndex] >= 1.0) "\$${"%,.2f".format(prices[selectedIndex])}"
                    else "\$${"%,.5f".format(prices[selectedIndex])}",
                    2f, dot.y + 8f,
                    android.graphics.Paint().apply {
                        color = lineColor.toArgb()
                        textSize = 24f; isFakeBoldText = true; isAntiAlias = true
                    }
                )
            }
        } // end Canvas

        // ── Zoom level hint
        if (transform.scale > 1.02f) {
            Text(
                "${"%.1f".format(transform.scale)}×",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helper: pick a "nice" grid step that lands on round numbers
// ─────────────────────────────────────────────────────────────────────────────
private fun niceGridStep(range: Double, targetLines: Int): Double {
    if (range <= 0.0) return 1.0
    val rough = range / targetLines
    val exp   = Math.floor(Math.log10(rough))
    val p     = Math.pow(10.0, exp)
    val frac  = rough / p
    val nice  = if (frac < 1.5) 1.0 else if (frac < 3.0) 2.0 else if (frac < 7.0) 5.0 else 10.0
    return nice * p
}

// ─────────────────────────────────────────────────────────────────────────────
//  Candlestick Chart  – professional-grade rendering
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CandlestickChart(
    data: List<ChartEntry>,
    modifier: Modifier = Modifier,
    showVolume: Boolean = true,
    buySellSignals: List<BuySellSignal> = emptyList(),
    supertrendLine: List<SupertrendLinePoint> = emptyList(),
    confluenceZones: List<ConfluenceZone> = emptyList(),
    candlePatterns: List<CandlePattern> = emptyList(),
    showSignalProgress: Boolean = true,
    overlayLines: List<OverlayLine> = emptyList(),
    drawnObjects: List<DrawnObject> = emptyList(),
    /** Fires when the user scrubs — useful for drawing tools. */
    onIndexSelected: ((index: Int, price: Double) -> Unit)? = null,
    /**
     * Reset viewport (zoom + pan) only when this key changes.
     * Pass a value that changes on symbol/timeframe switch but stays stable across live-data
     * refreshes so the user's zoom and scroll position are preserved between updates.
     */
    chartStateKey: Any = Unit
) {
    if (data.isEmpty()) return

    // ── Viewport state ────────────────────────────────────────────────────────
    // transformState resets ONLY when chartStateKey changes (symbol/interval switch).
    // Live-data refreshes keep the same key → zoom and pan are preserved.
    val transformState = remember(chartStateKey) { mutableStateOf(ChartTransform()) }
    val transform      by transformState

    // selectedIndex still tracks the first candle so it resets only on real data changes.
    val stableKey    = data.firstOrNull()?.timestamp ?: 0L
    var selectedIndex by remember(stableKey) { mutableIntStateOf(-1) }

    // Tracks the usable canvas width so ChartNavigator and auto-scroll can compute fractions
    var mainChartWidth by remember { mutableFloatStateOf(0f) }

    // ── Auto-scroll: keep latest candle visible when user is already at the right edge ──
    // Runs asynchronously so it never blocks touch input.
    val prevSize = remember { mutableIntStateOf(0) }
    LaunchedEffect(data.size) {
        val wasSize = prevSize.intValue
        prevSize.intValue = data.size
        if (data.size > wasSize && wasSize > 0 && mainChartWidth > 0f) {
            val t      = transformState.value
            val chartW = mainChartWidth
            // "At latest" = panOffset is within 120px of maxPan (far-right edge)
            val maxPan    = -(t.scale - 1f) * chartW
            val isAtRight = t.scale <= 1.01f || (t.panOffset - maxPan) < 120f
            if (isAtRight) {
                // Slide the viewport right so the new candle stays visible
                transformState.value = t.copy(
                    panOffset = (-(t.scale - 1f) * chartW).coerceAtMost(0f)
                )
            }
        }
    }

    // Theme colours – must be read in Composable scope, not inside Canvas
    val onSurfVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurf        = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier) {

        // ── OHLC Tooltip row ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (selectedIndex in data.indices) {
                val e       = data[selectedIndex]
                val isUp    = e.close >= e.open
                val cColor  = if (isUp) GainGreen else LossRed
                val fmtDate = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
                    .format(java.util.Date(e.timestamp))

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // OHLC pill
                    Surface(shape = RoundedCornerShape(6.dp), color = cColor.copy(alpha = 0.12f)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                "O" to e.open, "H" to e.high, "L" to e.low, "C" to e.close
                            ).forEach { (lbl, v) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        lbl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = onSurfVariant,
                                        fontWeight = FontWeight.Normal
                                    )
                                    Text(
                                        "%,.2f".format(v),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (lbl == "C") cColor else onSurf,
                                        fontWeight = if (lbl == "C") FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        fmtDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfVariant
                    )
                }
            } else {
                Text(
                    "Drag · Pinch to zoom",
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfVariant.copy(alpha = 0.38f)
                )
            }
        }

        // ── Chart canvas ─────────────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showVolume) 300.dp else 240.dp)
                .onSizeChanged { mainChartWidth = it.width - Y_PAD - X_PAD }
                .chartGestures(
                    transformState = transformState,
                    onScrub        = { touchX, canvasW ->
                        val t  = transformState.value
                        val sp = (canvasW - Y_PAD - X_PAD) * t.scale / data.size.coerceAtLeast(1)
                        // Candle i is centred at Y_PAD + (i + 0.5) * sp + panOffset
                        // → slot index = floor((touchX - Y_PAD - panOffset) / sp)
                        val raw = ((-t.panOffset + touchX - Y_PAD) / sp).toInt()
                        val newIdx = raw.coerceIn(0, data.size - 1)
                        selectedIndex = newIdx
                        onIndexSelected?.invoke(newIdx, data.getOrNull(newIdx)?.close ?: 0.0)
                    },
                    maxScale = 12f
                )
        ) {
            val w = size.width
            val h = size.height
            val t = transform

            // ── Height allocation ─────────────────────────────────────────────
            // Price area occupies top ~72%, volume occupies bottom ~20%, gap 3%
            // X-axis date label strip is the bottom 5% (shared with volume area on no-volume mode)
            val xAxisStripH = if (showVolume) 0f else 24f
            val priceAreaH  = if (showVolume) h * 0.72f else h - xAxisStripH
            val volAreaH    = if (showVolume) h * 0.19f else 0f
            val volAreaTop  = if (showVolume) priceAreaH + h * 0.04f else 0f
            val xAxisTop    = if (showVolume) volAreaTop + volAreaH + 2f else h - xAxisStripH

            val chartW  = w - Y_PAD - X_PAD
            // spacing = width per candle slot (includes inter-candle gap)
            val spacing = chartW * t.scale / data.size.coerceAtLeast(1)

            // ── Visible index window ──────────────────────────────────────────
            // First visible slot: floor(-panOffset / spacing)
            val startIdx = ((-t.panOffset) / spacing).toInt().coerceIn(0, data.size - 1)
            val endIdx   = ((chartW - t.panOffset) / spacing + 2f).toInt()
                .coerceIn(startIdx + 1, data.size)

            // ── Candle centre X: slot i is centred at (i + 0.5) * spacing ─────
            // This ensures equal half-gap margins on both sides of the chart.
            fun xFor(i: Int): Float = Y_PAD + (i + 0.5f) * spacing + t.panOffset

            // ── Price scaling with 8% headroom top + bottom ───────────────────
            val visible    = data.subList(startIdx, endIdx)
            val rawMin     = visible.minOf { it.low }
            val rawMax     = visible.maxOf { it.high }
            val rawRange   = (rawMax - rawMin).coerceAtLeast(0.01)
            val margin     = rawRange * 0.08                       // 8% margin
            val minP       = rawMin - margin
            val maxP       = rawMax + margin
            val priceRange = maxP - minP                           // final Y range

            val maxVol = if (showVolume) visible.maxOf { it.volume }.coerceAtLeast(1L) else 1L

            // price → pixel Y inside the price area
            fun yFor(p: Double): Float =
                V_PAD + (priceAreaH - 2f * V_PAD) * (1f - ((p - minP) / priceRange).toFloat())

            // ── Y-axis: grid lines at "nice" round numbers ────────────────────
            val gridStep   = niceGridStep(rawRange, 5)
            val firstLine  = kotlin.math.ceil(minP / gridStep) * gridStep
            val labelPaint = android.graphics.Paint().apply {
                color       = android.graphics.Color.argb(130, 140, 155, 170)
                textSize    = 22f
                isAntiAlias = true
                textAlign   = android.graphics.Paint.Align.RIGHT
            }
            var gridPrice = firstLine
            while (gridPrice <= maxP) {
                val y = yFor(gridPrice)
                if (y > V_PAD && y < priceAreaH - V_PAD) {
                    // subtle horizontal grid line
                    drawLine(Color.Gray.copy(alpha = 0.09f), Offset(Y_PAD, y), Offset(w, y), 1f)
                    // Y-axis label – right-aligned inside the Y_PAD strip
                    val label = when {
                        gridPrice >= 10_000  -> "$${"%,.0f".format(gridPrice)}"
                        gridPrice >= 1_000   -> "$${"%,.0f".format(gridPrice)}"
                        gridPrice >= 1.0     -> "$${"%,.2f".format(gridPrice)}"
                        gridPrice >= 0.01    -> "$${"%,.4f".format(gridPrice)}"
                        else                 -> "$${"%,.6f".format(gridPrice)}"
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        label, Y_PAD - 4f, y + 7f, labelPaint
                    )
                }
                gridPrice += gridStep
            }

            // ── Confluence zones (drawn as background behind everything) ─────
            if (confluenceZones.isNotEmpty()) {
                val zoneLabelPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize    = 20f
                    isFakeBoldText = true
                    textAlign   = android.graphics.Paint.Align.LEFT
                }
                for (zone in confluenceZones) {
                    val zStartIdx = zone.startIndex.coerceIn(startIdx, endIdx)
                    val zEndIdx   = zone.endIndex.coerceIn(startIdx, endIdx)
                    if (zStartIdx >= zEndIdx) continue
                    val zx1 = xFor(zStartIdx) - spacing / 2f
                    val zx2 = xFor(zEndIdx)   + spacing / 2f
                    val (zoneColor, labelColor) = when (zone.type) {
                        ConfluenceType.STRONG_BULLISH -> Color(0xFF00E676) to Color(0xFF00E676)
                        ConfluenceType.WEAK_BULLISH   -> Color(0xFF69F0AE) to Color(0xFF69F0AE)
                        ConfluenceType.STRONG_BEARISH -> Color(0xFFFF5252) to Color(0xFFFF5252)
                        ConfluenceType.WEAK_BEARISH   -> Color(0xFFFF8A80) to Color(0xFFFF8A80)
                    }
                    // Background fill
                    drawRect(
                        color   = zoneColor.copy(alpha = 0.08f),
                        topLeft = androidx.compose.ui.geometry.Offset(zx1.coerceAtLeast(Y_PAD), V_PAD),
                        size    = androidx.compose.ui.geometry.Size(
                            (zx2 - zx1).coerceAtLeast(0f),
                            priceAreaH - 2f * V_PAD
                        )
                    )
                    // Label at top-left of zone
                    zoneLabelPaint.color = labelColor.copy(alpha = 0.75f).toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        zone.label,
                        (zx1 + 4f).coerceAtLeast(Y_PAD + 2f),
                        V_PAD + 22f,
                        zoneLabelPaint
                    )
                }
            }

            // ── Supertrend line (drawn behind candles) ────────────────────────
            // Green segment = bullish (support below price)
            // Red  segment = bearish (resistance above price)
            val visibleST = supertrendLine.filter { it.index in startIdx until endIdx }
            if (visibleST.size >= 2) {
                var segPath    = Path()
                var segBull    = visibleST.first().isBullish
                var segStarted = false

                for (pt in visibleST) {
                    val px = xFor(pt.index)
                    val py = yFor(pt.value).coerceIn(V_PAD, priceAreaH - V_PAD)

                    if (!segStarted || pt.isBullish != segBull) {
                        if (segStarted) {
                            // Close the finished segment up to this point
                            segPath.lineTo(px, py)
                            val segColor = if (segBull) GainGreen else LossRed
                            // Glow
                            drawPath(segPath, segColor.copy(alpha = 0.18f),
                                style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            // Main line
                            drawPath(segPath, segColor,
                                style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        segPath    = Path().also { it.moveTo(px, py) }
                        segBull    = pt.isBullish
                        segStarted = true
                    } else {
                        segPath.lineTo(px, py)
                    }
                }
                // Final segment
                if (segStarted) {
                    val segColor = if (segBull) GainGreen else LossRed
                    drawPath(segPath, segColor.copy(alpha = 0.18f),
                        style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(segPath, segColor,
                        style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }

            // ── Custom overlay lines (EMA / VWAP / …) ────────────────────────
            for (overlay in overlayLines) {
                val ovrPts = overlay.points.filter { (i, _) -> i in startIdx until endIdx }
                if (ovrPts.size < 2) continue
                val path = Path()
                var started = false
                for ((idx, price) in ovrPts) {
                    if (price <= 0.0) continue
                    val px = xFor(idx)
                    val py = yFor(price).coerceIn(V_PAD, priceAreaH - V_PAD)
                    if (!started) { path.moveTo(px, py); started = true }
                    else path.lineTo(px, py)
                }
                if (started) {
                    drawPath(
                        path, overlay.color,
                        style = Stroke(overlay.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            // ── Candle geometry ───────────────────────────────────────────────
            // Body = 72 % of slot width (capped 2–22 px for readability).
            // Wick  = 1.5 px at normal zoom, 1 px when very zoomed-out.
            val bodyW = (spacing * 0.72f).coerceIn(2f, 22f)
            val snapW = if (bodyW >= 3f) kotlin.math.floor(bodyW).toFloat() else bodyW
            val wickW = if (spacing > 8f) 1.5f else 1f

            // ─────────────────────────────────────────────────────────────────
            //  Draw each candle:
            //   • Bullish (close ≥ open): hollow body – green outline only,
            //     transparent fill.  Classic TradingView "bar-chart" look.
            //   • Bearish (close <  open): solid red body – filled rectangle.
            //   • Wick: same colour as the body, drawn top-to-bottom so the
            //     body naturally covers the middle section.
            // ─────────────────────────────────────────────────────────────────
            for (i in startIdx until endIdx) {
                val e   = data[i]
                val cx  = xFor(i)
                if (cx + snapW < Y_PAD || cx - snapW > w) continue   // off-screen

                val isUp      = e.close >= e.open
                val bodyCol   = if (isUp) GainGreen else LossRed

                val highY     = yFor(e.high)
                val lowY      = yFor(e.low)
                val openY     = yFor(e.open)
                val closeY    = yFor(e.close)
                val bodyTop    = minOf(openY, closeY)
                val bodyBottom = maxOf(openY, closeY)
                val bodyH      = (bodyBottom - bodyTop).coerceAtLeast(1f)

                // ── 1. Wick: high-to-low as one continuous line ───────────────
                drawLine(
                    color       = bodyCol,
                    start       = Offset(cx, highY),
                    end         = Offset(cx, lowY),
                    strokeWidth = wickW
                )

                // ── 2. Body ───────────────────────────────────────────────────
                val priceSpread = if (e.open > 0) Math.abs(e.close - e.open) / e.open else 0.0
                val isDoji      = priceSpread < 0.0001

                if (isDoji) {
                    // Doji: single horizontal line at the open/close level
                    drawLine(
                        color       = bodyCol,
                        start       = Offset(cx - snapW / 2f, bodyTop),
                        end         = Offset(cx + snapW / 2f, bodyTop),
                        strokeWidth = 2f
                    )
                } else if (isUp) {
                    // ── Bullish: hollow body (transparent fill + coloured outline)
                    // Cover the wick with a slightly transparent background first so
                    // the outline doesn't blend with the wick line.
                    drawRect(
                        color   = Color(0xFF0D1117),   // match the dark chart background
                        topLeft = Offset(cx - snapW / 2f, bodyTop),
                        size    = androidx.compose.ui.geometry.Size(snapW, bodyH)
                    )
                    // Outline
                    drawRect(
                        color   = bodyCol,
                        topLeft = Offset(cx - snapW / 2f, bodyTop),
                        size    = androidx.compose.ui.geometry.Size(snapW, bodyH),
                        style   = Stroke(width = 1.5f)
                    )
                } else {
                    // ── Bearish: solid filled body
                    drawRect(
                        color   = bodyCol,
                        topLeft = Offset(cx - snapW / 2f, bodyTop),
                        size    = androidx.compose.ui.geometry.Size(snapW, bodyH)
                    )
                }

                // ── 3. Volume bar ─────────────────────────────────────────────
                if (showVolume && e.volume > 0L) {
                    val vH = (e.volume.toFloat() / maxVol * volAreaH).coerceAtLeast(1f)
                    val vW = (snapW * 1.05f).coerceIn(1.5f, 22f)
                    drawRect(
                        color   = bodyCol.copy(alpha = 0.40f),
                        topLeft = Offset(cx - vW / 2f, volAreaTop + volAreaH - vH),
                        size    = androidx.compose.ui.geometry.Size(vW, vH)
                    )
                }
            }

            // ── Buy / Sell signal labels (candle chart) ───────────────────────
            // Green "BUY" pill below candle low, red "SELL" pill above candle high
            val pillBgPaint   = android.graphics.Paint().apply { isAntiAlias = true; style = android.graphics.Paint.Style.FILL }
            val pillTextPaint = android.graphics.Paint().apply {
                isAntiAlias    = true
                isFakeBoldText = true
                textAlign      = android.graphics.Paint.Align.CENTER
                color          = android.graphics.Color.WHITE
            }

            for (signal in buySellSignals) {
                val i = signal.index
                if (i !in startIdx until endIdx) continue
                val e  = data[i]
                val cx = xFor(i)
                if (cx < Y_PAD || cx > w) continue

                val badgeColor = if (signal.isBuy) GainGreen else LossRed
                val alpha      = when (signal.strength) { 3 -> 1.0f; 2 -> 0.90f; else -> 0.72f }
                val label      = if (signal.isBuy) "BUY" else "SELL"

                // Text size / pill height scale slightly with strength
                val textSz = when (signal.strength) { 3 -> 21f; 2 -> 18f; else -> 15f }
                pillTextPaint.textSize = textSz
                val pillH  = textSz + 14f
                val pillW  = pillTextPaint.measureText(label) + 20f

                if (signal.isBuy) {
                    // Pill placed below candle low
                    val pillCy = (yFor(e.low) + pillH / 2 + 6f)
                        .coerceIn(V_PAD + pillH / 2 + 2f, priceAreaH - pillH / 2 - 2f)
                    // Glow
                    pillBgPaint.color = badgeColor.copy(alpha = alpha * 0.22f).toArgb()
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        android.graphics.RectF(cx - pillW/2 - 4f, pillCy - pillH/2 - 4f,
                            cx + pillW/2 + 4f, pillCy + pillH/2 + 4f), 9f, 9f, pillBgPaint)
                    // Pill
                    pillBgPaint.color = badgeColor.copy(alpha = alpha).toArgb()
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        android.graphics.RectF(cx - pillW/2, pillCy - pillH/2,
                            cx + pillW/2, pillCy + pillH/2), 6f, 6f, pillBgPaint)
                    // Text
                    drawContext.canvas.nativeCanvas.drawText(
                        label, cx, pillCy + textSz * 0.35f, pillTextPaint)
                } else {
                    // Pill placed above candle high
                    val pillCy = (yFor(e.high) - pillH / 2 - 6f)
                        .coerceIn(V_PAD + pillH / 2 + 2f, priceAreaH - pillH / 2 - 2f)
                    // Glow
                    pillBgPaint.color = badgeColor.copy(alpha = alpha * 0.22f).toArgb()
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        android.graphics.RectF(cx - pillW/2 - 4f, pillCy - pillH/2 - 4f,
                            cx + pillW/2 + 4f, pillCy + pillH/2 + 4f), 9f, 9f, pillBgPaint)
                    // Pill
                    pillBgPaint.color = badgeColor.copy(alpha = alpha).toArgb()
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        android.graphics.RectF(cx - pillW/2, pillCy - pillH/2,
                            cx + pillW/2, pillCy + pillH/2), 6f, 6f, pillBgPaint)
                    // Text
                    drawContext.canvas.nativeCanvas.drawText(
                        label, cx, pillCy + textSz * 0.35f, pillTextPaint)
                }
            }

            // ── Signal progress lines (trade duration) ───────────────────────
            if (showSignalProgress && buySellSignals.size >= 1) {
                val progLinePaint = android.graphics.Paint().apply { isAntiAlias = true; strokeWidth = 1.5f }
                val dashEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 5f), 0f)

                for (idx in buySellSignals.indices) {
                    val signal    = buySellSignals[idx]
                    val sigBarIdx = signal.index
                    if (sigBarIdx !in data.indices) continue
                    val nextSigIdx = buySellSignals.getOrNull(idx + 1)?.index ?: (data.size - 1)
                    val endBarIdx  = nextSigIdx.coerceAtMost(data.size - 1)
                    if (endBarIdx <= sigBarIdx) continue
                    if (endBarIdx < startIdx || sigBarIdx >= endIdx) continue

                    val entryPrice = data[sigBarIdx].close
                    val exitPrice  = data[endBarIdx].close
                    val pnlPct     = if (signal.isBuy)
                        (exitPrice - entryPrice) / entryPrice * 100.0
                    else
                        (entryPrice - exitPrice) / entryPrice * 100.0

                    val profitable = pnlPct >= 0
                    val lineColorInt = if (profitable) android.graphics.Color.argb(120, 0, 230, 118)
                                       else            android.graphics.Color.argb(120, 255, 82, 82)
                    progLinePaint.color      = lineColorInt
                    progLinePaint.pathEffect = dashEffect

                    val x1 = xFor(sigBarIdx.coerceIn(startIdx, endIdx - 1))
                    val x2 = xFor(endBarIdx.coerceIn(startIdx, endIdx - 1))
                    val y  = yFor(entryPrice).coerceIn(V_PAD, priceAreaH - V_PAD)

                    val progPath = android.graphics.Path()
                    progPath.moveTo(x1, y); progPath.lineTo(x2, y)
                    drawContext.canvas.nativeCanvas.drawPath(progPath, progLinePaint)
                }
            }

            // ── Candle Pattern markers (diamond only) ────────────────────────
            if (candlePatterns.isNotEmpty()) {
                val patBgPaint = android.graphics.Paint().apply { isAntiAlias = true }

                for (pat in candlePatterns) {
                    if (pat.index !in startIdx until endIdx) continue
                    if (pat.strength < 2) continue
                    val e  = data[pat.index]
                    val cx = xFor(pat.index)
                    if (cx < Y_PAD || cx > w) continue

                    val patColor = if (pat.isBullish) android.graphics.Color.argb(220, 0, 230, 118)
                                   else               android.graphics.Color.argb(220, 255, 82, 82)

                    val anchorY = if (pat.isBullish)
                        (yFor(e.low)  + 32f).coerceIn(V_PAD + 40f, priceAreaH - 10f)
                    else
                        (yFor(e.high) - 32f).coerceIn(V_PAD + 10f, priceAreaH - 40f)

                    val dSize = when (pat.strength) { 3 -> 7f; else -> 5f }
                    val diamondPath = android.graphics.Path().apply {
                        moveTo(cx, anchorY - dSize)
                        lineTo(cx + dSize, anchorY)
                        lineTo(cx, anchorY + dSize)
                        lineTo(cx - dSize, anchorY)
                        close()
                    }
                    patBgPaint.color = patColor
                    patBgPaint.alpha = if (pat.strength == 3) 230 else 160
                    drawContext.canvas.nativeCanvas.drawPath(diamondPath, patBgPaint)
                }
            }

            // ── Drawn Objects (professional chart tools) ──────────────────────
            if (drawnObjects.isNotEmpty()) {
                val drawPaint = android.graphics.Paint().apply { isAntiAlias = true }
                val drawTextPaint = android.graphics.Paint().apply {
                    isAntiAlias = true; textSize = 20f; isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.LEFT
                }
                val dashFx = android.graphics.DashPathEffect(floatArrayOf(8f, 5f), 0f)

                for (obj in drawnObjects) {
                    when (obj) {
                        is DrawnObject.HorizontalLine -> {
                            val y = yFor(obj.price).coerceIn(V_PAD, priceAreaH - V_PAD)
                            drawPaint.color = android.graphics.Color.argb(200, 255, 193, 7)
                            drawPaint.strokeWidth = 1.5f
                            drawPaint.style = android.graphics.Paint.Style.STROKE
                            drawPaint.pathEffect = dashFx
                            drawContext.canvas.nativeCanvas.drawLine(Y_PAD, y, w, y, drawPaint)
                            drawPaint.pathEffect = null
                            drawTextPaint.color = android.graphics.Color.argb(220, 255, 193, 7)
                            drawContext.canvas.nativeCanvas.drawText(
                                "$${"%,.4f".format(obj.price)}${if (obj.label.isNotBlank()) "  ${obj.label}" else ""}",
                                Y_PAD + 4f, y - 4f, drawTextPaint
                            )
                        }
                        is DrawnObject.TrendLine -> {
                            val x1 = xFor(obj.startIndex)
                            val y1 = yFor(obj.startPrice).coerceIn(V_PAD, priceAreaH - V_PAD)
                            val x2 = xFor(obj.endIndex)
                            val y2 = yFor(obj.endPrice).coerceIn(V_PAD, priceAreaH - V_PAD)
                            drawPaint.color = android.graphics.Color.argb(200, 33, 150, 243)
                            drawPaint.strokeWidth = 1.8f
                            drawPaint.style = android.graphics.Paint.Style.STROKE
                            drawPaint.pathEffect = null
                            if (obj.extended && x1 != x2) {
                                val slope = (y2 - y1) / (x2 - x1)
                                drawContext.canvas.nativeCanvas.drawLine(
                                    Y_PAD, y1 + slope * (Y_PAD - x1),
                                    w - X_PAD, y1 + slope * (w - X_PAD - x1),
                                    drawPaint
                                )
                            } else {
                                drawContext.canvas.nativeCanvas.drawLine(x1, y1, x2, y2, drawPaint)
                            }
                            drawPaint.style = android.graphics.Paint.Style.FILL
                            drawContext.canvas.nativeCanvas.drawCircle(x1, y1, 5f, drawPaint)
                            drawContext.canvas.nativeCanvas.drawCircle(x2, y2, 5f, drawPaint)
                        }
                        is DrawnObject.FibRetracement -> {
                            val fibLevels = listOf(0.0 to "0%", 0.236 to "23.6%", 0.382 to "38.2%",
                                0.5 to "50%", 0.618 to "61.8%", 0.786 to "78.6%", 1.0 to "100%")
                            val fibArgb = listOf(
                                android.graphics.Color.argb(180,255,82,82),
                                android.graphics.Color.argb(180,255,152,0),
                                android.graphics.Color.argb(180,255,193,7),
                                android.graphics.Color.argb(180,200,200,200),
                                android.graphics.Color.argb(180,76,175,80),
                                android.graphics.Color.argb(180,0,188,212),
                                android.graphics.Color.argb(180,255,82,82)
                            )
                            val pH = obj.highPrice - obj.lowPrice
                            fibLevels.forEachIndexed { i, (lv, lbl) ->
                                val price = obj.lowPrice + pH * (1.0 - lv)
                                val y = yFor(price).coerceIn(V_PAD, priceAreaH - V_PAD)
                                drawPaint.color = fibArgb[i]; drawPaint.strokeWidth = 1f
                                drawPaint.style = android.graphics.Paint.Style.STROKE; drawPaint.pathEffect = dashFx
                                drawContext.canvas.nativeCanvas.drawLine(Y_PAD, y, w, y, drawPaint)
                                drawPaint.pathEffect = null
                                drawTextPaint.color = fibArgb[i]
                                drawContext.canvas.nativeCanvas.drawText(
                                    "$lbl  ${"$"}${"%,.4f".format(price)}", Y_PAD + 4f, y - 3f, drawTextPaint)
                            }
                        }
                        is DrawnObject.LongPosition -> {
                            val eY  = yFor(obj.entryPrice).coerceIn(V_PAD, priceAreaH - V_PAD)
                            val slY = yFor(obj.stopLoss).coerceIn(V_PAD, priceAreaH - V_PAD)
                            val tpY = yFor(obj.takeProfit).coerceIn(V_PAD, priceAreaH - V_PAD)
                            val sx  = if (obj.entryIndex in data.indices) xFor(obj.entryIndex).coerceAtLeast(Y_PAD) else Y_PAD
                            drawPaint.style = android.graphics.Paint.Style.FILL
                            drawPaint.color = android.graphics.Color.argb(38, 0, 200, 100)
                            drawContext.canvas.nativeCanvas.drawRect(android.graphics.RectF(sx, minOf(eY,tpY), w, maxOf(eY,tpY)), drawPaint)
                            drawPaint.color = android.graphics.Color.argb(38, 255, 60, 60)
                            drawContext.canvas.nativeCanvas.drawRect(android.graphics.RectF(sx, minOf(eY,slY), w, maxOf(eY,slY)), drawPaint)
                            drawPaint.style = android.graphics.Paint.Style.STROKE; drawPaint.strokeWidth = 1.5f; drawPaint.pathEffect = dashFx
                            drawPaint.color = android.graphics.Color.argb(200,255,255,255)
                            drawContext.canvas.nativeCanvas.drawLine(sx, eY, w, eY, drawPaint)
                            drawPaint.color = android.graphics.Color.argb(220,0,200,100)
                            drawContext.canvas.nativeCanvas.drawLine(sx, tpY, w, tpY, drawPaint)
                            drawPaint.color = android.graphics.Color.argb(220,255,60,60)
                            drawContext.canvas.nativeCanvas.drawLine(sx, slY, w, slY, drawPaint)
                            drawPaint.pathEffect = null
                            val tpPct = if (obj.entryPrice>0) (obj.takeProfit-obj.entryPrice)/obj.entryPrice*100 else 0.0
                            val slPct = if (obj.entryPrice>0) (obj.entryPrice-obj.stopLoss)/obj.entryPrice*100 else 0.0
                            val rr    = if (slPct>0) tpPct/slPct else 0.0
                            drawTextPaint.color = android.graphics.Color.argb(220,0,220,100)
                            drawContext.canvas.nativeCanvas.drawText("TP ${"$"}${"%,.4f".format(obj.takeProfit)} (+${"%.1f".format(tpPct)}%)", Y_PAD+4f, tpY-4f, drawTextPaint)
                            drawTextPaint.color = android.graphics.Color.argb(220,255,80,80)
                            drawContext.canvas.nativeCanvas.drawText("SL ${"$"}${"%,.4f".format(obj.stopLoss)} (-${"%.1f".format(slPct)}%)", Y_PAD+4f, slY+16f, drawTextPaint)
                            drawTextPaint.color = android.graphics.Color.argb(200,240,240,240)
                            drawContext.canvas.nativeCanvas.drawText("⬆ LONG ${"$"}${"%,.4f".format(obj.entryPrice)}  R:R 1:${"%.1f".format(rr)}", Y_PAD+4f, eY-4f, drawTextPaint)
                        }
                        is DrawnObject.ShortPosition -> {
                            val eY  = yFor(obj.entryPrice).coerceIn(V_PAD, priceAreaH - V_PAD)
                            val slY = yFor(obj.stopLoss).coerceIn(V_PAD, priceAreaH - V_PAD)
                            val tpY = yFor(obj.takeProfit).coerceIn(V_PAD, priceAreaH - V_PAD)
                            val sx  = if (obj.entryIndex in data.indices) xFor(obj.entryIndex).coerceAtLeast(Y_PAD) else Y_PAD
                            drawPaint.style = android.graphics.Paint.Style.FILL
                            drawPaint.color = android.graphics.Color.argb(38,255,60,60)
                            drawContext.canvas.nativeCanvas.drawRect(android.graphics.RectF(sx, minOf(eY,slY), w, maxOf(eY,slY)), drawPaint)
                            drawPaint.color = android.graphics.Color.argb(38,0,200,100)
                            drawContext.canvas.nativeCanvas.drawRect(android.graphics.RectF(sx, minOf(eY,tpY), w, maxOf(eY,tpY)), drawPaint)
                            drawPaint.style = android.graphics.Paint.Style.STROKE; drawPaint.strokeWidth = 1.5f; drawPaint.pathEffect = dashFx
                            drawPaint.color = android.graphics.Color.argb(200,255,255,255)
                            drawContext.canvas.nativeCanvas.drawLine(sx, eY, w, eY, drawPaint)
                            drawPaint.color = android.graphics.Color.argb(220,255,60,60)
                            drawContext.canvas.nativeCanvas.drawLine(sx, slY, w, slY, drawPaint)
                            drawPaint.color = android.graphics.Color.argb(220,0,200,100)
                            drawContext.canvas.nativeCanvas.drawLine(sx, tpY, w, tpY, drawPaint)
                            drawPaint.pathEffect = null
                            val tpPct = if (obj.entryPrice>0) (obj.entryPrice-obj.takeProfit)/obj.entryPrice*100 else 0.0
                            val slPct = if (obj.entryPrice>0) (obj.stopLoss-obj.entryPrice)/obj.entryPrice*100 else 0.0
                            val rr    = if (slPct>0) tpPct/slPct else 0.0
                            drawTextPaint.color = android.graphics.Color.argb(220,0,220,100)
                            drawContext.canvas.nativeCanvas.drawText("TP ${"$"}${"%,.4f".format(obj.takeProfit)} (+${"%.1f".format(tpPct)}%)", Y_PAD+4f, tpY+16f, drawTextPaint)
                            drawTextPaint.color = android.graphics.Color.argb(220,255,80,80)
                            drawContext.canvas.nativeCanvas.drawText("SL ${"$"}${"%,.4f".format(obj.stopLoss)} (-${"%.1f".format(slPct)}%)", Y_PAD+4f, slY-4f, drawTextPaint)
                            drawTextPaint.color = android.graphics.Color.argb(200,240,240,240)
                            drawContext.canvas.nativeCanvas.drawText("⬇ SHORT ${"$"}${"%,.4f".format(obj.entryPrice)}  R:R 1:${"%.1f".format(rr)}", Y_PAD+4f, eY-4f, drawTextPaint)
                        }
                    }
                }
            }

            // ── X-axis date labels ────────────────────────────────────────────
            val visibleCount = endIdx - startIdx
            val labelEvery   = when {
                visibleCount <= 10  -> 2
                visibleCount <= 30  -> 5
                visibleCount <= 90  -> 10
                visibleCount <= 180 -> 20
                else                -> 30
            }.coerceAtLeast(1)

            val dateLabelPaint = android.graphics.Paint().apply {
                color       = android.graphics.Color.argb(110, 140, 155, 170)
                textSize    = 20f
                isAntiAlias = true
                textAlign   = android.graphics.Paint.Align.CENTER
            }
            val dateFormat = java.text.SimpleDateFormat(
                if (visibleCount <= 3) "MMM dd HH:mm" else "MMM dd",
                java.util.Locale.US
            )
            for (i in startIdx until endIdx step labelEvery) {
                val cx  = xFor(i)
                if (cx < Y_PAD + 20f || cx > w - X_PAD - 20f) continue
                val label = dateFormat.format(java.util.Date(data[i].timestamp))
                // Tiny tick mark
                drawLine(
                    Color.Gray.copy(alpha = 0.25f),
                    Offset(cx, priceAreaH - 2f),
                    Offset(cx, priceAreaH + 6f),
                    1f
                )
                drawContext.canvas.nativeCanvas.drawText(
                    label, cx, xAxisTop + 18f, dateLabelPaint
                )
            }

            // ── Volume panel divider ──────────────────────────────────────────
            if (showVolume) {
                drawLine(
                    Color.Gray.copy(alpha = 0.12f),
                    Offset(Y_PAD, volAreaTop - 2f),
                    Offset(w, volAreaTop - 2f),
                    1f
                )
            }

            // ── Scrubber overlay – redraws selected candle on top ─────────────
            if (selectedIndex in startIdx until endIdx) {
                val e      = data[selectedIndex]
                val cx     = xFor(selectedIndex)
                val isUp   = e.close >= e.open
                val selCol = if (isUp) GainGreen else LossRed

                val highY      = yFor(e.high)
                val lowY       = yFor(e.low)
                val openY      = yFor(e.open)
                val closeY     = yFor(e.close)
                val bodyTop    = minOf(openY, closeY)
                val bodyBottom = maxOf(openY, closeY)
                val bodyH      = (bodyBottom - bodyTop).coerceAtLeast(1f)

                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))

                // Full-height vertical guide line
                drawLine(
                    Color.White.copy(alpha = 0.28f),
                    Offset(cx, V_PAD),
                    Offset(cx, priceAreaH - V_PAD),
                    strokeWidth = 1f, pathEffect = dash
                )
                // Horizontal guide at close price
                drawLine(
                    selCol.copy(alpha = 0.30f),
                    Offset(Y_PAD, closeY), Offset(w, closeY),
                    strokeWidth = 1f, pathEffect = dash
                )

                val hlW = snapW + 4f

                // Redraw wick brighter
                drawLine(selCol, Offset(cx, highY), Offset(cx, lowY), wickW + 0.5f)

                // Redraw body in selected style (hollow bullish / solid bearish)
                val priceSpread = if (e.open > 0) Math.abs(e.close - e.open) / e.open else 0.0
                if (priceSpread < 0.0001) {
                    drawLine(selCol, Offset(cx - hlW / 2f, bodyTop), Offset(cx + hlW / 2f, bodyTop), 2f)
                } else if (isUp) {
                    drawRect(color = Color(0xFF0D1117),
                        topLeft = Offset(cx - hlW / 2f, bodyTop),
                        size = androidx.compose.ui.geometry.Size(hlW, bodyH))
                    drawRect(color = selCol,
                        topLeft = Offset(cx - hlW / 2f, bodyTop),
                        size = androidx.compose.ui.geometry.Size(hlW, bodyH),
                        style = Stroke(width = 2f))
                } else {
                    drawRect(color = selCol,
                        topLeft = Offset(cx - hlW / 2f, bodyTop),
                        size = androidx.compose.ui.geometry.Size(hlW, bodyH))
                }

                // White outline around body
                drawRect(
                    color   = Color.White.copy(alpha = 0.55f),
                    topLeft = Offset(cx - hlW / 2f - 1f, bodyTop - 1f),
                    size    = androidx.compose.ui.geometry.Size(hlW + 2f, bodyH + 2f),
                    style   = Stroke(width = 1.5f)
                )

                // Price badge on Y-axis
                drawContext.canvas.nativeCanvas.drawText(
                    when {
                        e.close >= 1.0  -> "\$${"%,.2f".format(e.close)}"
                        e.close >= 0.01 -> "\$${"%,.4f".format(e.close)}"
                        else            -> "\$${"%,.6f".format(e.close)}"
                    },
                    Y_PAD - 4f, closeY + 7f,
                    android.graphics.Paint().apply {
                        color          = selCol.toArgb()
                        textSize       = 22f
                        isFakeBoldText = true
                        isAntiAlias    = true
                        textAlign      = android.graphics.Paint.Align.RIGHT
                    }
                )
            }
        } // end Canvas

        // ── Zoom level hint ───────────────────────────────────────────────────
        if (transform.scale > 1.02f) {
            Text(
                "${"%.1f".format(transform.scale)}×",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp)
            )
        }

        // ── Chart Navigator (minimap + draggable window + Go to Latest) ───────
        if (data.size > 1) {
            Spacer(Modifier.height(6.dp))
            ChartNavigator(
                data           = data,
                transformState = transformState,
                chartWidth     = mainChartWidth,
                modifier       = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Chart Navigator  – mini overview bar beneath the main candlestick chart
//  Shows the full price history as a compressed line.  A semi-transparent
//  window highlights the currently visible range.  Drag the window with one
//  finger to pan the main chart; tap "→ Latest" to snap to the newest candle.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ChartNavigator(
    data: List<ChartEntry>,
    transformState: MutableState<ChartTransform>,
    chartWidth: Float,           // width of the main chart canvas (w - Y_PAD - X_PAD)
    modifier: Modifier = Modifier
) {
    if (data.size < 2) return

    val transform     by transformState
    val isZoomedIn     = transform.scale > 1.02f
    val isAtLatest     = !isZoomedIn || transform.panOffset >= -4f
    val navLineColor   = if (data.last().close >= data.first().close) GainGreen else LossRed
    val surfaceColor   = MaterialTheme.colorScheme.surfaceVariant
    val onSurfVariant  = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier.fillMaxWidth()) {
        // ── Mini overview canvas ──────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(surfaceColor.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .pointerInput(data.size) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val t = transformState.value
                        if (t.scale <= 1.01f) return@detectDragGestures
                        // 1 px in navigator = scale px in main chart
                        val panDelta = -dragAmount.x * t.scale
                        val maxPan   = -(t.scale - 1f) * chartWidth.coerceAtLeast(1f)
                        transformState.value = t.copy(
                            panOffset = (t.panOffset + panDelta).coerceIn(maxPan, 0f)
                        )
                    }
                }
        ) {
            val w       = size.width
            val h       = size.height
            val navW    = w - Y_PAD - X_PAD   // usable width (mirrors main chart)

            // ── Full-data mini line ───────────────────────────────────────────
            val closes  = data.map { it.close }
            val minC    = closes.min()
            val maxC    = closes.max()
            val rangeC  = (maxC - minC).coerceAtLeast(0.01)
            val vPad    = 5f

            val linePath = Path()
            closes.forEachIndexed { i, v ->
                val x = Y_PAD + navW * i.toFloat() / (closes.size - 1).coerceAtLeast(1)
                val y = vPad + (h - vPad * 2) * (1f - ((v - minC) / rangeC).toFloat())
                if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }
            // Subtle gradient fill under the line
            val fillPath = Path().also { it.addPath(linePath) }
            if (closes.size >= 2) {
                fillPath.lineTo(Y_PAD + navW, h)
                fillPath.lineTo(Y_PAD, h)
                fillPath.close()
                drawPath(fillPath, Brush.verticalGradient(
                    listOf(navLineColor.copy(0.25f), Color.Transparent),
                    startY = 0f, endY = h
                ))
            }
            drawPath(linePath, navLineColor.copy(alpha = 0.75f),
                style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // ── Visible-range highlight window ────────────────────────────────
            val t = transform
            if (t.scale > 1.01f) {
                val windowFrac  = 1f / t.scale
                val windowStartFrac = (-t.panOffset) / (t.scale * chartWidth.coerceAtLeast(1f))
                val wx = (Y_PAD + windowStartFrac * navW).coerceIn(Y_PAD, Y_PAD + navW - 4f)
                val ww = (windowFrac * navW).coerceIn(4f, navW)

                // Dim the areas outside the window
                drawRect(Color.Black.copy(alpha = 0.35f),
                    topLeft = Offset(Y_PAD, 0f),
                    size    = Size((wx - Y_PAD).coerceAtLeast(0f), h))
                drawRect(Color.Black.copy(alpha = 0.35f),
                    topLeft = Offset((wx + ww).coerceAtMost(Y_PAD + navW), 0f),
                    size    = Size((Y_PAD + navW - wx - ww).coerceAtLeast(0f), h))

                // Window fill
                drawRect(Color.White.copy(alpha = 0.10f),
                    topLeft = Offset(wx, 0f),
                    size    = Size(ww, h))
                // Window border
                drawRect(Color.White.copy(alpha = 0.55f),
                    topLeft = Offset(wx, 0f),
                    size    = Size(ww, h),
                    style   = Stroke(1.5f))
                // Drag handles on left/right edges
                val handleH = h * 0.45f
                val handleY = (h - handleH) / 2f
                drawRect(Color.White.copy(alpha = 0.75f),
                    topLeft = Offset(wx, handleY),
                    size    = Size(2.5f, handleH))
                drawRect(Color.White.copy(alpha = 0.75f),
                    topLeft = Offset(wx + ww - 2.5f, handleY),
                    size    = Size(2.5f, handleH))
            } else {
                // scale ≈ 1 → show the whole range; draw a subtle full-width outline
                drawRect(Color.White.copy(alpha = 0.08f),
                    topLeft = Offset(Y_PAD, 0f),
                    size    = Size(navW, h),
                    style   = Stroke(1f))
            }
        }

        // ── "→ Latest" snap button ────────────────────────────────────────────
        if (isZoomedIn && !isAtLatest) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 6.dp, top = 6.dp)
                    .background(Color(0xFF1A73E8).copy(alpha = 0.88f), RoundedCornerShape(6.dp))
                    .clickable {
                        val maxPan = -(transformState.value.scale - 1f) * chartWidth.coerceAtLeast(1f)
                        transformState.value = transformState.value.copy(panOffset = maxPan)
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "→ Latest",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── Hint label when not zoomed ────────────────────────────────────────
        if (!isZoomedIn) {
            Text(
                "Pinch to zoom · Navigator",
                style    = MaterialTheme.typography.labelSmall,
                color    = onSurfVariant.copy(alpha = 0.38f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mini Sparkline  (card-level, no interaction needed)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MiniSparkline(
    data: List<Double>,
    modifier: Modifier = Modifier,
    isPositive: Boolean = true
) {
    if (data.isEmpty()) return
    val color = if (isPositive) GainGreen else LossRed
    Canvas(modifier = modifier.defaultMinSize(minWidth = 40.dp, minHeight = 32.dp)) {
        val min   = data.min()
        val max   = data.max()
        val range = (max - min).coerceAtLeast(0.001)
        val path  = Path()
        data.forEachIndexed { i, v ->
            val x = size.width  * i / (data.size - 1).coerceAtLeast(1)
            val y = size.height * (1f - ((v - min) / range).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.5f, cap = StrokeCap.Round))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Time Range Selector
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TimeRangeSelector(
    ranges: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ranges.forEach { range ->
            val isSelected = range == selected
            Box(
                modifier = Modifier
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else Color.Transparent,
                        MaterialTheme.shapes.small
                    )
                    .clickable { onSelect(range) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = range,
                    style      = MaterialTheme.typography.labelMedium,
                    color      = if (isSelected) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Chart Toolbar  – professional drawing tools like Trading 212 / TradingView
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ChartToolbar(
    selectedTool: ChartTool,
    waitingForAnchor2: Boolean,
    hoveredPrice: Double,
    hasDrawings: Boolean,
    onToolSelect: (ChartTool) -> Unit,
    onSetAnchor1: () -> Unit,
    onSetAnchor2: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tools = listOf(
        ChartTool.NONE,
        ChartTool.TREND_LINE,
        ChartTool.HORIZONTAL_LINE,
        ChartTool.FIBONACCI,
        ChartTool.LONG_POSITION,
        ChartTool.SHORT_POSITION
    )

    Column(modifier = modifier) {
        // Tool selection row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tools.forEach { tool ->
                val isSelected = selectedTool == tool
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isSelected && tool == ChartTool.LONG_POSITION  -> GainGreen.copy(alpha = 0.22f)
                        isSelected && tool == ChartTool.SHORT_POSITION -> LossRed.copy(alpha = 0.22f)
                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else       -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.clickable { onToolSelect(tool) }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(tool.emoji, style = MaterialTheme.typography.bodySmall)
                        Text(
                            tool.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isSelected && tool == ChartTool.LONG_POSITION  -> GainGreen
                                isSelected && tool == ChartTool.SHORT_POSITION -> LossRed
                                isSelected -> MaterialTheme.colorScheme.primary
                                else       -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Undo & Clear buttons
            if (hasDrawings) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onUndo, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Undo, "Undo", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, "Clear All", tint = LossRed.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp))
                }
            }
        }

        // Anchor-point control for 2-tap drawing tools (Trend Line, Fibonacci)
        if (selectedTool == ChartTool.TREND_LINE || selectedTool == ChartTool.FIBONACCI) {
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (!waitingForAnchor2) "Scrub to first point, then:" else "Scrub to second point, then:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (hoveredPrice > 0) {
                        Text(
                            "@ ${"$"}${"%,.3f".format(hoveredPrice)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        modifier = Modifier.clickable {
                            if (!waitingForAnchor2) onSetAnchor1() else onSetAnchor2()
                        }
                    ) {
                        Text(
                            if (!waitingForAnchor2) "📍 Set Point 1" else "📍 Set Point 2",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Pattern Analysis Card  — AI trend prediction based on candle patterns
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PatternAnalysisCard(
    patterns: List<com.tradescreenerai.app.data.model.CandlePattern>,
    prediction: com.tradescreenerai.app.data.model.PredictionData,
    currentPrice: Double,
    modifier: Modifier = Modifier
) {
    if (patterns.isEmpty()) return

    val recentPatterns = patterns.takeLast(5)
    val bullCount = recentPatterns.count { it.isBullish }
    val bearCount = recentPatterns.size - bullCount
    val bullPct   = if (recentPatterns.isNotEmpty()) bullCount.toFloat() / recentPatterns.size else 0.5f

    val directionColor = when (prediction.direction) {
        com.tradescreenerai.app.data.model.PredictionDirection.BULLISH -> GainGreen
        com.tradescreenerai.app.data.model.PredictionDirection.BEARISH -> LossRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val directionLabel = when (prediction.direction) {
        com.tradescreenerai.app.data.model.PredictionDirection.BULLISH -> "BULLISH"
        com.tradescreenerai.app.data.model.PredictionDirection.BEARISH -> "BEARISH"
        else -> "NEUTRAL"
    }
    val directionEmoji = when (prediction.direction) {
        com.tradescreenerai.app.data.model.PredictionDirection.BULLISH -> "📈"
        com.tradescreenerai.app.data.model.PredictionDirection.BEARISH -> "📉"
        else -> "⚖️"
    }

    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🤖", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "AI Pattern Analysis",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                }
                // Confidence chip
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = directionColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(directionEmoji, style = MaterialTheme.typography.labelMedium)
                        Text(
                            "$directionLabel  ${"%.0f".format(prediction.confidence)}%",
                            style      = MaterialTheme.typography.labelMedium,
                            color      = directionColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Bull/Bear pattern bar ─────────────────────────────────────────
            Text(
                "Pattern Score: $bullCount bullish · $bearCount bearish (last ${recentPatterns.size} patterns)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(LossRed.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bullPct)
                        .fillMaxHeight()
                        .background(GainGreen.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Detected patterns list ─────────────────────────────────────────
            Text(
                "Recent Patterns",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            recentPatterns.reversed().take(4).forEach { pat ->
                val pColor = if (pat.isBullish) GainGreen else LossRed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Strength dots
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(pat.strength) {
                            Box(Modifier.size(6.dp).background(pColor, RoundedCornerShape(3.dp)))
                        }
                        repeat(3 - pat.strength) {
                            Box(Modifier.size(6.dp).background(pColor.copy(alpha = 0.2f), RoundedCornerShape(3.dp)))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(pat.emoji, style = MaterialTheme.typography.labelMedium)
                            Text(
                                pat.name,
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color      = pColor
                            )
                        }
                        Text(
                            pat.prediction,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (prediction.reasons.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(10.dp))
                Text(
                    "Key Signals",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                prediction.reasons.forEach { reason ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("•", style = MaterialTheme.typography.labelSmall, color = directionColor)
                        Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Target price row ─────────────────────────────────────────────
            if (currentPrice > 0 && prediction.direction != com.tradescreenerai.app.data.model.PredictionDirection.NEUTRAL) {
                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(10.dp))
                Text(
                    "Price Targets",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        Triple("24h",  prediction.target24h, prediction.target24h  - currentPrice),
                        Triple("7d",   prediction.target7d,  prediction.target7d   - currentPrice),
                        Triple("30d",  prediction.target30d, prediction.target30d  - currentPrice)
                    ).forEach { (label, target, diff) ->
                        val pct = if (currentPrice > 0) diff / currentPrice * 100 else 0.0
                        val tc = if (diff >= 0) GainGreen else LossRed
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (currentPrice >= 1.0) "\$${"%,.2f".format(target)}"
                                else "\$${"%,.4f".format(target)}",
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color      = tc
                            )
                            Text(
                                "${if (pct >= 0) "+" else ""}${"%.1f".format(pct)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = tc
                            )
                        }
                    }
                }
            }

            // ── Risk badge ────────────────────────────────────────────────────
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val riskColor = when (prediction.riskLevel) {
                    "Low"  -> GainGreen
                    "High" -> LossRed
                    else   -> Color(0xFFFFC107)
                }
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = riskColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        "Risk: ${prediction.riskLevel}",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = riskColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
