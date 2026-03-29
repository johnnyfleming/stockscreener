package com.tradescreenerai.app.data.model

// ─── Confluence Score (0–100 combined indicator score) ────────────────────────
data class ConfluenceResult(
    val score: Int = 50,
    val label: String = "Neutral",        // Strong Bullish / Bullish / Neutral / Bearish / Strong Bearish
    val reasons: List<String> = emptyList(), // "Why This Trade" explanations
    val emaScore: Int = 0,
    val macdScore: Int = 0,
    val rsiScore: Int = 0,
    val volumeScore: Int = 0,
    val atrScore: Int = 0,
    val adxScore: Int = 0
)

// ─── Strategy classification ──────────────────────────────────────────────────
enum class StrategyType(val displayName: String, val emoji: String) {
    BREAKOUT("Breakout", "🚀"),
    REVERSAL("Reversal", "🔄"),
    TREND_CONTINUATION("Trend", "📈"),
    HIGH_MOMENTUM("Momentum", "⚡")
}

// ─── Trade outcome tracking ───────────────────────────────────────────────────
data class TradeOutcome(
    val signalIndex: Int,
    val isBuy: Boolean,
    val entryPrice: Double,
    val exitPrice: Double,
    val pctGainLoss: Double,
    val strategy: StrategyType? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isWin: Boolean get() = if (isBuy) exitPrice > entryPrice else exitPrice < entryPrice
}

// ─── Risk overlay (ATR-based stop/target) ─────────────────────────────────────
data class RiskOverlay(
    val entryPrice: Double = 0.0,
    val stopLoss: Double = 0.0,
    val takeProfit: Double = 0.0,
    val riskRewardRatio: Double = 0.0,
    val atrValue: Double = 0.0,
    val riskPct: Double = 0.0     // % distance to stop loss
)

// ─── Multi-timeframe trend status ─────────────────────────────────────────────
data class TimeframeTrend(
    val label: String,          // "4H", "1D", "1W"
    val direction: SignalType,  // BUY / SELL / HOLD
    val description: String = ""
)

// ─── Simple backtest result ───────────────────────────────────────────────────
data class BacktestResult(
    val totalTrades: Int = 0,
    val winRate: Double = 0.0,
    val avgGainPct: Double = 0.0,
    val avgLossPct: Double = 0.0,
    val bestTradePct: Double = 0.0,
    val worstTradePct: Double = 0.0,
    val trades: List<TradeOutcome> = emptyList(),
    val perStrategy: Map<StrategyType, Pair<Int, Double>> = emptyMap() // count, winRate
)

/** A detected Japanese candlestick pattern with prediction info. */
data class CandlePattern(
    val index: Int,
    val name: String,
    val emoji: String,
    val isBullish: Boolean,
    val strength: Int,        // 1=weak 2=moderate 3=strong
    val prediction: String    // short plain-English forecast
)

/** A point on the chart where one or more indicators signal a buy or sell. */
data class BuySellSignal(
    val index: Int,           // index into the ChartEntry list
    val isBuy: Boolean,       // true = Buy (B), false = Sell (S)
    val strength: Int = 1,    // 1=weak · 2=moderate · 3=strong (multiple indicators agree)
    val label: String = "ST"  // ST · MACD · RSI · EMA · BB · STOCH · MULTI
)

/** A single value on the Supertrend line drawn on the candlestick chart. */
data class SupertrendLinePoint(
    val index: Int,
    val value: Double,
    val isBullish: Boolean   // true = green (support), false = red (resistance)
)

/** Persisted configuration for the Buy Signals scanner screen. */
data class BuySignalConfig(
    val pool: String = "Top 20",          // "Top 20" | "Top 50" | "Top 100"
    val speed: String = "Fast",           // "Fast" | "Medium" | "Slow"
    val notificationsEnabled: Boolean = true
) {
    val poolSize: Int get() = when (pool) { "Top 50" -> 50; "Top 100" -> 100; else -> 20 }
    val atrPeriod: Int get() = when (speed) { "Medium" -> 10; "Slow" -> 14; else -> 7 }
    val multiplier: Double get() = when (speed) { "Medium" -> 3.0; "Slow" -> 4.0; else -> 2.0 }
}

data class Stock(
    val symbol: String,
    val name: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val volume: Long = 0,
    val marketCap: Long = 0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val open: Double = 0.0,
    val previousClose: Double = 0.0,
    val pe: Double = 0.0,
    val dividend: Double = 0.0,
    val sector: String = "",
    val exchange: String = "",
    val description: String = "",
    val logoUrl: String = ""
)

data class Crypto(
    val id: String,
    val symbol: String,
    val name: String,
    val price: Double,
    val change24h: Double,
    val changePercent24h: Double,
    val marketCap: Long = 0,
    val volume24h: Long = 0,
    val high24h: Double = 0.0,
    val low24h: Double = 0.0,
    val circulatingSupply: Double = 0.0,
    val totalSupply: Double = 0.0,
    val maxSupply: Double? = null,
    val rank: Int = 0,
    val imageUrl: String = "",
    val sparkline: List<Double> = emptyList(),
    val ath: Double = 0.0,
    val athChangePercent: Double = 0.0
)

enum class NewsSentiment { POSITIVE, NEGATIVE, NEUTRAL }

data class NewsArticle(
    val title: String,
    val description: String,
    val url: String,
    val imageUrl: String?,
    val source: String,
    val publishedAt: String,
    val content: String? = null,
    val sentiment: NewsSentiment = NewsSentiment.NEUTRAL
)

data class ChartEntry(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

data class PricePoint(
    val timestamp: Long,
    val price: Double
)

data class WatchlistItem(
    val symbol: String,
    val name: String,
    val type: AssetType,
    val addedAt: Long = System.currentTimeMillis()
)

data class PortfolioItem(
    val symbol: String,
    val name: String,
    val type: AssetType,
    val quantity: Double,
    val buyPrice: Double,
    val currentPrice: Double = 0.0,
    val addedAt: Long = System.currentTimeMillis()
) {
    val totalValue: Double get() = quantity * currentPrice
    val totalCost: Double get() = quantity * buyPrice
    val profitLoss: Double get() = totalValue - totalCost
    val profitLossPercent: Double get() = if (totalCost > 0) (profitLoss / totalCost) * 100 else 0.0
}

data class PriceAlert(
    val id: String,
    val symbol: String,
    val name: String,
    val targetPrice: Double,
    val isAbove: Boolean, // true = alert when price goes above, false = below
    val type: AssetType,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

enum class AssetType {
    STOCK, CRYPTO, COMMODITY
}

data class ScreenerFilter(
    val minPrice: Double = 0.0,
    val maxPrice: Double = Double.MAX_VALUE,
    val minChange: Double = -100.0,
    val maxChange: Double = 100.0,
    val minVolume: Long = 0,
    val minMarketCap: Long = 0,
    val sector: String? = null,
    val sortBy: SortBy = SortBy.MARKET_CAP,
    val sortDesc: Boolean = true
)

enum class SortBy {
    PRICE, CHANGE, VOLUME, MARKET_CAP, NAME
}

data class Signal(
    val type: SignalType,
    val strength: SignalStrength,
    val reasons: List<String>
)

enum class SignalType { BUY, SELL, HOLD }
enum class SignalStrength { STRONG, MODERATE, WEAK }

data class TechnicalData(
    val rsi: Double = 50.0,
    val macd: Double = 0.0,
    val macdSignal: Double = 0.0,
    val macdHistogram: Double = 0.0,
    val sma20: Double = 0.0,
    val sma50: Double = 0.0,
    val sma200: Double = 0.0,
    val ema12: Double = 0.0,
    val ema26: Double = 0.0,
    val ema20: Double = 0.0,
    val ema50: Double = 0.0,
    val ema200: Double = 0.0,
    val bollingerUpper: Double = 0.0,
    val bollingerMiddle: Double = 0.0,
    val bollingerLower: Double = 0.0,
    val vwap: Double = 0.0,
    val atr: Double = 0.0,
    val adx: Double = 0.0,
    val plusDI: Double = 0.0,
    val minusDI: Double = 0.0,
    val stochK: Double = 50.0,
    val stochD: Double = 50.0,
    val obv: Double = 0.0,
    val volumeAvg20: Long = 0L,
    val signal: Signal = Signal(SignalType.HOLD, SignalStrength.WEAK, emptyList())
)

// ─── Smart Signal Score (0-100 weighted scoring system) ───────────────────────
data class SmartSignalScore(
    val score: Int = 50,
    val label: String = "Neutral",
    val trendScore: Double = 0.0,
    val momentumScore: Double = 0.0,
    val volumeScore: Double = 0.0,
    val volatilityScore: Double = 0.0,
    val trendStrengthScore: Double = 0.0,
    val insights: List<String> = emptyList()
)

// ─── Indicator Visibility Toggles ─────────────────────────────────────────────
data class IndicatorVisibility(
    // Basic (visible by default)
    val showEMA20: Boolean = true,
    val showEMA50: Boolean = true,
    val showVolume: Boolean = true,
    // Advanced (hidden by default — toggle with "Advanced" button)
    val showEMA200: Boolean = false,
    val showSMA20: Boolean = false,
    val showMACD: Boolean = false,
    val showRSI: Boolean = false,
    val showBollingerBands: Boolean = false,
    val showVWAP: Boolean = false,
    val showATR: Boolean = false,
    val showADX: Boolean = false,
    val showStochastic: Boolean = false,
    val showOBV: Boolean = false,
    // Overlay features
    val showConfluenceZones: Boolean = false,
    val showSignalProgress: Boolean = true
)

// ─── Confluence Zones (multi-indicator agreement highlighting) ────────────────
enum class ConfluenceType { STRONG_BULLISH, WEAK_BULLISH, STRONG_BEARISH, WEAK_BEARISH }

data class ConfluenceZone(
    val startIndex: Int,
    val endIndex: Int,
    val type: ConfluenceType
) {
    val label: String get() = when (type) {
        ConfluenceType.STRONG_BULLISH -> "Strong Bullish"
        ConfluenceType.WEAK_BULLISH   -> "Weak Bullish"
        ConfluenceType.STRONG_BEARISH -> "Strong Bearish"
        ConfluenceType.WEAK_BEARISH   -> "Weak Bearish"
    }
}

// ─── Quick Insight Panel data (beginner-friendly 3-line summary) ──────────────
data class QuickInsightData(
    val trend: String = "",
    val momentum: String = "",
    val volume: String = "",
    val alert: String? = null
)

// ─── Indicator Alert Types ─────────────────────────────────────────────────────
enum class IndicatorAlertType(val displayName: String) {
    EMA_CROSSOVER("EMA Crossover"),
    RSI_OVERBOUGHT("RSI Overbought (>70)"),
    RSI_OVERSOLD("RSI Oversold (<30)"),
    MACD_CROSSOVER("MACD Crossover"),
    BOLLINGER_BREAKOUT("Bollinger Band Breakout"),
    VWAP_CROSS("Price Crosses VWAP"),
    VOLUME_SPIKE("Volume Spike (2× Average)")
}

data class IndicatorAlert(
    val id: String,
    val symbol: String,
    val name: String,
    val alertType: IndicatorAlertType,
    val assetType: AssetType,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class SentimentData(
    val bullishPercent: Double = 50.0,
    val bearishPercent: Double = 50.0,
    val totalMentions: Int = 0,
    val trendingScore: Double = 0.0
)

// ─── Fear & Greed Index (from alternative.me) ────────────────────────────────
data class FearGreedIndex(
    val value: Int = 50,
    val label: String = "Neutral",
    val timestamp: Long = System.currentTimeMillis(),
    val previousClose: Int = 50,
    val weekAgo: Int = 50
)

// ─── AI Price Prediction ──────────────────────────────────────────────────────
data class PredictionData(
    val direction: PredictionDirection = PredictionDirection.NEUTRAL,
    val confidence: Double = 0.0,       // 0–100
    val target24h: Double = 0.0,
    val target7d: Double = 0.0,
    val target30d: Double = 0.0,
    val reasons: List<String> = emptyList(),
    val riskLevel: String = "Medium"
)

enum class PredictionDirection { BULLISH, BEARISH, NEUTRAL }

// ─── Whale Alert (large transactions) ─────────────────────────────────────────
data class WhaleTransaction(
    val symbol: String,
    val name: String,
    val amount: Double,
    val amountUsd: Double,
    val type: WhaleType,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUrl: String = ""
)

enum class WhaleType { LARGE_BUY, LARGE_SELL, EXCHANGE_INFLOW, EXCHANGE_OUTFLOW }

// ─── Market Heatmap ───────────────────────────────────────────────────────────
data class HeatmapCell(
    val id: String,
    val symbol: String,
    val name: String,
    val marketCap: Long,
    val changePercent: Double,
    val price: Double,
    val imageUrl: String = ""
)

// ─── Order Flow Imbalance (1% feature) ────────────────────────────────────────
data class OrderFlowData(
    val symbol: String,
    val buyPressure: Double = 50.0,     // 0–100
    val sellPressure: Double = 50.0,    // 0–100
    val netFlow: Double = 0.0,          // positive = buy dominant
    val volumeDelta: Long = 0L,
    val largeOrderRatio: Double = 0.0,  // % of volume from large orders
    val imbalanceLabel: String = "Balanced",
    val timestamp: Long = System.currentTimeMillis()
)

// ─── Risk Score per asset ─────────────────────────────────────────────────────
data class RiskScoreData(
    val score: Int = 50,                // 0=safe, 100=extreme risk
    val label: String = "Medium",
    val volatilityScore: Double = 0.0,
    val drawdownScore: Double = 0.0,
    val correlationScore: Double = 0.0,
    val liquidityScore: Double = 0.0
)

// ─── Correlation Pair ─────────────────────────────────────────────────────────
data class CorrelationPair(
    val symbolA: String,
    val symbolB: String,
    val correlation: Double             // -1.0 to +1.0
)

// ─── Chart Drawing Tools ──────────────────────────────────────────────────────
enum class ChartTool(val label: String, val emoji: String) {
    NONE("Cursor",     "🖱️"),
    TREND_LINE("Trend Line", "📐"),
    HORIZONTAL_LINE("H. Level", "➖"),
    FIBONACCI("Fibonacci",  "🌀"),
    LONG_POSITION("Long",   "🟢"),
    SHORT_POSITION("Short",  "🔴")
}

sealed class DrawnObject {
    data class TrendLine(
        val startIndex: Int,
        val startPrice: Double,
        val endIndex: Int,
        val endPrice: Double,
        val extended: Boolean = true
    ) : DrawnObject()

    data class HorizontalLine(
        val price: Double,
        val label: String = ""
    ) : DrawnObject()

    data class FibRetracement(
        val highIndex: Int,
        val highPrice: Double,
        val lowIndex: Int,
        val lowPrice: Double
    ) : DrawnObject()

    data class LongPosition(
        val entryIndex: Int,
        val entryPrice: Double,
        val stopLoss: Double,
        val takeProfit: Double
    ) : DrawnObject()

    data class ShortPosition(
        val entryIndex: Int,
        val entryPrice: Double,
        val stopLoss: Double,
        val takeProfit: Double
    ) : DrawnObject()
}

// ─── Multi-Timeframe Signal ───────────────────────────────────────────────────
data class MultiTimeframeSignal(
    val daily: SignalType = SignalType.HOLD,
    val weekly: SignalType = SignalType.HOLD,
    val monthly: SignalType = SignalType.HOLD,
    val confluenceScore: Int = 0,       // 0–3 (how many agree)
    val overallSignal: SignalType = SignalType.HOLD,
    val label: String = "Mixed"
)

// ─── Social Sentiment Pulse ───────────────────────────────────────────────────
data class SocialSentimentPulse(
    val symbol: String,
    val sentimentScore: Double = 50.0,  // 0=very bearish, 100=very bullish
    val buzzScore: Double = 0.0,        // relative mentions volume
    val newsScore: Double = 50.0,
    val socialScore: Double = 50.0,
    val label: String = "Neutral"
)

// ─── Push Notification Preferences ───────────────────────────────────────────
data class NotificationPrefs(
    /** Fire a system notification when a user's price alert triggers */
    val priceAlertsEnabled: Boolean = true,
    /** Market open (9:30 AM ET), close (4:00 PM ET) + 5-min warnings */
    val marketSessionEnabled: Boolean = true,
    /** Urgent / breaking market news headlines */
    val urgentNewsEnabled: Boolean = true
)

// ─── App-Wide Settings (persisted via SettingsRepository) ─────────────────────
data class AppSettings(
    // ── Notifications ─────────────────────────────────────────────────────────
    val notifAllEnabled: Boolean = true,
    val notifBuySignals: Boolean = true,
    val notifSellSignals: Boolean = true,
    val notifPriceAlerts: Boolean = true,
    val notifMarketOpen: Boolean = true,
    val notifMarketClose: Boolean = true,
    val notifHighVolatility: Boolean = false,
    val notifSound: Boolean = true,
    val notifVibration: Boolean = true,

    // ── Trading Signals ────────────────────────────────────────────────────────
    /** "Aggressive" | "Balanced" | "Conservative" */
    val signalMode: String = "Balanced",
    val showConfidenceScore: Boolean = true,
    val showSignalReasons: Boolean = true,
    val showRejectedDiagnostics: Boolean = false,

    // ── Chart Settings ─────────────────────────────────────────────────────────
    /** e.g. "1m" "5m" "15m" "1H" "4H" "1D" "1W" */
    val defaultChartTimeframe: String = "1D",
    val chartShowCandlesticks: Boolean = true,
    val chartShowVolume: Boolean = true,
    val chartShowMovingAverages: Boolean = true,
    val chartShowRSI: Boolean = true,
    val chartShowMACD: Boolean = true,
    val chartShowSupportResistance: Boolean = true,
    val chartAutoJumpToLatest: Boolean = true,

    // ── Appearance ─────────────────────────────────────────────────────────────
    /** "light" | "dark" | "system" */
    val themeMode: String = "dark",
    val compactMode: Boolean = false,
    val largerTextMode: Boolean = false,

    // ── Market Preferences ─────────────────────────────────────────────────────
    /** "Stocks" | "Crypto" | "Forex" | "Commodities" */
    val defaultMarket: String = "Crypto",
    val showPreMarketData: Boolean = false,
    val showAfterHoursData: Boolean = false,
    /** "ET" | "UTC" | "Local" */
    val timeZoneDisplay: String = "ET",
    /** "USD" | "EUR" | "GBP" | "JPY" */
    val currencyDisplay: String = "USD",

    // ── Alerts ─────────────────────────────────────────────────────────────────
    val allowRepeatedAlerts: Boolean = false,
    /** Minutes to snooze an alert before it can fire again */
    val snoozeDurationMinutes: Int = 30
)

