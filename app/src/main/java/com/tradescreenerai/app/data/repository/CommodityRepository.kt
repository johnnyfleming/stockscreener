package com.tradescreenerai.app.data.repository

import com.tradescreenerai.app.BuildConfig
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.remote.RetrofitClient
import com.tradescreenerai.app.util.Constants
import com.tradescreenerai.app.util.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for commodity data via Finnhub + Polygon.io ETF proxies.
 *
 * Current prices are fetched from Finnhub's /quote endpoint (free tier, no subscription needed).
 * Historical bars for weekly/monthly % changes are fetched from Polygon.io agg bars (free tier).
 *
 * ETF proxy map:
 *   GLD  → Gold       SLV  → Silver      USO  → WTI Oil
 *   BNO  → Brent Oil  UNG  → Natural Gas  CPER → Copper
 *   WEAT → Wheat      CORN → Corn         JO   → Coffee
 *   CANE → Sugar
 */
class CommodityRepository {

    private val finnhubApi   = RetrofitClient.finnhubApi
    private val polygonApi   = RetrofitClient.polygonApi
    private val yahooApi     = RetrofitClient.yahooFinanceApi
    private val alphaVantageApi = RetrofitClient.alphaVantageApi
    private val finnhubKey   = BuildConfig.FINNHUB_KEY
    private val polygonKey   = BuildConfig.POLYGON_KEY
    private val avKey        = BuildConfig.ALPHA_VANTAGE_KEY

    private val commodityCache = mutableMapOf<CommodityType, CommodityCacheEntry>()
    private val chartCache     = mutableMapOf<String, List<ChartEntry>>()

    private data class CommodityCacheEntry(
        val commodity: Commodity,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        val isStale: Boolean
            get() = System.currentTimeMillis() - timestamp > Constants.COMMODITY_CACHE_TTL_MS
    }

    /**
     * Fetch all 10 commodities via a single Yahoo Finance batch request (free, fast).
     * Falls back to parallel individual fetches if the batch fails.
     */
    suspend fun getAllCommodities(): Resource<List<Commodity>> = withContext(Dispatchers.IO) {
        // Serve fully from cache if available
        val cached = CommodityType.entries
            .mapNotNull { commodityCache[it]?.takeIf { e -> !e.isStale }?.commodity }
        if (cached.size == CommodityType.entries.size) {
            return@withContext Resource.Success(cached)
        }

        // ── 1. Single Yahoo Finance batch call for all ETF tickers ───────────
        try {
            val allTickers = CommodityType.entries.joinToString(",") { it.etfTicker }
            val resp    = yahooApi.getQuotes(symbols = allTickers)
            val results = resp.getAsJsonObject("quoteResponse")?.getAsJsonArray("result")
            if (results != null && results.size() >= CommodityType.entries.size / 2) {
                val priceMap = (0 until results.size()).associate { i ->
                    val q = results[i].asJsonObject
                    (q.get("symbol")?.asString ?: "") to q
                }
                val commodities = CommodityType.entries.map { type ->
                    val q         = priceMap[type.etfTicker]
                    val old       = commodityCache[type]?.commodity
                    if (q != null) {
                        val rawPrice  = q.get("regularMarketPrice")?.asDouble ?: 0.0
                        val prevClose = q.get("regularMarketPreviousClose")?.asDouble ?: 0.0
                        val price     = if (rawPrice > 0) rawPrice else prevClose
                        if (price > 0) {
                            val commodity = Commodity(
                                type          = type,
                                price         = price,
                                change        = q.get("regularMarketChange")?.asDouble ?: 0.0,
                                changePct     = q.get("regularMarketChangePercent")?.asDouble ?: 0.0,
                                dailyHigh     = q.get("regularMarketDayHigh")?.asDouble ?: price,
                                dailyLow      = q.get("regularMarketDayLow")?.asDouble  ?: price,
                                weeklyChange  = old?.weeklyChange  ?: 0.0,
                                monthlyChange = old?.monthlyChange ?: 0.0,
                                lastUpdated   = System.currentTimeMillis()
                            )
                            commodityCache[type] = CommodityCacheEntry(commodity)
                            commodity
                        } else old ?: Commodity(type = type)
                    } else old ?: Commodity(type = type)
                }
                // Return if we got at least half with real prices
                if (commodities.count { it.price > 0 } >= CommodityType.entries.size / 2) {
                    return@withContext Resource.Success(commodities)
                }
            }
        } catch (_: Exception) { }

        // ── 2. Fallback: parallel individual fetches (Yahoo → Finnhub → AV) ──
        return@withContext try {
            val results = coroutineScope {
                CommodityType.entries.map { type ->
                    async { fetchOneFinnhub(type) }
                }.map { it.await() }
            }
            Resource.Success(results)
        } catch (e: Exception) {
            // Return stale cache on network error
            val stale = CommodityType.entries.map { commodityCache[it]?.commodity ?: Commodity(type = it) }
            if (stale.any { it.price > 0 }) Resource.Success(stale)
            else Resource.Error(e.message ?: "Failed to load commodity data")
        }
    }

    /** Fetch a single commodity quote using Finnhub (free, no subscription). */
    suspend fun getCommodityQuote(type: CommodityType): Resource<Commodity> = withContext(Dispatchers.IO) {
        commodityCache[type]?.takeIf { !it.isStale }?.let { return@withContext Resource.Success(it.commodity) }
        return@withContext try {
            Resource.Success(fetchOneFinnhub(type))
        } catch (e: Exception) {
            commodityCache[type]?.let { Resource.Success(it.commodity) }
                ?: Resource.Error(e.message ?: "Failed to fetch ${type.displayName}")
        }
    }

    private suspend fun fetchOneFinnhub(type: CommodityType): Commodity {
        val ticker = type.etfTicker
        val old    = commodityCache[type]?.commodity

        // ── 1. Yahoo Finance (free, no key, works for ETFs like GLD/SLV/USO) ──
        try {
            val resp    = yahooApi.getQuotes(symbols = ticker)
            val results = resp.getAsJsonObject("quoteResponse")?.getAsJsonArray("result")
            if (results != null && results.size() > 0) {
                val q         = results[0].asJsonObject
                val rawPrice  = q.get("regularMarketPrice")?.asDouble ?: 0.0
                val prevClose = q.get("regularMarketPreviousClose")?.asDouble ?: 0.0
                val price     = if (rawPrice > 0) rawPrice else prevClose
                if (price > 0) {
                    val commodity = Commodity(
                        type          = type,
                        price         = price,
                        change        = q.get("regularMarketChange")?.asDouble ?: 0.0,
                        changePct     = q.get("regularMarketChangePercent")?.asDouble ?: 0.0,
                        dailyHigh     = q.get("regularMarketDayHigh")?.asDouble ?: price,
                        dailyLow      = q.get("regularMarketDayLow")?.asDouble  ?: price,
                        weeklyChange  = old?.weeklyChange  ?: 0.0,
                        monthlyChange = old?.monthlyChange ?: 0.0,
                        lastUpdated   = System.currentTimeMillis()
                    )
                    commodityCache[type] = CommodityCacheEntry(commodity)
                    return commodity
                }
            }
        } catch (_: Exception) { }

        // ── 2. Finnhub fallback ───────────────────────────────────────────────
        try {
            val r         = finnhubApi.getQuote(symbol = ticker, token = finnhubKey)
            val current   = r.get("c")?.asDouble  ?: 0.0
            val prevClose = r.get("pc")?.asDouble ?: 0.0
            val price     = if (current > 0) current else prevClose
            if (price > 0) {
                val commodity = Commodity(
                    type          = type,
                    price         = price,
                    change        = r.get("d")?.asDouble  ?: 0.0,
                    changePct     = r.get("dp")?.asDouble ?: 0.0,
                    dailyHigh     = r.get("h")?.asDouble  ?: price,
                    dailyLow      = r.get("l")?.asDouble  ?: price,
                    weeklyChange  = old?.weeklyChange  ?: 0.0,
                    monthlyChange = old?.monthlyChange ?: 0.0,
                    lastUpdated   = System.currentTimeMillis()
                )
                commodityCache[type] = CommodityCacheEntry(commodity)
                return commodity
            }
        } catch (_: Exception) { }

        // ── 3. Alpha Vantage GLOBAL_QUOTE fallback (25 calls/day) ────────────
        try {
            val r     = alphaVantageApi.getQuote(symbol = ticker, apiKey = avKey)
            val quote = r.getAsJsonObject("Global Quote")
            if (quote != null) {
                val rawPrice  = quote.get("05. price")?.asDouble ?: 0.0
                val prevClose = quote.get("08. previous close")?.asDouble ?: 0.0
                val price     = if (rawPrice > 0) rawPrice else prevClose
                if (price > 0) {
                    val commodity = Commodity(
                        type          = type,
                        price         = price,
                        change        = quote.get("09. change")?.asDouble ?: 0.0,
                        changePct     = quote.get("10. change percent")?.asString
                                            ?.replace("%", "")?.toDoubleOrNull() ?: 0.0,
                        dailyHigh     = quote.get("03. high")?.asDouble ?: price,
                        dailyLow      = quote.get("04. low")?.asDouble  ?: price,
                        weeklyChange  = old?.weeklyChange  ?: 0.0,
                        monthlyChange = old?.monthlyChange ?: 0.0,
                        lastUpdated   = System.currentTimeMillis()
                    )
                    commodityCache[type] = CommodityCacheEntry(commodity)
                    return commodity
                }
            }
        } catch (_: Exception) { }

        // Return cached (possibly stale) or empty placeholder
        return old ?: Commodity(type = type)
    }

    /**
     * Fetch historical chart data using Polygon agg bars on the ETF proxy.
     * @param interval "daily", "weekly", or "monthly"
     */
    suspend fun getCommodityHistory(
        type: CommodityType,
        interval: String = "daily"
    ): Resource<List<ChartEntry>> = withContext(Dispatchers.IO) {
        val cacheKey = "${type.name}_$interval"
        chartCache[cacheKey]?.let { return@withContext Resource.Success(it) }

        return@withContext try {
            val sdf      = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today    = sdf.format(Date())
            val daysBack = when (interval) { "weekly" -> 365; "monthly" -> 1825; else -> 90 }
            val from     = sdf.format(Date(System.currentTimeMillis() - daysBack.toLong() * 86_400_000L))
            val timespan = when (interval) { "weekly" -> "week"; "monthly" -> "month"; else -> "day" }

            val response = polygonApi.getAggBars(
                ticker     = type.etfTicker,
                multiplier = 1,
                timespan   = timespan,
                from       = from,
                to         = today,
                adjusted   = true,
                sort       = "asc",
                limit      = 500,
                apiKey     = polygonKey
            )

            val results = response.getAsJsonArray("results")
            if (results == null || results.size() == 0) {
                return@withContext chartCache[cacheKey]?.let { Resource.Success(it) }
                    ?: Resource.Error("No history available for ${type.displayName}")
            }

            val entries = (0 until results.size()).mapNotNull { i ->
                val bar = results[i].asJsonObject
                val t   = bar.get("t")?.asLong ?: return@mapNotNull null
                ChartEntry(
                    timestamp = t,
                    open   = bar.get("o")?.asDouble ?: 0.0,
                    high   = bar.get("h")?.asDouble ?: 0.0,
                    low    = bar.get("l")?.asDouble ?: 0.0,
                    close  = bar.get("c")?.asDouble ?: 0.0,
                    volume = bar.get("v")?.asLong   ?: 0L
                )
            }

            if (entries.isNotEmpty()) {
                chartCache[cacheKey] = entries
                // Back-fill weekly/monthly into the commodity cache
                val latest    = entries.last().close
                val wklyPct   = pctChange(latest, entries.getOrNull(entries.size - 6)?.close  ?: latest)
                val monthlyPct = pctChange(latest, entries.getOrNull(entries.size - 23)?.close ?: latest)
                commodityCache[type]?.commodity?.let { c ->
                    commodityCache[type] = CommodityCacheEntry(
                        c.copy(weeklyChange = wklyPct, monthlyChange = monthlyPct)
                    )
                }
                Resource.Success(entries)
            } else {
                Resource.Error("No valid data points for ${type.displayName}")
            }
        } catch (e: Exception) {
            chartCache[cacheKey]?.let { Resource.Success(it) }
                ?: Resource.Error(e.message ?: "Failed to fetch history")
        }
    }

    suspend fun getCommodityPerformances(): Resource<List<CommodityPerformance>> = withContext(Dispatchers.IO) {
        val performances = mutableListOf<CommodityPerformance>()
        for (type in CommodityType.entries) {
            val history = getCommodityHistory(type, "daily")
            if (history is Resource.Success && history.data != null && history.data.isNotEmpty()) {
                val e = history.data
                val latest = e.last().close
                performances.add(CommodityPerformance(
                    type               = type,
                    dailyChangePct     = pctChange(latest, e.getOrNull(e.size - 2)?.close  ?: latest),
                    weeklyChangePct    = pctChange(latest, e.getOrNull(e.size - 6)?.close  ?: latest),
                    monthlyChangePct   = pctChange(latest, e.getOrNull(e.size - 23)?.close ?: latest),
                    quarterlyChangePct = pctChange(latest, e.getOrNull(e.size - 66)?.close ?: latest),
                    yearlyChangePct    = pctChange(latest, e.getOrNull(0)?.close            ?: latest)
                ))
            }
        }
        Resource.Success(performances)
    }

    private fun pctChange(latest: Double, old: Double): Double =
        if (old > 0) ((latest - old) / old) * 100.0 else 0.0
}
