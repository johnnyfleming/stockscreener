package com.tradescreenerai.app.data.repository

import com.tradescreenerai.app.BuildConfig
import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.remote.RetrofitClient
import com.tradescreenerai.app.util.Constants
import com.tradescreenerai.app.util.Resource
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class StockRepository {

    private val alphaVantageApi = RetrofitClient.alphaVantageApi
    private val finnhubApi      = RetrofitClient.finnhubApi
    private val polygonApi      = RetrofitClient.polygonApi
    private val yahooApi        = RetrofitClient.yahooFinanceApi
    private val apiKey          = BuildConfig.ALPHA_VANTAGE_KEY
    private val finnhubKey      = BuildConfig.FINNHUB_KEY
    private val polygonKey      = BuildConfig.POLYGON_KEY

    // In-memory cache
    private val quoteCache = mutableMapOf<String, Stock>()
    private val chartCache = mutableMapOf<String, List<ChartEntry>>()

    suspend fun getQuote(symbol: String): Resource<Stock> = withContext(Dispatchers.IO) {
        // ── 1. Yahoo Finance (free, real-time, no key) ────────────────────────
        try {
            val resp    = yahooApi.getQuotes(symbols = symbol)
            val results = resp.getAsJsonObject("quoteResponse")?.getAsJsonArray("result")
            if (results != null && results.size() > 0) {
                val stocks = parseYahooQuotes(results)
                if (stocks.isNotEmpty()) {
                    quoteCache[symbol] = stocks[0]
                    return@withContext Resource.Success(stocks[0])
                }
            }
        } catch (_: Exception) { }

        // ── 2. Finnhub (free, individual quote) ───────────────────────────────
        try {
            val response  = finnhubApi.getQuote(symbol, finnhubKey)
            val current   = response.get("c")?.asDouble  ?: 0.0
            val prevClose = response.get("pc")?.asDouble ?: 0.0
            // On weekends / after-hours, c==0; use previous close as the price
            val price = if (current > 0) current else prevClose
            if (price > 0) {
                val stock = Stock(
                    symbol        = symbol,
                    name          = symbol,
                    price         = price,
                    change        = response.get("d")?.asDouble  ?: 0.0,
                    changePercent = response.get("dp")?.asDouble ?: 0.0,
                    high          = response.get("h")?.asDouble  ?: price,
                    low           = response.get("l")?.asDouble  ?: price,
                    open          = response.get("o")?.asDouble  ?: price,
                    previousClose = prevClose
                )
                quoteCache[symbol] = stock
                return@withContext Resource.Success(stock)
            }
        } catch (_: Exception) { }

        // ── 3. Alpha Vantage fallback (25 calls/day limit) ────────────────────
        try {
            val response = alphaVantageApi.getQuote(symbol = symbol, apiKey = apiKey)
            val quote = response.getAsJsonObject("Global Quote")
            if (quote != null) {
                val price = quote.get("05. price")?.asDouble ?: 0.0
                val prev  = quote.get("08. previous close")?.asDouble ?: 0.0
                val effPrice = if (price > 0) price else prev
                if (effPrice > 0) {
                    val stock = Stock(
                        symbol        = quote.get("01. symbol")?.asString ?: symbol,
                        name          = symbol,
                        price         = effPrice,
                        change        = quote.get("09. change")?.asDouble ?: 0.0,
                        changePercent = quote.get("10. change percent")?.asString
                                            ?.replace("%", "")?.toDoubleOrNull() ?: 0.0,
                        volume        = quote.get("06. volume")?.asLong   ?: 0,
                        high          = quote.get("03. high")?.asDouble   ?: effPrice,
                        low           = quote.get("04. low")?.asDouble    ?: effPrice,
                        open          = quote.get("02. open")?.asDouble   ?: effPrice,
                        previousClose = prev
                    )
                    quoteCache[symbol] = stock
                    return@withContext Resource.Success(stock)
                }
            }
        } catch (_: Exception) { }

        // ── 4. Yahoo chart meta fallback (most robust, works weekends) ────────
        try {
            val resp   = yahooApi.getChart(symbol = symbol, interval = "1d", range = "5d")
            val meta   = resp.getAsJsonObject("chart")
                           ?.getAsJsonArray("result")
                           ?.takeIf { it.size() > 0 }
                           ?.get(0)?.asJsonObject
                           ?.getAsJsonObject("meta")
            if (meta != null) {
                val price  = meta.get("regularMarketPrice")?.asDouble  ?: 0.0
                val prev   = meta.get("chartPreviousClose")?.asDouble  ?: 0.0
                val effPrice = if (price > 0) price else prev
                if (effPrice > 0) {
                    val stock = Stock(
                        symbol        = symbol,
                        name          = meta.get("shortName")?.asString ?: symbol,
                        price         = effPrice,
                        change        = effPrice - prev,
                        changePercent = if (prev > 0) ((effPrice - prev) / prev) * 100 else 0.0,
                        previousClose = prev,
                        high          = meta.get("regularMarketDayHigh")?.asDouble ?: effPrice,
                        low           = meta.get("regularMarketDayLow")?.asDouble  ?: effPrice,
                        open          = meta.get("regularMarketOpen")?.asDouble    ?: effPrice,
                        volume        = meta.get("regularMarketVolume")?.asLong    ?: 0L
                    )
                    quoteCache[symbol] = stock
                    return@withContext Resource.Success(stock)
                }
            }
        } catch (_: Exception) { }

        // Return cached data if all sources fail
        quoteCache[symbol]?.let { return@withContext Resource.Success(it) }
        Resource.Error("No data available for $symbol")
    }

    suspend fun getCompanyOverview(symbol: String): Resource<Stock> = withContext(Dispatchers.IO) {
        try {
            val response = alphaVantageApi.getCompanyOverview(symbol = symbol, apiKey = apiKey)
            val existing = quoteCache[symbol]
            val stock = Stock(
                symbol = response.get("Symbol")?.asString ?: symbol,
                name = response.get("Name")?.asString ?: symbol,
                price = existing?.price ?: 0.0,
                change = existing?.change ?: 0.0,
                changePercent = existing?.changePercent ?: 0.0,
                volume = existing?.volume ?: 0,
                marketCap = response.get("MarketCapitalization")?.asLong ?: 0,
                pe = response.get("PERatio")?.asDouble ?: 0.0,
                dividend = response.get("DividendYield")?.asDouble ?: 0.0,
                sector = response.get("Sector")?.asString ?: "",
                exchange = response.get("Exchange")?.asString ?: "",
                description = response.get("Description")?.asString ?: "",
                high = existing?.high ?: 0.0,
                low = existing?.low ?: 0.0,
                open = existing?.open ?: 0.0,
                previousClose = existing?.previousClose ?: 0.0
            )
            quoteCache[symbol] = stock
            Resource.Success(stock)
        } catch (e: Exception) {
            quoteCache[symbol]?.let { Resource.Success(it) }
                ?: Resource.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getDailyTimeSeries(symbol: String, days: Int = 365): Resource<List<ChartEntry>> = withContext(Dispatchers.IO) {

        // ── 1. Yahoo Finance chart (FREE, no key, no strict rate limit) ────────
        try {
            val range = when {
                days <= 30  -> "1mo"
                days <= 90  -> "3mo"
                days <= 180 -> "6mo"
                days <= 365 -> "1y"
                days <= 730 -> "2y"
                else        -> "5y"
            }
            val response = yahooApi.getChart(symbol = symbol, interval = "1d", range = range)
            val entries  = parseYahooChart(response)
            if (entries.size >= 20) {
                chartCache[symbol] = entries
                return@withContext Resource.Success(entries)
            }
        } catch (_: Exception) { /* fall through */ }

        // ── 2. Polygon.io (free tier: 5 req/min, previous-day data) ──────────
        if (polygonKey != "YOUR_POLYGON_API_KEY") {
            try {
                val sdf       = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val today     = sdf.format(java.util.Date())
                val fromDate  = if (days >= 1800) "2010-01-01" else
                    sdf.format(java.util.Date(System.currentTimeMillis() - days.toLong() * 86_400_000L))

                val response = polygonApi.getAggBars(
                    ticker     = symbol,
                    multiplier = 1,
                    timespan   = "day",
                    from       = fromDate,
                    to         = today,
                    adjusted   = true,
                    sort       = "asc",
                    limit      = minOf(days, 1500),
                    apiKey     = polygonKey
                )
                val results = response.getAsJsonArray("results")
                if (results != null && results.size() > 0) {
                    val entries = (0 until results.size()).map { i ->
                        val bar = results[i].asJsonObject
                        ChartEntry(
                            timestamp = bar.get("t")?.asLong ?: 0L,
                            open      = bar.get("o")?.asDouble ?: 0.0,
                            high      = bar.get("h")?.asDouble ?: 0.0,
                            low       = bar.get("l")?.asDouble ?: 0.0,
                            close     = bar.get("c")?.asDouble ?: 0.0,
                            volume    = bar.get("v")?.asLong   ?: 0L
                        )
                    }
                    chartCache[symbol] = entries
                    return@withContext Resource.Success(entries)
                }
            } catch (_: Exception) { /* fall through to Alpha Vantage */ }
        }

        // ── 3. Alpha Vantage fallback (25 calls/day – use sparingly) ─────────
        try {
            val response   = alphaVantageApi.getTimeSeries(symbol = symbol, apiKey = apiKey)
            val timeSeries = response.getAsJsonObject("Time Series (Daily)")
            if (timeSeries != null) {
                val entries = timeSeries.entrySet().map { (dateStr, values) ->
                    val obj = values.asJsonObject
                    ChartEntry(
                        timestamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .parse(dateStr)?.time ?: 0,
                        open   = obj.get("1. open")?.asDouble  ?: 0.0,
                        high   = obj.get("2. high")?.asDouble  ?: 0.0,
                        low    = obj.get("3. low")?.asDouble   ?: 0.0,
                        close  = obj.get("4. close")?.asDouble ?: 0.0,
                        volume = obj.get("5. volume")?.asLong  ?: 0
                    )
                }.sortedBy { it.timestamp }
                chartCache[symbol] = entries
                return@withContext Resource.Success(entries)
            }
        } catch (_: Exception) { /* fall through to cache */ }

        // ── 4. Return cache if all sources failed ─────────────────────────────
        chartCache[symbol]?.let { Resource.Success(it) }
            ?: Resource.Error("Chart unavailable. Try again in a moment.")
    }

    /**
     * Parse Yahoo Finance v8/finance/chart response into ChartEntry list.
     * Timestamps are Unix seconds → multiply by 1000 for milliseconds.
     */
    private fun parseYahooChart(response: JsonObject): List<ChartEntry> {
        val result = response
            .getAsJsonObject("chart")
            ?.getAsJsonArray("result")
            ?.takeIf { it.size() > 0 }
            ?.get(0)?.asJsonObject
            ?: return emptyList()

        val timestamps = result.getAsJsonArray("timestamp") ?: return emptyList()
        val quoteArr   = result
            .getAsJsonObject("indicators")
            ?.getAsJsonArray("quote")
            ?.takeIf { it.size() > 0 }
            ?.get(0)?.asJsonObject
            ?: return emptyList()

        val opens   = quoteArr.getAsJsonArray("open")
        val highs   = quoteArr.getAsJsonArray("high")
        val lows    = quoteArr.getAsJsonArray("low")
        val closes  = quoteArr.getAsJsonArray("close")
        val volumes = quoteArr.getAsJsonArray("volume")

        val entries = mutableListOf<ChartEntry>()
        for (i in 0 until timestamps.size()) {
            val close = closes?.get(i)?.asDouble ?: continue
            if (close <= 0) continue
            entries.add(ChartEntry(
                timestamp = (timestamps[i]?.asLong ?: 0L) * 1000L,
                open      = opens?.get(i)?.asDouble   ?: close,
                high      = highs?.get(i)?.asDouble   ?: close,
                low       = lows?.get(i)?.asDouble    ?: close,
                close     = close,
                volume    = try { volumes?.get(i)?.asLong ?: 0L } catch (_: Exception) { 0L }
            ))
        }
        return entries
    }

    suspend fun getCandles(symbol: String, resolution: String, from: Long, to: Long): Resource<List<ChartEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val response = finnhubApi.getCandles(symbol, resolution, from, to, finnhubKey)
                val status = response.get("s")?.asString
                if (status == "ok") {
                    val timestamps = response.getAsJsonArray("t")
                    val opens = response.getAsJsonArray("o")
                    val highs = response.getAsJsonArray("h")
                    val lows = response.getAsJsonArray("l")
                    val closes = response.getAsJsonArray("c")
                    val volumes = response.getAsJsonArray("v")

                    val entries = (0 until timestamps.size()).map { i ->
                        ChartEntry(
                            timestamp = timestamps[i].asLong * 1000,
                            open = opens[i].asDouble,
                            high = highs[i].asDouble,
                            low = lows[i].asDouble,
                            close = closes[i].asDouble,
                            volume = volumes[i].asLong
                        )
                    }
                    Resource.Success(entries)
                } else {
                    Resource.Error("No candle data available")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Unknown error")
            }
        }

    suspend fun searchSymbols(query: String): Resource<List<Stock>> = withContext(Dispatchers.IO) {
        try {
            val response = alphaVantageApi.getSearchResults(keywords = query, apiKey = apiKey)
            val matches = response.getAsJsonArray("bestMatches")
            if (matches != null) {
                val results = (0 until matches.size()).map { i ->
                    val item = matches[i].asJsonObject
                    Stock(
                        symbol = item.get("1. symbol")?.asString ?: "",
                        name = item.get("2. name")?.asString ?: "",
                        price = 0.0,
                        change = 0.0,
                        changePercent = 0.0,
                        exchange = item.get("4. region")?.asString ?: ""
                    )
                }
                Resource.Success(results)
            } else {
                Resource.Success(emptyList())
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getTopGainersLosers(): Resource<Pair<List<Stock>, List<Stock>>> = withContext(Dispatchers.IO) {
        try {
            val response = alphaVantageApi.getTopGainersLosers(apiKey = apiKey)
            val gainers = parseStockList(response.getAsJsonArray("top_gainers"))
            val losers = parseStockList(response.getAsJsonArray("top_losers"))
            Resource.Success(Pair(gainers, losers))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getSocialSentiment(symbol: String): Resource<SentimentData> = withContext(Dispatchers.IO) {
        try {
            val response = finnhubApi.getSocialSentiment(symbol = symbol, token = finnhubKey)
            val reddit = response.getAsJsonArray("reddit")
            val twitter = response.getAsJsonArray("twitter")
            var totalMentions = 0
            var positiveScore = 0.0
            var count = 0

            listOf(reddit, twitter).forEach { source ->
                source?.forEach { item ->
                    val obj = item.asJsonObject
                    totalMentions += obj.get("mention")?.asInt ?: 0
                    positiveScore += obj.get("positiveScore")?.asDouble ?: 0.5
                    count++
                }
            }

            val avgPositive = if (count > 0) positiveScore / count * 100 else 50.0
            Resource.Success(
                SentimentData(
                    bullishPercent = avgPositive,
                    bearishPercent = 100 - avgPositive,
                    totalMentions = totalMentions,
                    trendingScore = totalMentions.toDouble()
                )
            )
        } catch (e: Exception) {
            Resource.Success(SentimentData()) // Default neutral sentiment
        }
    }

    /**
     * Fetches actively traded small-cap / penny stocks (price $0.01–maxPrice).
     *
     * PRIMARY  → Yahoo Finance /v7/finance/quote on PENNY_STOCK_UNIVERSE
     *            (chunks of 20 with 200ms delays to avoid rate-limiting)
     *            Volume filter is intentionally OMITTED here — regularMarketVolume
     *            returns 0 on weekends/holidays, which would filter out every stock.
     *            The ScreenerViewModel's applyFilters() handles volume filtering via
     *            its own minVolumeFilter slider (default 0 = no filter).
     *
     * FALLBACK → RANKING_STOCK_POOL quotes filtered by price < maxPrice.
     *            These are guaranteed to be active, tradeable stocks so this
     *            path always produces results even when penny stocks are unavailable.
     *
     * LAST     → Finnhub parallel quotes for PENNY_STOCK_UNIVERSE.
     */
    suspend fun getPennyStocks(
        maxPrice: Double = 5.0,
        minVolume: Long  = 0L,   // NOT used in filtering here; caller's UI handles it
        limit: Int       = 150
    ): Resource<List<Stock>> = withContext(Dispatchers.IO) {

        val allStocks = mutableListOf<Stock>()

        // ── 1. Yahoo Finance batch quotes — chunks of 20 with small delay ─────
        for (chunk in Constants.PENNY_STOCK_UNIVERSE.chunked(20)) {
            try {
                val resp    = yahooApi.getQuotes(symbols = chunk.joinToString(","))
                val results = resp.getAsJsonObject("quoteResponse")?.getAsJsonArray("result")
                if (results != null && results.size() > 0) {
                    allStocks.addAll(parseYahooQuotes(results))
                }
            } catch (_: Exception) { /* skip chunk, try next */ }
            delay(200L) // avoid Yahoo Finance rate-limiting
        }

        // Filter by price only — no volume check (volume = 0 on weekends)
        val pennyResults = allStocks
            .distinctBy { it.symbol }
            .filter { it.price in 0.01..maxPrice }
            .sortedByDescending { it.changePercent }
            .take(limit)

        if (pennyResults.isNotEmpty()) {
            pennyResults.forEach { quoteCache[it.symbol] = it }
            return@withContext Resource.Success(pennyResults)
        }

        // ── 2. Guaranteed fallback: RANKING_STOCK_POOL — no price filter ─────────
        //    Returns whatever stocks we can fetch. Price filtering happens in the
        //    ScreenerViewModel's applyFilters() so the screener ALWAYS has data.
        try {
            val result = getBatchQuotes(Constants.RANKING_STOCK_POOL.take(50))
            val rankStocks = (result as? Resource.Success)?.data ?: emptyList()
            val sorted = rankStocks
                .filter { it.price > 0 }
                .sortedByDescending { it.changePercent }
                .take(limit)
            if (sorted.isNotEmpty()) {
                sorted.forEach { quoteCache[it.symbol] = it }
                return@withContext Resource.Success(sorted)
            }
        } catch (_: Exception) { }

        // ── 3. Yahoo Finance screeners (try both known response shapes) ───────
        val screenerStocks = mutableListOf<Stock>()
        for (scrId in listOf("aggressive_small_caps", "small_cap_gainers", "day_gainers", "most_actives")) {
            try {
                val resp   = yahooApi.getScreener(scrId = scrId, count = 100)
                val quotes = resp.getAsJsonObject("finance")
                    ?.getAsJsonArray("result")?.takeIf { it.size() > 0 }
                    ?.get(0)?.asJsonObject?.getAsJsonArray("quotes")
                    ?: resp.getAsJsonObject("quoteResponse")?.getAsJsonArray("result")
                if (quotes != null) screenerStocks.addAll(parseYahooQuotes(quotes))
            } catch (_: Exception) { }
        }
        val screenerFiltered = screenerStocks
            .distinctBy { it.symbol }
            .filter { it.price in 0.01..maxPrice }
            .sortedByDescending { it.volume }
            .take(limit)
        if (screenerFiltered.isNotEmpty()) {
            screenerFiltered.forEach { quoteCache[it.symbol] = it }
            return@withContext Resource.Success(screenerFiltered)
        }

        // ── 4. Last resort: Finnhub parallel quotes ───────────────────────────
        try {
            val deferreds = coroutineScope {
                Constants.PENNY_STOCK_UNIVERSE.map { sym ->
                    async {
                        try {
                            val r = finnhubApi.getQuote(sym, finnhubKey)
                            val current   = r.get("c")?.asDouble  ?: 0.0
                            val prevClose = r.get("pc")?.asDouble ?: 0.0
                            val price     = if (current > 0) current else prevClose
                            if (price <= 0 || price > maxPrice) null
                            else Stock(
                                symbol        = sym, name = sym,
                                price         = price,
                                change        = r.get("d")?.asDouble  ?: 0.0,
                                changePercent = r.get("dp")?.asDouble ?: 0.0,
                                high          = r.get("h")?.asDouble  ?: price,
                                low           = r.get("l")?.asDouble  ?: price,
                                open          = r.get("o")?.asDouble  ?: price,
                                previousClose = prevClose,
                                volume        = 0L
                            )
                        } catch (_: Exception) { null }
                    }
                }
            }
            val finnhubResults = deferreds.awaitAll().filterNotNull()
                .sortedByDescending { it.changePercent }.take(limit)
            if (finnhubResults.isNotEmpty()) {
                finnhubResults.forEach { quoteCache[it.symbol] = it }
                return@withContext Resource.Success(finnhubResults)
            }
        } catch (_: Exception) { }

        Resource.Error(
            "Could not load stock data right now.\n" +
            "Check your internet connection and try again."
        )
    }

    /**
     * Parse a Yahoo Finance quote JsonArray (from screener or /v7/finance/quote)
     * into a list of [Stock] objects.
     * When regularMarketPrice == 0 (market closed/weekend), falls back to
     * regularMarketPreviousClose so stocks still appear.
     */
    private fun parseYahooQuotes(quotes: com.google.gson.JsonArray): List<Stock> {
        val result = mutableListOf<Stock>()
        for (i in 0 until quotes.size()) {
            try {
                val q         = quotes[i].asJsonObject
                val symbol    = q.get("symbol")?.asString              ?: continue
                val name      = q.get("shortName")?.asString
                             ?: q.get("longName")?.asString
                             ?: symbol
                val rawPrice  = q.get("regularMarketPrice")?.asDouble  ?: 0.0
                val prevClose = q.get("regularMarketPreviousClose")?.asDouble ?: 0.0
                // Use previous close as fallback when market is closed (e.g. weekends)
                val price     = if (rawPrice > 0) rawPrice else prevClose
                val change    = q.get("regularMarketChange")?.asDouble ?: 0.0
                val changePct = q.get("regularMarketChangePercent")?.asDouble ?: 0.0
                val volume    = q.get("regularMarketVolume")?.asLong   ?: 0L
                val open      = q.get("regularMarketOpen")?.asDouble   ?: price
                val high      = q.get("regularMarketDayHigh")?.asDouble ?: price
                val low       = q.get("regularMarketDayLow")?.asDouble  ?: price
                val marketCap = q.get("marketCap")?.asLong             ?: 0L

                if (price <= 0) continue

                result.add(Stock(
                    symbol        = symbol,
                    name          = name,
                    price         = price,
                    change        = change,
                    changePercent = changePct,
                    volume        = volume,
                    open          = open,
                    high          = high,
                    low           = low,
                    previousClose = prevClose,
                    marketCap     = marketCap
                ))
            } catch (_: Exception) { /* skip malformed entry */ }
        }
        return result
    }

    private fun parseStockList(array: JsonArray?): List<Stock> {
        if (array == null) return emptyList()
        return (0 until array.size()).map { i ->
            val item = array[i].asJsonObject
            Stock(
                symbol = item.get("ticker")?.asString ?: "",
                name = item.get("ticker")?.asString ?: "",
                price = item.get("price")?.asDouble ?: 0.0,
                change = item.get("change_amount")?.asDouble ?: 0.0,
                changePercent = item.get("change_percentage")?.asString
                    ?.replace("%", "")?.toDoubleOrNull() ?: 0.0,
                volume = item.get("volume")?.asLong ?: 0
            )
        }
    }

    /**
     * Batch-fetch quotes for multiple symbols.
     *
     * PRIMARY   → Yahoo Finance /v7/finance/quote (chunks of 20, free, no key)
     * FALLBACK1 → Polygon.io snapshots (250 tickers/call; prevDay used when market closed)
     * FALLBACK2 → Finnhub individual quotes in parallel (uses pc when c==0, i.e. market closed)
     * FALLBACK3 → Yahoo Finance /v8/finance/chart meta (most robust, one call per symbol)
     * FALLBACK4 → In-memory cache
     */
    suspend fun getBatchQuotes(symbols: List<String>): Resource<List<Stock>> = withContext(Dispatchers.IO) {
        if (symbols.isEmpty()) return@withContext Resource.Success(emptyList())

        // ── 1. Yahoo Finance batch quotes (chunks of 20 to avoid blocking) ───
        try {
            val allYahoo = mutableListOf<Stock>()
            for (chunk in symbols.chunked(20)) {
                try {
                    val symbolStr = chunk.joinToString(",")
                    val resp = yahooApi.getQuotes(symbols = symbolStr)
                    val results = resp.getAsJsonObject("quoteResponse")?.getAsJsonArray("result")
                    if (results != null && results.size() > 0) {
                        allYahoo.addAll(parseYahooQuotes(results))
                    }
                } catch (_: Exception) { /* skip this chunk */ }
            }
            if (allYahoo.size >= symbols.size / 3) {       // got at least 1/3 of requested symbols
                allYahoo.forEach { quoteCache[it.symbol] = it }
                return@withContext Resource.Success(allYahoo)
            }
        } catch (_: Exception) { /* fall through to Polygon */ }

        // ── 2. Polygon snapshots (uses prevDay when today has no trading) ────
        if (polygonKey != "YOUR_POLYGON_API_KEY") {
            try {
                val chunks = symbols.chunked(250)
                val allStocks = mutableListOf<Stock>()
                for (chunk in chunks) {
                    try {
                        val tickerStr = chunk.joinToString(",")
                        val response = polygonApi.getSnapshots(tickers = tickerStr, apiKey = polygonKey)
                        val tickers = response.getAsJsonArray("tickers")
                        if (tickers != null) {
                            for (i in 0 until tickers.size()) {
                                try {
                                    val item    = tickers[i].asJsonObject
                                    val ticker  = item.get("ticker")?.asString ?: continue
                                    // Prefer today's data; use prevDay when market is closed
                                    val day     = item.getAsJsonObject("day")
                                    val prevDay = item.getAsJsonObject("prevDay")
                                    val src     = day?.takeIf { it.get("c")?.asDouble ?: 0.0 > 0 }
                                                  ?: prevDay ?: continue
                                    val close   = src.get("c")?.asDouble ?: 0.0
                                    if (close <= 0) continue
                                    val open      = src.get("o")?.asDouble  ?: close
                                    val high      = src.get("h")?.asDouble  ?: close
                                    val low       = src.get("l")?.asDouble  ?: close
                                    val volume    = src.get("v")?.asLong    ?: 0L
                                    val prevClose = prevDay?.get("c")?.asDouble?.takeIf { it > 0 } ?: open
                                    val change    = close - prevClose
                                    val changePct = if (prevClose > 0) (change / prevClose) * 100.0 else 0.0
                                    val stock = Stock(
                                        symbol        = ticker,
                                        name          = ticker,
                                        price         = close,
                                        change        = change,
                                        changePercent = changePct,
                                        volume        = volume,
                                        high          = high,
                                        low           = low,
                                        open          = open,
                                        previousClose = prevClose
                                    )
                                    allStocks.add(stock)
                                    quoteCache[ticker] = stock
                                } catch (_: Exception) { /* skip bad entry */ }
                            }
                        }
                    } catch (_: Exception) { /* skip this chunk */ }
                }
                if (allStocks.size >= symbols.size / 3) return@withContext Resource.Success(allStocks)
            } catch (_: Exception) { /* fall through */ }
        }

        // ── 3. Finnhub parallel quotes (pc used as fallback when market closed) ─
        try {
            val finnhubStocks: List<Stock> = coroutineScope {
                symbols.take(60).map { sym ->
                    async {
                        try {
                            val r          = finnhubApi.getQuote(sym, finnhubKey)
                            val current    = r.get("c")?.asDouble  ?: 0.0
                            val prevClose  = r.get("pc")?.asDouble ?: 0.0
                            // When market is closed, c==0; use previous close as price
                            val price      = if (current > 0) current else prevClose
                            if (price <= 0) return@async null
                            Stock(
                                symbol        = sym,
                                name          = sym,
                                price         = price,
                                change        = r.get("d")?.asDouble  ?: 0.0,
                                changePercent = r.get("dp")?.asDouble ?: 0.0,
                                high          = r.get("h")?.asDouble  ?: price,
                                low           = r.get("l")?.asDouble  ?: price,
                                open          = r.get("o")?.asDouble  ?: price,
                                previousClose = prevClose
                            )
                        } catch (_: Exception) { null }
                    }
                }.awaitAll().filterNotNull()
            }
            if (finnhubStocks.isNotEmpty()) {
                finnhubStocks.forEach { quoteCache[it.symbol] = it }
                return@withContext Resource.Success(finnhubStocks)
            }
        } catch (_: Exception) { /* fall through */ }

        // ── 4. Yahoo Finance chart meta fallback (most robust, per-symbol) ───
        //    Extracts price from chart meta; works even when v7/quote is blocked.
        try {
            val chartStocks: List<Stock> = coroutineScope {
                symbols.take(30).map { sym ->
                    async {
                        try {
                            val resp   = yahooApi.getChart(symbol = sym, interval = "1d", range = "5d")
                            val meta   = resp.getAsJsonObject("chart")
                                           ?.getAsJsonArray("result")
                                           ?.takeIf { it.size() > 0 }
                                           ?.get(0)?.asJsonObject
                                           ?.getAsJsonObject("meta")
                                           ?: return@async null
                            val price  = meta.get("regularMarketPrice")?.asDouble  ?: 0.0
                            val prev   = meta.get("chartPreviousClose")?.asDouble  ?: 0.0
                            val effPrice = if (price > 0) price else prev
                            if (effPrice <= 0) return@async null
                            val name   = meta.get("shortName")?.asString ?: sym
                            Stock(
                                symbol        = sym,
                                name          = name,
                                price         = effPrice,
                                change        = effPrice - prev,
                                changePercent = if (prev > 0) ((effPrice - prev) / prev) * 100 else 0.0,
                                previousClose = prev
                            )
                        } catch (_: Exception) { null }
                    }
                }.awaitAll().filterNotNull()
            }
            if (chartStocks.isNotEmpty()) {
                chartStocks.forEach { quoteCache[it.symbol] = it }
                return@withContext Resource.Success(chartStocks)
            }
        } catch (_: Exception) { /* fall through */ }

        // ── 5. Return whatever is in cache ────────────────────────────────────
        val cached = symbols.mapNotNull { quoteCache[it] }
        if (cached.isNotEmpty()) return@withContext Resource.Success(cached)

        Resource.Error("Unable to load stock quotes. Check your internet connection.")
    }

    /**
     * Fetch intraday bars using Polygon for short-term charting.
     * @param interval "1" for 1-minute, "5" for 5-minute, etc.
     */
    suspend fun getIntradayBars(
        symbol: String,
        intervalMinutes: Int = 5,
        daysBack: Int = 1
    ): Resource<List<ChartEntry>> = withContext(Dispatchers.IO) {
        if (polygonKey != "YOUR_POLYGON_API_KEY") {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val today = sdf.format(java.util.Date())
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.DAY_OF_YEAR, -daysBack)
                val from = sdf.format(cal.time)

                val response = polygonApi.getAggBars(
                    ticker = symbol,
                    multiplier = intervalMinutes,
                    timespan = "minute",
                    from = from,
                    to = today,
                    adjusted = true,
                    sort = "asc",
                    limit = 500,
                    apiKey = polygonKey
                )
                val results = response.getAsJsonArray("results")
                if (results != null && results.size() > 0) {
                    val entries = (0 until results.size()).map { i ->
                        val bar = results[i].asJsonObject
                        ChartEntry(
                            timestamp = bar.get("t")?.asLong ?: 0L,
                            open = bar.get("o")?.asDouble ?: 0.0,
                            high = bar.get("h")?.asDouble ?: 0.0,
                            low = bar.get("l")?.asDouble ?: 0.0,
                            close = bar.get("c")?.asDouble ?: 0.0,
                            volume = bar.get("v")?.asLong ?: 0L
                        )
                    }
                    return@withContext Resource.Success(entries)
                }
            } catch (_: Exception) { /* fall through */ }
        }

        // Fallback: Finnhub candles
        try {
            val to = System.currentTimeMillis() / 1000
            val from = to - (daysBack * 86400L)
            val resolution = when {
                intervalMinutes <= 1 -> "1"
                intervalMinutes <= 5 -> "5"
                intervalMinutes <= 15 -> "15"
                intervalMinutes <= 30 -> "30"
                else -> "60"
            }
            return@withContext getCandles(symbol, resolution, from, to)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get intraday data")
        }
    }
}
