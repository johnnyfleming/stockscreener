package com.tradescreenerai.app.data.model

// ─── Day Trader Mode – Intraday intervals ─────────────────────────────────────
enum class DayTraderInterval(val label: String, val binanceCode: String, val finnhubCode: String, val coinCapCode: String, val refreshMs: Long) {
    M1 ("1m",  "1m",  "1",  "m1",  5_000L),
    M5 ("5m",  "5m",  "5",  "m5",  10_000L),
    M15("15m", "15m", "15", "m15", 15_000L),
    M30("30m", "30m", "30", "m30", 30_000L),
    H1 ("1h",  "1h",  "60", "h1",  60_000L)
}

// ─── Riskiness level ──────────────────────────────────────────────────────────
// HIGH (Aggressive) thresholds are intentionally frozen — do NOT adjust them.
// LOW / MEDIUM are tuned independently to surface more usable setups.
enum class RiskinessLevel(val label: String, val emoji: String, val minConfirmations: Int, val scoreThreshold: Int) {
    LOW   ("Conservative", "🛡️", 3, 60),   // was (4, 72) — strong-trend & S/R setups now pass
    MEDIUM("Balanced",     "⚖️", 2, 45),   // was (3, 55) — trend + 1-2 confirmations now pass
    HIGH  ("Aggressive",   "🔥", 2, 35)    // ── UNCHANGED ──
}

// ─── Signal strength ──────────────────────────────────────────────────────────
enum class DayTraderSignalStrength(val label: String, val emoji: String) {
    WEAK  ("Weak",   "🟡"),
    MEDIUM("Medium", "🟠"),
    STRONG("Strong", "🟢")
}

// ─── Confirmation layer result ────────────────────────────────────────────────
data class ConfirmationLayer(
    val name: String,
    val passed: Boolean,
    val detail: String
)

// ─── Latest bar analysis status ───────────────────────────────────────────────
data class LatestBarStatus(
    val hasPattern: Boolean = false,
    val patternName: String = "",
    val patternEmoji: String = "",
    val isBullishPattern: Boolean = false,
    val layers: List<ConfirmationLayer> = emptyList(),
    val score: Int = 0,
    val scoreNeeded: Int = 45,
    val confirmedSignal: Boolean = false,
    val blockedReasons: List<String> = emptyList(),
    // ── Diagnostics: how close a rejected signal was to passing ──────────────
    val scoreGap: Int = 0,                          // pts short of threshold (0 when passing)
    val nearMissLayers: List<String> = emptyList()  // hints for indicators that almost confirmed
)

// ─── Day Trader backtest result ───────────────────────────────────────────────
data class DayTraderBacktestResult(
    val totalSignals: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val winRate: Double = 0.0,
    val avgGainPct: Double = 0.0,
    val avgLossPct: Double = 0.0,
    val expectancy: Double = 0.0
)

// ─── Market session ───────────────────────────────────────────────────────────
enum class MarketSessionType { PRE_MARKET, REGULAR, AFTER_HOURS, CLOSED, CRYPTO_24H }

data class MarketSessionInfo(
    val type: MarketSessionType = MarketSessionType.CLOSED,
    val label: String = "Market Closed",
    val openTime: String = "",
    val closeTime: String = "",
    val countdownLabel: String = "",
    val countdownMs: Long = 0L,
    val isExtendedHours: Boolean = false,
    val exchangeName: String = ""
)

// ─── Day Trader signal ────────────────────────────────────────────────────────
data class DayTraderSignal(
    val index: Int,
    val isBuy: Boolean,
    val confidence: Int,          // 0–100 score
    val entryPrice: Double = 0.0,
    val stopLoss: Double = 0.0,
    val takeProfit: Double = 0.0,
    val riskReward: Double = 0.0,
    val reasons: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    // ── Enhanced fields ──────────────────────────────────────────────────────
    val patternName: String = "",
    val patternEmoji: String = "",
    val strengthLabel: DayTraderSignalStrength = DayTraderSignalStrength.WEAK,
    val confirmationLayers: List<ConfirmationLayer> = emptyList(),
    val isEarlySignal: Boolean = false   // true = aggressive mode, candle still live
) {
    val label: String get() = if (isBuy) "BUY" else "SELL"
}

// ─── Day Trader indicator visibility ──────────────────────────────────────────
data class DayTraderIndicators(
    val showVWAP: Boolean = true,
    val showEMA9: Boolean = true,
    val showEMA20: Boolean = true,
    val showEMA50: Boolean = false,
    val showVolume: Boolean = true,
    val showMACD: Boolean = true,
    val showRSI: Boolean = true,
    val showATR: Boolean = false,
    val showSignals: Boolean = true
)

// ─── Persisted Day Trader settings ────────────────────────────────────────────
data class DayTraderSettings(
    val interval: DayTraderInterval = DayTraderInterval.M1,
    val riskiness: RiskinessLevel = RiskinessLevel.MEDIUM,
    val indicators: DayTraderIndicators = DayTraderIndicators(),
    val chartType: String = "Candlestick",
    val alertOnBuy: Boolean = true,
    val alertOnSell: Boolean = true,
    val alertVWAPCross: Boolean = true,
    val alertVolumeSpike: Boolean = true,
    val alertRSIExtreme: Boolean = true,
    val alertMarketOpen: Boolean = true,
    val alertMarketClose15m: Boolean = true
)

// ─── Day Trader alert event ───────────────────────────────────────────────────
enum class DayTraderAlertType(val label: String) {
    BUY_TRIGGER("Buy Signal"),
    SELL_TRIGGER("Sell Signal"),
    VWAP_CROSS("Price Crossed VWAP"),
    VOLUME_SPIKE("Volume Spike"),
    RSI_OVERBOUGHT("RSI Overbought"),
    RSI_OVERSOLD("RSI Oversold"),
    MARKET_OPEN("Market Open"),
    MARKET_CLOSE_15M("Market Closes in 15m")
}

data class DayTraderAlert(
    val type: DayTraderAlertType,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
