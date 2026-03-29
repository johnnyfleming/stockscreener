package com.tradescreenerai.app.domain

import com.tradescreenerai.app.data.model.*

/**
 * Generates plain-English beginner-friendly explanation boxes for any asset.
 * Every stock, commodity, or crypto gets a simple 4-part summary:
 *  1. What is happening
 *  2. Why it matters
 *  3. Whether this looks short-term or long-term
 *  4. What the key risk is
 */
object BeginnerInsightEngine {

    fun generateStockInsight(
        stock: Stock,
        td: TechnicalData?,
        entries: List<ChartEntry> = emptyList()
    ): BeginnerInsight {
        val price = stock.price
        val changePct = stock.changePercent

        // What is happening
        val whatIsHappening = when {
            changePct > 5 -> "${stock.symbol} is surging today, up ${f(changePct)}%. The stock is seeing strong buying pressure."
            changePct > 2 -> "${stock.symbol} is having a good day, up ${f(changePct)}%. Buyers are in control."
            changePct > 0 -> "${stock.symbol} is slightly up ${f(changePct)}% today. Quiet but positive."
            changePct > -2 -> "${stock.symbol} is down ${f(changePct)}% today. A minor pullback."
            changePct > -5 -> "${stock.symbol} is falling ${f(changePct)}% today. Sellers are taking profits."
            else -> "${stock.symbol} is dropping sharply, down ${f(changePct)}%. Significant selling pressure."
        }

        // Why it matters
        val whyItMatters = buildString {
            if (td != null) {
                when {
                    td.rsi > 70 -> append("The RSI is above 70, meaning the stock may be overbought — it's risen fast and could pull back. ")
                    td.rsi < 30 -> append("The RSI is below 30, meaning the stock is oversold — it's fallen a lot and could bounce. ")
                    td.rsi in 45.0..55.0 -> append("The RSI is neutral, showing balanced buying and selling. ")
                }
                if (td.macd > td.macdSignal) {
                    append("The MACD indicator is bullish, suggesting upward momentum. ")
                } else if (td.macd < td.macdSignal) {
                    append("The MACD is bearish, suggesting downward momentum. ")
                }
                if (td.ema200 > 0 && price > td.ema200) {
                    append("The stock is trading above its 200-day average, which is a positive long-term sign.")
                } else if (td.ema200 > 0) {
                    append("The stock is below its 200-day average — the long-term trend needs watching.")
                }
            } else {
                append("Technical data is loading. Check back for detailed analysis.")
            }
        }

        // Time horizon
        val timeHorizon = if (td != null) {
            when {
                td.ema200 > 0 && price > td.ema200 && td.adx > 25 -> "Long-term — strong established trend"
                td.rsi < 35 || td.rsi > 65 -> "Short-term — momentum-driven move"
                td.macd > td.macdSignal && td.ema50 > 0 && price > td.ema50 -> "Mixed — could develop into either"
                else -> "Unclear — wait for stronger signals"
            }
        } else "Loading..."

        // Key risk
        val keyRisk = when {
            changePct > 8 -> "After a big surge, there's risk of a sharp pullback. Don't chase the move."
            changePct < -8 -> "Sharp drops can continue. Wait for stabilization before buying."
            td != null && td.rsi > 75 -> "Very overbought — high risk of a correction."
            td != null && td.atr > 0 && price > 0 && (td.atr / price) > 0.04 -> "High volatility — price could swing significantly either way."
            stock.volume > 0 && td != null && td.volumeAvg20 > 0 && stock.volume < td.volumeAvg20 / 2 -> "Low volume today — moves on low volume can be unreliable."
            else -> "Normal market risk. Always use stop-losses and never invest more than you can afford to lose."
        }

        val riskLevel = when {
            td != null && td.rsi > 75 -> "High"
            td != null && td.rsi < 25 -> "High"
            td != null && td.atr > 0 && price > 0 && (td.atr / price) > 0.04 -> "High"
            changePct > 8 || changePct < -8 -> "High"
            changePct > 3 || changePct < -3 -> "Medium"
            else -> "Low"
        }

        return BeginnerInsight(whatIsHappening, whyItMatters, timeHorizon, keyRisk, riskLevel)
    }

    fun generateCommodityInsight(
        commodity: Commodity,
        td: TechnicalData? = null
    ): BeginnerInsight {
        val changePct = commodity.changePct

        val whatIsHappening = when {
            changePct > 3 -> "${commodity.name} is rallying ${f(changePct)}% today. ${categoryContext(commodity.type)}"
            changePct > 0 -> "${commodity.name} is up ${f(changePct)}% today. Steady demand."
            changePct > -3 -> "${commodity.name} is down ${f(changePct)}% today. Mild selling pressure."
            else -> "${commodity.name} is falling ${f(changePct)}% today. ${negativeCategoryContext(commodity.type)}"
        }

        val whyItMatters = buildString {
            append(commodity.type.description)
            append(" ")
            when (commodity.type.category) {
                CommodityCategory.PRECIOUS_METAL -> append("Precious metals often rise during economic uncertainty or inflation.")
                CommodityCategory.ENERGY -> append("Energy prices affect everything from fuel costs to shipping and manufacturing.")
                CommodityCategory.AGRICULTURE -> append("Agricultural commodity prices impact food costs globally.")
                CommodityCategory.INDUSTRIAL_METAL -> append("Industrial metals signal manufacturing activity and economic health.")
            }
        }

        val timeHorizon = when {
            commodity.weeklyChange > 5 && commodity.monthlyChange > 10 -> "Long-term — sustained uptrend"
            commodity.changePct > 3 && commodity.weeklyChange < 2 -> "Short-term — today's spike may fade"
            commodity.monthlyChange > 5 -> "Medium-to-long-term trend building"
            else -> "Mixed — no clear directional commitment"
        }

        val keyRisk = when (commodity.type.category) {
            CommodityCategory.ENERGY -> "Energy prices are volatile — geopolitics, OPEC decisions, and weather can cause sudden swings."
            CommodityCategory.PRECIOUS_METAL -> "If the economy strengthens or interest rates rise, gold/silver can fall."
            CommodityCategory.AGRICULTURE -> "Weather events, trade policies, and seasonal patterns can cause unpredictable moves."
            CommodityCategory.INDUSTRIAL_METAL -> "Copper and industrial metals are sensitive to economic slowdowns."
        }

        val riskLevel = when {
            kotlin.math.abs(changePct) > 5 -> "High"
            kotlin.math.abs(changePct) > 2 -> "Medium"
            else -> "Low"
        }

        return BeginnerInsight(whatIsHappening, whyItMatters, timeHorizon, keyRisk, riskLevel)
    }

    fun generateCryptoInsight(
        crypto: Crypto,
        td: TechnicalData? = null
    ): BeginnerInsight {
        val changePct = crypto.changePercent24h

        val whatIsHappening = when {
            changePct > 10 -> "${crypto.name} is surging ${f(changePct)}% in 24h! Strong buying momentum."
            changePct > 3 -> "${crypto.name} is up ${f(changePct)}% today. Healthy upward move."
            changePct > 0 -> "${crypto.name} is slightly up ${f(changePct)}%. Consolidating."
            changePct > -5 -> "${crypto.name} is down ${f(changePct)}%. Minor correction."
            else -> "${crypto.name} is dropping ${f(changePct)}%. Significant selling pressure."
        }

        val whyItMatters = buildString {
            if (crypto.rank <= 10) append("${crypto.name} is a top-10 cryptocurrency by market cap. ")
            else if (crypto.rank <= 50) append("${crypto.name} is a mid-cap crypto. ")
            else append("${crypto.name} is a smaller cryptocurrency. ")

            if (td != null) {
                if (td.rsi > 70) append("RSI shows it's overbought. ")
                else if (td.rsi < 30) append("RSI shows it's oversold. ")
                if (td.macd > td.macdSignal) append("MACD is bullish.")
                else append("MACD is bearish.")
            }
        }

        val timeHorizon = when {
            crypto.rank <= 5 && changePct > 0 -> "Both short and long-term potential"
            changePct > 15 -> "Short-term — likely momentum-driven"
            changePct < -15 -> "Short-term — panic selling may create opportunity"
            else -> "Depends on broader market conditions"
        }

        val keyRisk = when {
            crypto.rank > 50 -> "Smaller cryptos are highly volatile and can lose most of their value."
            kotlin.math.abs(changePct) > 10 -> "Large daily moves indicate high volatility. Use caution."
            else -> "Crypto markets trade 24/7 and can be very volatile. Never invest more than you can afford to lose."
        }

        val riskLevel = when {
            kotlin.math.abs(changePct) > 10 || crypto.rank > 50 -> "High"
            kotlin.math.abs(changePct) > 5 || crypto.rank > 20 -> "Medium"
            else -> "Low"
        }

        return BeginnerInsight(whatIsHappening, whyItMatters, timeHorizon, keyRisk, riskLevel)
    }

    private fun categoryContext(type: CommodityType) = when (type.category) {
        CommodityCategory.PRECIOUS_METAL -> "Investors may be seeking safe-haven assets."
        CommodityCategory.ENERGY -> "Energy demand is up or supply concerns are driving prices higher."
        CommodityCategory.AGRICULTURE -> "Supply concerns or increased demand are pushing prices up."
        CommodityCategory.INDUSTRIAL_METAL -> "Industrial demand signals may be strengthening."
    }

    private fun negativeCategoryContext(type: CommodityType) = when (type.category) {
        CommodityCategory.PRECIOUS_METAL -> "Risk appetite is returning, reducing safe-haven demand."
        CommodityCategory.ENERGY -> "Demand concerns or supply increases are weighing on prices."
        CommodityCategory.AGRICULTURE -> "Good harvest expectations or weak demand are pushing prices down."
        CommodityCategory.INDUSTRIAL_METAL -> "Economic slowdown fears may be reducing demand."
    }

    private fun f(v: Double) = String.format(java.util.Locale.US, "%.1f", v)
}

