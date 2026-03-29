package com.tradescreenerai.app.domain

import com.tradescreenerai.app.data.model.*

/**
 * Simple backtesting engine — walks through historical chart data,
 * replays buy/sell signals, and tracks outcomes.
 */
object BacktestEngine {

    /**
     * Run a backtest on the given entries using the multi-signal engine.
     * Each signal is paired with the next opposite signal (or last bar) as its exit.
     */
    fun run(entries: List<ChartEntry>, holdBars: Int = 10): BacktestResult {
        if (entries.size < 30) return BacktestResult()

        val signals = TechnicalAnalysis.calculateMultiSignals(entries)
        if (signals.isEmpty()) return BacktestResult()

        val td = TechnicalAnalysis.calculateAllIndicators(entries)
        val trades = mutableListOf<TradeOutcome>()

        for (i in signals.indices) {
            val sig = signals[i]
            if (sig.index !in entries.indices) continue

            val entryPrice = entries[sig.index].close
            if (entryPrice <= 0) continue

            // Exit at next opposite signal, or holdBars later, or end of data
            val exitIndex = signals.getOrNull(i + 1)?.index
                ?.coerceAtMost(entries.size - 1)
                ?: minOf(sig.index + holdBars, entries.size - 1)

            if (exitIndex <= sig.index) continue
            val exitPrice = entries[exitIndex].close

            val pct = if (sig.isBuy) {
                (exitPrice - entryPrice) / entryPrice * 100
            } else {
                (entryPrice - exitPrice) / entryPrice * 100
            }

            // Classify strategy for this signal's context
            val localEntries = entries.subList(maxOf(0, sig.index - 26), sig.index + 1)
            val localTd = if (localEntries.size > 26) TechnicalAnalysis.calculateAllIndicators(localEntries) else td
            val strategy = ConfluenceScoreEngine.classifyStrategy(localEntries, localTd)

            trades.add(TradeOutcome(
                signalIndex = sig.index,
                isBuy = sig.isBuy,
                entryPrice = entryPrice,
                exitPrice = exitPrice,
                pctGainLoss = pct,
                strategy = strategy,
                timestamp = entries[sig.index].timestamp
            ))
        }

        if (trades.isEmpty()) return BacktestResult()

        val wins = trades.count { it.isWin }
        val winRate = wins.toDouble() / trades.size * 100
        val gains = trades.filter { it.pctGainLoss > 0 }.map { it.pctGainLoss }
        val losses = trades.filter { it.pctGainLoss <= 0 }.map { it.pctGainLoss }

        // Per-strategy stats
        val perStrategy = trades
            .filter { it.strategy != null }
            .groupBy { it.strategy!! }
            .mapValues { (_, list) ->
                val w = list.count { it.isWin }
                Pair(list.size, w.toDouble() / list.size * 100)
            }

        return BacktestResult(
            totalTrades = trades.size,
            winRate = winRate,
            avgGainPct = if (gains.isNotEmpty()) gains.average() else 0.0,
            avgLossPct = if (losses.isNotEmpty()) losses.average() else 0.0,
            bestTradePct = trades.maxOfOrNull { it.pctGainLoss } ?: 0.0,
            worstTradePct = trades.minOfOrNull { it.pctGainLoss } ?: 0.0,
            trades = trades,
            perStrategy = perStrategy
        )
    }
}

