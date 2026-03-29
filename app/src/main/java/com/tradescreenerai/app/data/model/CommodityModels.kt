package com.tradescreenerai.app.data.model

// ─── Commodity Type → ETF proxy tickers (Polygon.io, free tier) ──────────────
// Alpha Vantage commodity endpoints (GOLD, WTI, etc.) require a premium plan.
// We use commodity-tracking ETFs as free-tier proxies via Polygon.io.
// Daily % changes are directionally accurate; absolute prices reflect ETF NAV.
enum class CommodityType(
    val displayName: String,
    val avFunction: String,   // kept for legacy/detail screen compatibility
    val unit: String,
    val category: CommodityCategory,
    val emoji: String,
    val description: String,
    val etfTicker: String     // Polygon.io free-tier proxy ETF
) {
    GOLD(
        "Gold", "GOLD", "USD/oz", CommodityCategory.PRECIOUS_METAL, "🥇",
        "Gold is a precious metal used as a store of value and inflation hedge.", "GLD"
    ),
    SILVER(
        "Silver", "SILVER", "USD/oz", CommodityCategory.PRECIOUS_METAL, "🥈",
        "Silver is both a precious and industrial metal, used in electronics and solar panels.", "SLV"
    ),
    WTI(
        "Crude Oil (WTI)", "WTI", "USD/barrel", CommodityCategory.ENERGY, "🛢️",
        "West Texas Intermediate crude oil — the US benchmark for oil prices.", "USO"
    ),
    BRENT(
        "Brent Oil", "BRENT", "USD/barrel", CommodityCategory.ENERGY, "🛢️",
        "Brent crude oil — the international benchmark for oil prices.", "BNO"
    ),
    NATURAL_GAS(
        "Natural Gas", "NATURAL_GAS", "USD/MMBtu", CommodityCategory.ENERGY, "🔥",
        "Natural gas is used for heating, electricity, and industrial processes.", "UNG"
    ),
    COPPER(
        "Copper", "COPPER", "USD/lb", CommodityCategory.INDUSTRIAL_METAL, "🔶",
        "Copper is a key industrial metal — its price often signals economic health.", "CPER"
    ),
    WHEAT(
        "Wheat", "WHEAT", "USD/bushel", CommodityCategory.AGRICULTURE, "🌾",
        "Wheat is a staple food commodity affected by weather and geopolitics.", "WEAT"
    ),
    CORN(
        "Corn", "CORN", "USD/bushel", CommodityCategory.AGRICULTURE, "🌽",
        "Corn is used for food, animal feed, and ethanol production.", "CORN"
    ),
    COFFEE(
        "Coffee", "COFFEE", "USD/lb", CommodityCategory.AGRICULTURE, "☕",
        "Coffee is one of the most traded agricultural commodities worldwide.", "JO"
    ),
    SUGAR(
        "Sugar", "SUGAR", "USD/lb", CommodityCategory.AGRICULTURE, "🍬",
        "Sugar prices are driven by weather, ethanol demand, and government policies.", "CANE"
    );

    companion object {
        fun fromSymbol(symbol: String): CommodityType? =
            entries.find { it.name.equals(symbol, ignoreCase = true) || it.avFunction.equals(symbol, ignoreCase = true) }
    }
}

enum class CommodityCategory(val displayName: String, val emoji: String) {
    PRECIOUS_METAL("Precious Metals", "💎"),
    ENERGY("Energy", "⚡"),
    INDUSTRIAL_METAL("Industrial Metals", "🔧"),
    AGRICULTURE("Agriculture", "🌱")
}

// ─── Commodity data class ─────────────────────────────────────────────────────
data class Commodity(
    val type: CommodityType,
    val price: Double = 0.0,
    val change: Double = 0.0,
    val changePct: Double = 0.0,
    val dailyHigh: Double = 0.0,
    val dailyLow: Double = 0.0,
    val weeklyChange: Double = 0.0,
    val monthlyChange: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val symbol: String get() = type.name
    val name: String get() = type.displayName
    val unit: String get() = type.unit
    val emoji: String get() = type.emoji
    val category: CommodityCategory get() = type.category
}

// ─── Commodity performance over multiple timeframes ───────────────────────────
data class CommodityPerformance(
    val type: CommodityType,
    val dailyChangePct: Double = 0.0,
    val weeklyChangePct: Double = 0.0,
    val monthlyChangePct: Double = 0.0,
    val quarterlyChangePct: Double = 0.0,
    val yearlyChangePct: Double = 0.0
)

// ─── Commodity ranking for movers screen ──────────────────────────────────────
data class CommodityRanking(
    val commodity: Commodity,
    val score: Int = 50,        // 0–100 composite ranking
    val signal: SignalType = SignalType.HOLD,
    val reasons: List<String> = emptyList()
)

// ─── Beginner insight for any asset ───────────────────────────────────────────
data class BeginnerInsight(
    val whatIsHappening: String = "",
    val whyItMatters: String = "",
    val timeHorizon: String = "",    // "Short-term" / "Long-term" / "Mixed"
    val keyRisk: String = "",
    val riskLevel: String = "Medium" // "Low" / "Medium" / "High"
)

