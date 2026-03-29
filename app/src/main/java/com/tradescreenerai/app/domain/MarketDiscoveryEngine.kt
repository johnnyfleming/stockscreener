package com.tradescreenerai.app.domain

import com.tradescreenerai.app.data.model.*

/**
 * Market discovery engine — categorizes stocks and commodities into
 * screener categories for the discovery/screener tabs.
 */
object MarketDiscoveryEngine {

    /**
     * Classify a stock into its best-fit screener categories.
     * A single stock can appear in multiple categories.
     */
    fun classifyStock(
        stock: Stock,
        entries: List<ChartEntry>,
        td: TechnicalData
    ): Set<StockScreenerCategory> {
        val categories = mutableSetOf<StockScreenerCategory>()

        // Penny stock
        if (stock.price in 0.01..5.0) {
            categories.add(StockScreenerCategory.PENNY)
        }

        // High volume
        if (td.volumeAvg20 > 0 && entries.isNotEmpty()) {
            val lastVol = entries.last().volume
            if (lastVol > td.volumeAvg20 * 2) {
                categories.add(StockScreenerCategory.HIGH_VOLUME)
            }
        }

        // Short-term candidate
        val shortScore = StockRankingEngine.scoreShortTerm(stock, entries, td)
        if (shortScore.score >= 55) {
            categories.add(StockScreenerCategory.SHORT_TERM)
        }

        // Long-term candidate
        val longScore = StockRankingEngine.scoreLongTerm(stock, entries, td)
        if (longScore.score >= 55) {
            categories.add(StockScreenerCategory.LONG_TERM)
        }

        // Breakout
        if (StockRankingEngine.isBreakout(entries, td)) {
            categories.add(StockScreenerCategory.BREAKOUT)
        }

        // Reversal
        if (StockRankingEngine.isReversal(entries, td)) {
            categories.add(StockScreenerCategory.REVERSAL)
        }

        return categories
    }

    /**
     * Rank commodities by absolute daily mover score.
     */
    fun rankCommodityMovers(commodities: List<Commodity>): List<CommodityRanking> {
        return commodities.map { commodity ->
            val score = when {
                kotlin.math.abs(commodity.changePct) > 5 -> 90
                kotlin.math.abs(commodity.changePct) > 3 -> 75
                kotlin.math.abs(commodity.changePct) > 1 -> 60
                kotlin.math.abs(commodity.changePct) > 0.5 -> 45
                else -> 30
            }

            val signal = when {
                commodity.changePct > 2 -> SignalType.BUY
                commodity.changePct < -2 -> SignalType.SELL
                else -> SignalType.HOLD
            }

            val reasons = buildList {
                if (commodity.changePct > 3) add("Strong daily move: +${f(commodity.changePct)}%")
                else if (commodity.changePct < -3) add("Sharp decline: ${f(commodity.changePct)}%")
                if (commodity.weeklyChange > 5) add("Weekly trend: +${f(commodity.weeklyChange)}%")
                else if (commodity.weeklyChange < -5) add("Weekly decline: ${f(commodity.weeklyChange)}%")
                if (commodity.monthlyChange > 10) add("Strong monthly gain: +${f(commodity.monthlyChange)}%")
            }

            CommodityRanking(commodity, score, signal, reasons)
        }.sortedByDescending { kotlin.math.abs(it.commodity.changePct) }
    }

    /**
     * Build all discovery results from pre-analyzed stocks and commodities.
     */
    fun buildDiscoveryResults(
        rankedShortTerm: List<RankedStock>,
        rankedLongTerm: List<RankedStock>,
        pennyStocks: List<Stock>,
        highVolumeStocks: List<RankedStock>,
        breakoutStocks: List<RankedStock>,
        reversalStocks: List<RankedStock>,
        commodityMovers: List<CommodityRanking>
    ): List<DiscoveryResult> {
        return listOf(
            DiscoveryResult(StockScreenerCategory.SHORT_TERM, stocks = rankedShortTerm),
            DiscoveryResult(StockScreenerCategory.LONG_TERM, stocks = rankedLongTerm),
            DiscoveryResult(StockScreenerCategory.PENNY, stocks = pennyStocks.map {
                RankedStock(stock = it, horizon = StockHorizon.SHORT_TERM)
            }),
            DiscoveryResult(StockScreenerCategory.HIGH_VOLUME, stocks = highVolumeStocks),
            DiscoveryResult(StockScreenerCategory.BREAKOUT, stocks = breakoutStocks),
            DiscoveryResult(StockScreenerCategory.REVERSAL, stocks = reversalStocks),
            DiscoveryResult(StockScreenerCategory.COMMODITY_MOVERS, commodities = commodityMovers)
        )
    }

    private fun f(v: Double) = String.format(java.util.Locale.US, "%.1f", v)
}

