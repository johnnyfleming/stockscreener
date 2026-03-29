package com.tradescreenerai.app.data.model

// ─── Ranked stock with composite score ────────────────────────────────────────
data class RankedStock(
    val stock: Stock,
    val score: Int = 50,              // 0–100 composite score
    val horizon: StockHorizon = StockHorizon.SHORT_TERM,
    val reasons: List<String> = emptyList(),
    val technicalData: TechnicalData? = null,
    val sparkline: List<Double> = emptyList(),   // last 30 close prices for mini chart
    val aiInsight: String = "",                  // AI-generated one-liner
    val confluenceScore: Int = 0,                // 0–100 confluence (bull/bear)
    val momentumScore: Double = 0.0,
    val trendScore: Double = 0.0,
    val volumeScore: Double = 0.0,
    val volatilityScore: Double = 0.0,
    val sentimentScore: Double = 0.0
)

enum class StockHorizon(val displayName: String, val emoji: String) {
    SHORT_TERM("Short-Term", "⚡"),
    LONG_TERM("Long-Term", "🏗️")
}

// ─── Screener categories ──────────────────────────────────────────────────────
enum class StockScreenerCategory(val displayName: String, val emoji: String) {
    SHORT_TERM("Short-Term Picks", "⚡"),
    LONG_TERM("Long-Term Picks", "🏗️"),
    PENNY("Penny Stocks", "💰"),
    HIGH_VOLUME("High Volume", "📊"),
    BREAKOUT("Breakouts", "🚀"),
    REVERSAL("Reversal Candidates", "🔄"),
    COMMODITY_MOVERS("Commodity Movers", "🌍")
}

// ─── Discovery result ─────────────────────────────────────────────────────────
data class DiscoveryResult(
    val category: StockScreenerCategory,
    val stocks: List<RankedStock> = emptyList(),
    val commodities: List<CommodityRanking> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

