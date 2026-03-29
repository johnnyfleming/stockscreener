package com.tradescreenerai.app.ui.screens.learn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradescreenerai.app.ui.theme.*

// ─── Data model ───────────────────────────────────────────────────────────────

data class IndicatorLesson(
    val name: String,
    val category: String,
    val categoryColor: Color,
    val emoji: String,
    val whatItShows: String,
    val whyItMatters: String,
    val simpleExample: String,
    val whatItMeansForYou: String,
    val commonMistake: String
)

private val LESSONS = listOf(
    IndicatorLesson(
        name           = "RSI — Relative Strength Index",
        category       = "Momentum",
        categoryColor  = Color(0xFF7C4DFF),
        emoji          = "⚡",
        whatItShows    = "RSI shows whether a price has moved too far, too fast. It is a number between 0 and 100.",
        whyItMatters   = "Markets often bounce back after extreme moves. RSI helps you spot those moments before they happen.",
        simpleExample  = "If RSI is above 70, the price has been rising very quickly and may slow down soon. If RSI is below 30, the price has been falling a lot and could bounce back.",
        whatItMeansForYou = "Look for RSI below 30 as a possible buy opportunity. Look for RSI above 70 as a warning that the price might drop. Use it alongside other signals — not alone.",
        commonMistake  = "Buying or selling just because RSI hits 70 or 30. In a strong trend, RSI can stay high (or low) for a long time. Always wait for confirmation."
    ),
    IndicatorLesson(
        name           = "MACD — Moving Average Convergence Divergence",
        category       = "Momentum",
        categoryColor  = Color(0xFF7C4DFF),
        emoji          = "📊",
        whatItShows    = "MACD tracks the difference between two moving averages. It has three parts: the MACD line, the signal line, and the histogram (the bars).",
        whyItMatters   = "When the MACD line crosses above the signal line, it often means momentum is shifting upward. When it crosses below, momentum may be shifting downward.",
        simpleExample  = "Think of the MACD line as a faster runner and the signal line as a slower one. When the fast runner overtakes the slow one, that is a bullish signal. When the slow one is ahead, that is bearish.",
        whatItMeansForYou = "A MACD bullish crossover (MACD crossing above signal) combined with price rising above moving averages is a strong buy setup. A bearish crossover with price falling is a warning sign.",
        commonMistake  = "Using MACD alone on noisy, sideways markets. MACD works best when the asset is in a clear trend."
    ),
    IndicatorLesson(
        name           = "EMA — Exponential Moving Average",
        category       = "Trend",
        categoryColor  = Color(0xFF00BFA5),
        emoji          = "📈",
        whatItShows    = "EMA shows the average price over a set number of periods, but it gives more weight to recent prices. Common periods are 20, 50, and 200.",
        whyItMatters   = "When price is above the EMA, the trend is up. When price is below, the trend is down. The 200 EMA is the most important — it separates bull markets from bear markets.",
        simpleExample  = "If Bitcoin is trading above its 200 EMA, it is in a long-term uptrend. If it drops below, many traders treat that as a warning sign.",
        whatItMeansForYou = "Price above EMA 20 = short-term strength. Price above EMA 50 = medium-term uptrend. Price above EMA 200 = long-term bull market. When EMA 50 crosses above EMA 200, that is the \"Golden Cross\" — historically very bullish.",
        commonMistake  = "Entering a trade just because price touches the EMA. Wait for price to bounce off the EMA and close above it before acting."
    ),
    IndicatorLesson(
        name           = "SMA — Simple Moving Average",
        category       = "Trend",
        categoryColor  = Color(0xFF00BFA5),
        emoji          = "〰️",
        whatItShows    = "SMA is the plain average price over a set number of periods. Unlike EMA, it treats all prices equally — older prices have the same weight as recent ones.",
        whyItMatters   = "SMA is a smoother line than EMA, making it useful for identifying long-term trends without too many false signals.",
        simpleExample  = "The SMA 200 is used by many professional investors to decide if an asset is in a bull or bear market. If a stock's price is above its SMA 200, it is generally considered healthy.",
        whatItMeansForYou = "Use SMA 200 as the big picture trend guide. Use SMA 50 to see the medium-term trend. SMA 20 is more sensitive and useful for short-term trades.",
        commonMistake  = "Confusing SMA and EMA. EMA reacts faster to price changes. SMA is slower and smoother. For fast-moving assets like crypto, EMA may be more useful."
    ),
    IndicatorLesson(
        name           = "Bollinger Bands",
        category       = "Volatility",
        categoryColor  = Color(0xFFFF7043),
        emoji          = "🎯",
        whatItShows    = "Bollinger Bands draw three lines around the price: an upper band, a middle line (which is a moving average), and a lower band. The bands expand when the market is volatile and squeeze together when it is calm.",
        whyItMatters   = "When the price touches the lower band, it may be oversold. When it touches the upper band, it may be overbought. When the bands squeeze tight, a big move is often coming.",
        simpleExample  = "Imagine the price bouncing inside a rubber band. If the price stretches the rubber band too far upward (upper band), it tends to snap back. The same happens in the other direction.",
        whatItMeansForYou = "Look for the \"Bollinger Band Squeeze\" — when the bands narrow, a big breakout is likely. After the squeeze, the direction of the breakout tells you which way to trade.",
        commonMistake  = "Assuming that touching the upper band always means sell. In strong uptrends, price can \"ride\" the upper band for a long time. Always confirm with volume and trend direction."
    ),
    IndicatorLesson(
        name           = "VWAP — Volume Weighted Average Price",
        category       = "Volume / Trend",
        categoryColor  = Color(0xFF29B6F6),
        emoji          = "⚖️",
        whatItShows    = "VWAP is the average price of an asset during a session, weighted by how much volume traded at each price level. It shows the \"fair value\" price for the day.",
        whyItMatters   = "Institutional traders (large funds) use VWAP as a benchmark. Price above VWAP means buyers are in control. Price below VWAP means sellers are winning.",
        simpleExample  = "If you are watching Bitcoin at $60,000 and VWAP is at $59,000, the current price is above fair value for the day. This is a mild bullish signal.",
        whatItMeansForYou = "Use VWAP as a dynamic support and resistance level. Entering a long trade when price bounces off VWAP is a popular strategy among professionals.",
        commonMistake  = "Using VWAP on longer timeframes. VWAP resets every session — it works best on intraday (same-day) charts. On weekly or monthly charts, use EMA instead."
    ),
    IndicatorLesson(
        name           = "ATR — Average True Range",
        category       = "Volatility",
        categoryColor  = Color(0xFFFF7043),
        emoji          = "🌊",
        whatItShows    = "ATR measures how much an asset moves on average in each period. A high ATR means the price is swinging a lot. A low ATR means the price is calm.",
        whyItMatters   = "ATR does not tell you direction — it tells you how big the moves are. This helps you decide how wide to set your stop-loss so you are not stopped out by normal price noise.",
        simpleExample  = "If an asset has an ATR of \$500, it is normal for the price to swing \$500 in a day. Setting a stop-loss \$100 away would get triggered by normal fluctuation — not a real trend change.",
        whatItMeansForYou = "Use 1.5× to 2× ATR as your stop-loss distance. This gives the trade room to breathe while protecting you from a real reversal.",
        commonMistake  = "Ignoring ATR and setting a fixed stop-loss (like always \$100 away). Different assets have different volatility — a fixed stop does not fit all."
    ),
    IndicatorLesson(
        name           = "ADX — Average Directional Index",
        category       = "Trend Strength",
        categoryColor  = Color(0xFFFFB74D),
        emoji          = "💪",
        whatItShows    = "ADX measures how strong a trend is — not which direction. It ranges from 0 to 100. Values above 25 mean a strong trend. Values below 20 mean the market is moving sideways with no clear trend.",
        whyItMatters   = "Many strategies only work in trending markets. ADX tells you if it is safe to use those strategies right now, or if the market is too choppy.",
        simpleExample  = "If ADX is 35 and the price is rising, you have a strong uptrend — momentum strategies work well. If ADX is 15, the market is choppy and trend-following strategies will give false signals.",
        whatItMeansForYou = "Only trade in the direction of the trend when ADX is above 25. In low-ADX environments, range-trading strategies (buy low, sell high) work better than trend-following.",
        commonMistake  = "Looking only at ADX without checking the +DI and -DI lines. ADX tells you the strength, but +DI vs -DI tells you the direction. You need both."
    ),
    IndicatorLesson(
        name           = "Stochastic Oscillator",
        category       = "Momentum",
        categoryColor  = Color(0xFF7C4DFF),
        emoji          = "🔄",
        whatItShows    = "The Stochastic Oscillator compares the most recent closing price to the high-low range over a set period. Like RSI, it produces a value between 0 and 100. Above 80 = overbought. Below 20 = oversold.",
        whyItMatters   = "It helps identify potential reversal points by showing when the price is at an extreme relative to recent history.",
        simpleExample  = "If the Stochastic %K line crosses above the %D line while both are below 20, that is a bullish signal. If %K crosses below %D while both are above 80, that is a bearish signal.",
        whatItMeansForYou = "Use Stochastic to time your entries. After RSI and MACD show a bullish setup, wait for Stochastic to give a bullish crossover below 20 to confirm the bounce.",
        commonMistake  = "Using Stochastic in strong trending markets. In a powerful uptrend, Stochastic will stay in overbought territory for a long time — selling every time it hits 80 will cause many losing trades."
    ),
    IndicatorLesson(
        name           = "OBV — On-Balance Volume",
        category       = "Volume",
        categoryColor  = Color(0xFF66BB6A),
        emoji          = "📦",
        whatItShows    = "OBV tracks cumulative buying and selling pressure by adding volume on up-days and subtracting volume on down-days. When OBV rises, buyers are in control. When it falls, sellers dominate.",
        whyItMatters   = "OBV often moves before price. If OBV is rising while price is flat, big buyers are accumulating quietly — and a price rise may follow.",
        simpleExample  = "Imagine an asset's price has not moved for two weeks, but OBV has been steadily climbing. This suggests large investors are buying without moving the price yet. When they are ready, price typically follows.",
        whatItMeansForYou = "Look for OBV divergence. If price is making new highs but OBV is falling, the rally may be weak. If price is making new lows but OBV is rising, a reversal may be coming.",
        commonMistake  = "Focusing on the exact OBV number rather than its trend. The absolute number is meaningless — only the direction and slope matter."
    ),
    IndicatorLesson(
        name           = "Volume",
        category       = "Volume",
        categoryColor  = Color(0xFF66BB6A),
        emoji          = "🔊",
        whatItShows    = "Volume is simply the number of shares or coins traded during a period. High volume means many people are actively buying and selling. Low volume means there is little interest.",
        whyItMatters   = "Volume confirms price moves. A price rise on high volume is more trustworthy than a rise on low volume. Big moves on low volume are often \"fake-outs.\"",
        simpleExample  = "If Bitcoin jumps 5% today on 3× its average daily volume, that is a strong confirmed move. If it jumps 5% on 0.5× average volume, many traders will be skeptical.",
        whatItMeansForYou = "Before entering a breakout trade, always check volume. You want to see at least 1.5× the 20-day average volume for the breakout to be reliable.",
        commonMistake  = "Ignoring volume entirely. Many beginners only look at price. Volume is the \"fuel\" behind price moves — always check if the fuel matches the move."
    )
)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(onBack: () -> Unit) {
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Trend", "Momentum", "Volatility", "Volume", "Trend Strength", "Volume / Trend")
    val filtered = if (selectedCategory == "All") LESSONS
    else LESSONS.filter { it.category == selectedCategory }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Learn Indicators", fontWeight = FontWeight.Bold)
                    Text(
                        "Beginner-friendly explanations",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // Category filter chips
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered) { lesson ->
                LessonCard(lesson = lesson)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ─── Individual lesson card with expand/collapse ──────────────────────────────

@Composable
private fun LessonCard(lesson: IndicatorLesson) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = lesson.emoji,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lesson.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = lesson.categoryColor.copy(alpha = 0.18f),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = lesson.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = lesson.categoryColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick summary — always visible
            Spacer(Modifier.height(10.dp))
            Text(
                text = lesson.whatItShows,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )

            // Full detail — only when expanded
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LessonSection(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        title = "Why it matters",
                        text = lesson.whyItMatters,
                        iconColor = lesson.categoryColor
                    )

                    LessonSection(
                        icon = Icons.Filled.Lightbulb,
                        title = "Simple example",
                        text = lesson.simpleExample,
                        iconColor = GoldAccent
                    )

                    LessonSection(
                        icon = Icons.Filled.Person,
                        title = "What this means for you",
                        text = lesson.whatItMeansForYou,
                        iconColor = ElectricBlue
                    )

                    LessonSection(
                        icon = Icons.Filled.Warning,
                        title = "Common mistake",
                        text = lesson.commonMistake,
                        iconColor = LossRed
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    iconColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = iconColor
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
            )
        }
    }
}

