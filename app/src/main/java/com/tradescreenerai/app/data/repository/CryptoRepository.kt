package com.tradescreenerai.app.data.repository

import com.tradescreenerai.app.data.model.*
import com.tradescreenerai.app.data.remote.RetrofitClient
import com.tradescreenerai.app.util.Constants
import com.tradescreenerai.app.util.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/** Which data source is currently in use – exposed so the UI can show a notice. */
enum class CryptoDataSource { COINGECKO, COINPAPRIKA }

class CryptoRepository {

    private val coinGeckoApi  = RetrofitClient.coinGeckoApi
    private val coinPaprikaApi = RetrofitClient.coinPaprikaApi
    private val coinCapApi     = RetrofitClient.coinCapApi
    private val binanceApi     = RetrofitClient.binanceApi

    companion object {
        private val cache = ConcurrentHashMap<String, Crypto>()
        private val paprikaIdCache = ConcurrentHashMap<String, String>()

        /** Timestamp until which we skip CoinGecko and use CoinPaprika instead. */
        @Volatile private var rateLimitedUntil: Long = 0L

        @Volatile private var sharedActiveDataSource: CryptoDataSource = CryptoDataSource.COINGECKO
    }

    /** Currently active data source (readable by the ViewModel / UI). */
    var activeDataSource: CryptoDataSource
        get() = sharedActiveDataSource
        private set(value) {
            sharedActiveDataSource = value
        }

    private fun isCoinGeckoBlocked() = System.currentTimeMillis() < rateLimitedUntil

    private fun markCoinGeckoRateLimited() {
        rateLimitedUntil = System.currentTimeMillis() + Constants.RATE_LIMIT_BACKOFF_MS
        activeDataSource  = CryptoDataSource.COINPAPRIKA
    }

    private fun normalizeLookupKey(value: String): String = value
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private fun compactLookupKey(value: String): String = value
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "")

    private fun cacheCrypto(vararg ids: String, crypto: Crypto) {
        ids.asSequence()
            .map(::normalizeLookupKey)
            .filter { it.isNotEmpty() }
            .forEach { cache[it] = crypto }
    }

    private fun getCachedCrypto(id: String): Crypto? = cache[normalizeLookupKey(id)]

    private fun cachedCryptos(): List<Crypto> = cache.values.distinctBy { it.id }

    private fun rememberPaprikaId(appId: String, paprikaId: String?) {
        val normalizedAppId = normalizeLookupKey(appId)
        val normalizedPaprikaId = paprikaId?.let(::normalizeLookupKey).orEmpty()
        if (normalizedAppId.isNotEmpty() && normalizedPaprikaId.isNotEmpty()) {
            paprikaIdCache[normalizedAppId] = normalizedPaprikaId
        }
    }

    private fun rememberPaprikaAliases(paprikaId: String, name: String, symbol: String) {
        val normalizedPaprikaId = normalizeLookupKey(paprikaId)
        if (normalizedPaprikaId.isEmpty()) return

        listOf(
            paprikaId,
            paprikaId.substringAfter('-', paprikaId),
            name,
            symbol,
            "$symbol $name"
        ).forEach { alias ->
            rememberPaprikaId(alias, normalizedPaprikaId)
        }
    }

    private fun paprikaMatchScore(
        paprikaId: String,
        name: String,
        symbol: String,
        lookupKeys: Set<String>,
        compactLookupKeys: Set<String>
    ): Int {
        val normalizedPaprikaId = normalizeLookupKey(paprikaId)
        val normalizedPaprikaSlug = normalizeLookupKey(paprikaId.substringAfter('-', paprikaId))
        val normalizedName = normalizeLookupKey(name)
        val normalizedSymbol = normalizeLookupKey(symbol)
        val compactPaprikaId = compactLookupKey(paprikaId)
        val compactPaprikaSlug = compactLookupKey(paprikaId.substringAfter('-', paprikaId))
        val compactName = compactLookupKey(name)
        val compactSymbol = compactLookupKey(symbol)

        return when {
            normalizedPaprikaId in lookupKeys || compactPaprikaId in compactLookupKeys -> 100
            normalizedPaprikaSlug in lookupKeys || compactPaprikaSlug in compactLookupKeys -> 95
            normalizedName in lookupKeys || compactName in compactLookupKeys -> 90
            normalizedSymbol in lookupKeys || compactSymbol in compactLookupKeys -> 75
            else -> 0
        }
    }

    private fun findPaprikaIdInArray(
        currencies: com.google.gson.JsonArray?,
        lookupKeys: Set<String>,
        compactLookupKeys: Set<String>
    ): String? {
        if (currencies == null || currencies.size() == 0) return null

        return (0 until currencies.size())
            .mapNotNull { index ->
                val item = currencies[index].asJsonObject
                val paprikaId = item.get("id")?.asString ?: return@mapNotNull null
                val name = item.get("name")?.asString.orEmpty()
                val symbol = item.get("symbol")?.asString.orEmpty()
                val score = paprikaMatchScore(paprikaId, name, symbol, lookupKeys, compactLookupKeys)
                if (score <= 0) return@mapNotNull null
                score to paprikaId
            }
            .maxByOrNull { it.first }
            ?.second
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CoinPaprika helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun paprikaImageUrl(id: String) =
        "https://static.coinpaprika.com/coin/$id/logo.png"

    /**
     * Resolves the CoinPaprika coin ID from a CoinGecko coin ID.
     * Priority order:
     *   1. Cache hit → derive from symbol + name (e.g. "eth-ethereum")
     *   2. CoinPaprika /search endpoint
     *   3. Fall back to the raw [geckoId] (last resort, may fail)
     */
    private suspend fun resolvePaprikaId(geckoId: String): String {
        val normalizedGeckoId = normalizeLookupKey(geckoId)

        paprikaIdCache[normalizedGeckoId]?.let { return it }

        // 1. Try cache
        cache[normalizedGeckoId]?.let { c ->
            val derivedId = normalizeLookupKey("${c.symbol}-${c.name}")
            rememberPaprikaId(geckoId, derivedId)
            rememberPaprikaAliases(derivedId, c.name, c.symbol)
            return derivedId
        }

        // 2. Try CoinPaprika search
        val candidateQueries = linkedSetOf(
            geckoId,
            geckoId.replace('-', ' '),
            geckoId.substringAfterLast('-')
        ).filter { it.isNotBlank() }

        val lookupKeys = candidateQueries.map(::normalizeLookupKey).toSet() + normalizedGeckoId
        val compactLookupKeys = candidateQueries.map(::compactLookupKey).toSet() + compactLookupKey(geckoId)

        candidateQueries.forEach { query ->
            try {
                val result = coinPaprikaApi.search(query, limit = 10)
                val paprikaId = findPaprikaIdInArray(
                    currencies = result.getAsJsonArray("currencies"),
                    lookupKeys = lookupKeys,
                    compactLookupKeys = compactLookupKeys
                ) ?: return@forEach

                rememberPaprikaId(geckoId, paprikaId)
                val currencies = result.getAsJsonArray("currencies")
                val matched = if (currencies == null) null else {
                    (0 until currencies.size())
                        .map { currencies[it].asJsonObject }
                        .firstOrNull { it.get("id")?.asString == paprikaId }
                }
                rememberPaprikaAliases(
                    paprikaId = paprikaId,
                    name = matched?.get("name")?.asString.orEmpty(),
                    symbol = matched?.get("symbol")?.asString.orEmpty()
                )
                return paprikaId
            } catch (_: Exception) {
                // Search is best-effort; we'll try the broader ticker list next.
            }
        }

        // 3. Fall back to the public ticker list when search misses.
        return try {
            val tickers = coinPaprikaApi.getTickers()
            val paprikaId = findPaprikaIdInArray(tickers, lookupKeys, compactLookupKeys) ?: geckoId
            if (paprikaId != geckoId) {
                rememberPaprikaId(geckoId, paprikaId)
                val matched = (0 until tickers.size())
                    .map { tickers[it].asJsonObject }
                    .firstOrNull { it.get("id")?.asString == paprikaId }
                rememberPaprikaAliases(
                    paprikaId = paprikaId,
                    name = matched?.get("name")?.asString.orEmpty(),
                    symbol = matched?.get("symbol")?.asString.orEmpty()
                )
            }
            paprikaId
        } catch (_: Exception) {
            geckoId
        }
    }

    /** Convert CoinGecko "days" param to a start-date string (yyyy-MM-dd) for CoinPaprika. */
    private fun daysToStartDate(days: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        if (days == "max") return "2009-01-03"
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(days.toIntOrNull() ?: 30))
        return sdf.format(cal.time)
    }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /**
     * Resolves a CoinCap asset id from a CoinGecko / CoinPaprika id.
     * CoinCap uses simple slug ids like "bitcoin", "ethereum" which are
     * usually the same as CoinGecko ids. For CoinPaprika ids like
     * "btc-bitcoin", the part after the first hyphen is typically the slug.
     */
    private fun resolveCoinCapId(id: String): String {
        // If the id looks like a CoinPaprika id (e.g. "btc-bitcoin"), extract the slug
        val parts = id.split("-", limit = 2)
        if (parts.size == 2 && parts[0].length <= 5) {
            return parts[1]  // "btc-bitcoin" -> "bitcoin"
        }
        return id  // CoinGecko ids are already the right format
    }

    /**
     * Picks the best CoinCap interval for the requested number of days
     * so the chart has a reasonable number of data points.
     */
    private fun daysToInterval(days: String): String = when {
        days == "max" -> "d1"
        (days.toIntOrNull() ?: 30) <= 1   -> "m15"
        (days.toIntOrNull() ?: 30) <= 7   -> "h1"
        (days.toIntOrNull() ?: 30) <= 30  -> "h2"
        (days.toIntOrNull() ?: 30) <= 90  -> "h12"
        else -> "d1"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMarkets
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun getMarkets(
        page: Int = 1,
        perPage: Int = 50
    ): Resource<List<Crypto>> = withContext(Dispatchers.IO) {

        // ── Try CoinGecko unless it's still rate-limited ──────────────────────
        if (!isCoinGeckoBlocked()) {
            try {
                val response = coinGeckoApi.getMarkets(page = page, perPage = perPage)
                val cryptos = (0 until response.size()).map { i ->
                    val item = response[i].asJsonObject
                    val sparklineObj = item.getAsJsonObject("sparkline_in_7d")
                    val sparkline = sparklineObj?.getAsJsonArray("price")?.let { arr ->
                        (0 until arr.size()).map { j -> arr[j].asDouble }
                    } ?: emptyList()

                    Crypto(
                        id = item.get("id")?.asString ?: "",
                        symbol = item.get("symbol")?.asString?.uppercase() ?: "",
                        name = item.get("name")?.asString ?: "",
                        price = item.get("current_price")?.asDouble ?: 0.0,
                        change24h = item.get("price_change_24h")?.asDouble ?: 0.0,
                        changePercent24h = item.get("price_change_percentage_24h")?.asDouble ?: 0.0,
                        marketCap = item.get("market_cap")?.asLong ?: 0,
                        volume24h = item.get("total_volume")?.asLong ?: 0,
                        high24h = item.get("high_24h")?.asDouble ?: 0.0,
                        low24h = item.get("low_24h")?.asDouble ?: 0.0,
                        circulatingSupply = item.get("circulating_supply")?.asDouble ?: 0.0,
                        totalSupply = item.get("total_supply")?.asDouble ?: 0.0,
                        maxSupply = if (item.get("max_supply")?.isJsonNull == false)
                            item.get("max_supply")?.asDouble else null,
                        rank = item.get("market_cap_rank")?.asInt ?: 0,
                        imageUrl = item.get("image")?.asString ?: "",
                        sparkline = sparkline,
                        ath = item.get("ath")?.asDouble ?: 0.0,
                        athChangePercent = item.get("ath_change_percentage")?.asDouble ?: 0.0
                    ).also { cacheCrypto(it.id, it.name, it.symbol, crypto = it) }
                }
                activeDataSource = CryptoDataSource.COINGECKO
                return@withContext Resource.Success(cryptos)

            } catch (e: HttpException) {
                if (e.code() == 429) {
                    // Rate limited – switch to CoinPaprika for the next 90 s
                    markCoinGeckoRateLimited()
                } else {
                    val cached = cachedCryptos()
                    return@withContext if (cached.isNotEmpty()) Resource.Success(cached)
                    else Resource.Error("CoinGecko error ${e.code()}: ${e.message()}")
                }
            } catch (e: Exception) {
                val cached = cachedCryptos()
                return@withContext if (cached.isNotEmpty()) Resource.Success(cached)
                else Resource.Error(e.message ?: "Unknown error")
            }
        }

        // ── Fallback: CoinPaprika ─────────────────────────────────────────────
        return@withContext getMarketsFromCoinPaprika(perPage)
    }

    /** Fetches the top [limit] coins from CoinPaprika (sorted by rank). */
    private suspend fun getMarketsFromCoinPaprika(limit: Int): Resource<List<Crypto>> {
        return try {
            val response = coinPaprikaApi.getTickers()
            val cryptos = (0 until minOf(response.size(), limit)).mapNotNull { i ->
                val item = response[i].asJsonObject
                val usd = item.getAsJsonObject("quotes")?.getAsJsonObject("USD")
                    ?: return@mapNotNull null
                val paprikaId = item.get("id")?.asString ?: return@mapNotNull null
                val name = item.get("name")?.asString ?: ""
                val symbol = item.get("symbol")?.asString?.uppercase() ?: ""
                rememberPaprikaAliases(paprikaId, name, symbol)
                // Preserve cached sparkline from CoinGecko so charts survive the switch
                val cachedSparkline = getCachedCrypto(paprikaId)?.sparkline
                    ?: getCachedCrypto(name)?.sparkline
                    ?: getCachedCrypto(symbol)?.sparkline
                    ?: emptyList()
                val cachedImage = getCachedCrypto(paprikaId)?.imageUrl
                    ?: getCachedCrypto(name)?.imageUrl
                    ?: getCachedCrypto(symbol)?.imageUrl
                    ?: ""

                Crypto(
                    id = paprikaId,
                    symbol = symbol,
                    name = name,
                    price = usd.get("price")?.asDouble ?: 0.0,
                    change24h = 0.0,
                    changePercent24h = usd.get("percent_change_24h")?.asDouble ?: 0.0,
                    marketCap = usd.get("market_cap")?.asLong ?: 0,
                    volume24h = usd.get("volume_24h")?.asLong ?: 0,
                    high24h = 0.0,
                    low24h = 0.0,
                    circulatingSupply = item.get("circulating_supply")?.asDouble ?: 0.0,
                    totalSupply = item.get("total_supply")?.asDouble ?: 0.0,
                    maxSupply = if (item.get("max_supply")?.isJsonNull == false)
                        item.get("max_supply")?.asDouble else null,
                    rank = item.get("rank")?.asInt ?: 0,
                    imageUrl = paprikaImageUrl(paprikaId).ifEmpty { cachedImage },
                    sparkline = cachedSparkline,
                    ath = usd.get("ath_price")?.asDouble ?: 0.0,
                    athChangePercent = usd.get("percent_from_price_ath")?.asDouble ?: 0.0
                ).also { cacheCrypto(it.id, it.name, it.symbol, crypto = it) }
            }
            Resource.Success(cryptos)
        } catch (e: Exception) {
            val cached = cachedCryptos()
            if (cached.isNotEmpty()) Resource.Success(cached)
            else Resource.Error("All sources failed: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getCoinDetail
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun getCoinDetail(id: String): Resource<Crypto> = withContext(Dispatchers.IO) {

        // ── Try CoinGecko first ───────────────────────────────────────────────
        if (!isCoinGeckoBlocked()) {
            try {
                val item = coinGeckoApi.getCoinDetail(id)
                val marketData = item.getAsJsonObject("market_data")
                val currentPrice = marketData?.getAsJsonObject("current_price")
                val priceChange = marketData?.getAsJsonObject("price_change_24h_in_currency")
                val priceChangePct = marketData?.getAsJsonObject("price_change_percentage_24h_in_currency")
                val imageObj = item.getAsJsonObject("image")

                val crypto = Crypto(
                    id = item.get("id")?.asString ?: id,
                    symbol = item.get("symbol")?.asString?.uppercase() ?: "",
                    name = item.get("name")?.asString ?: "",
                    price = currentPrice?.get("usd")?.asDouble ?: 0.0,
                    change24h = priceChange?.get("usd")?.asDouble ?: 0.0,
                    changePercent24h = priceChangePct?.get("usd")?.asDouble ?: 0.0,
                    marketCap = marketData?.getAsJsonObject("market_cap")?.get("usd")?.asLong ?: 0,
                    volume24h = marketData?.getAsJsonObject("total_volume")?.get("usd")?.asLong ?: 0,
                    high24h = marketData?.getAsJsonObject("high_24h")?.get("usd")?.asDouble ?: 0.0,
                    low24h = marketData?.getAsJsonObject("low_24h")?.get("usd")?.asDouble ?: 0.0,
                    circulatingSupply = marketData?.get("circulating_supply")?.asDouble ?: 0.0,
                    totalSupply = marketData?.get("total_supply")?.asDouble ?: 0.0,
                    maxSupply = if (marketData?.get("max_supply")?.isJsonNull == false)
                        marketData.get("max_supply")?.asDouble else null,
                    rank = item.get("market_cap_rank")?.asInt ?: 0,
                    imageUrl = imageObj?.get("large")?.asString ?: "",
                    ath = marketData?.getAsJsonObject("ath")?.get("usd")?.asDouble ?: 0.0,
                    athChangePercent = marketData?.getAsJsonObject("ath_change_percentage")
                        ?.get("usd")?.asDouble ?: 0.0
                )
                cacheCrypto(crypto.id, crypto.name, crypto.symbol, crypto = crypto)
                activeDataSource = CryptoDataSource.COINGECKO
                return@withContext Resource.Success(crypto)

            } catch (e: HttpException) {
                if (e.code() == 429) markCoinGeckoRateLimited()
                else return@withContext getCachedCrypto(id)?.let { Resource.Success(it) }
                    ?: Resource.Error("CoinGecko error ${e.code()}: ${e.message()}")
            } catch (e: Exception) {
                return@withContext getCachedCrypto(id)?.let { Resource.Success(it) }
                    ?: Resource.Error(e.message ?: "Unknown error")
            }
        }

        // ── Fallback: CoinPaprika single ticker ────────────────────────────────
        // Resolve the correct CoinPaprika ID (search if not cached).
        val paprikaId = resolvePaprikaId(id)
        return@withContext try {
            val item = coinPaprikaApi.getTicker(paprikaId)
            val usd = item.getAsJsonObject("quotes")?.getAsJsonObject("USD")
            val name = item.get("name")?.asString ?: ""
            val symbol = item.get("symbol")?.asString?.uppercase() ?: ""
            rememberPaprikaId(id, paprikaId)
            rememberPaprikaAliases(paprikaId, name, symbol)
            val crypto = Crypto(
                id = id,  // keep the CoinGecko id for navigation consistency
                symbol = symbol,
                name = name,
                price = usd?.get("price")?.asDouble ?: 0.0,
                change24h = 0.0,
                changePercent24h = usd?.get("percent_change_24h")?.asDouble ?: 0.0,
                marketCap = usd?.get("market_cap")?.asLong ?: 0,
                volume24h = usd?.get("volume_24h")?.asLong ?: 0,
                high24h = 0.0,
                low24h = 0.0,
                circulatingSupply = item.get("circulating_supply")?.asDouble ?: 0.0,
                totalSupply = item.get("total_supply")?.asDouble ?: 0.0,
                maxSupply = if (item.get("max_supply")?.isJsonNull == false)
                    item.get("max_supply")?.asDouble else null,
                rank = item.get("rank")?.asInt ?: 0,
                imageUrl = paprikaImageUrl(paprikaId),
                ath = usd?.get("ath_price")?.asDouble ?: 0.0,
                athChangePercent = usd?.get("percent_from_price_ath")?.asDouble ?: 0.0
            )
            cacheCrypto(crypto.id, crypto.name, crypto.symbol, crypto = crypto)
            activeDataSource = CryptoDataSource.COINPAPRIKA
            Resource.Success(crypto)
        } catch (e: Exception) {
            getCachedCrypto(id)?.let { Resource.Success(it) }
                ?: Resource.Error("All sources failed: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Binance helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converts a CoinGecko / CoinPaprika coin id + symbol to a Binance
     * USDT spot trading-pair symbol (e.g. "bitcoin"/"BTC" → "BTCUSDT").
     */
    private fun toBinanceSymbol(geckoId: String, symbol: String): String {
        val overrides = mapOf(
            "binancecoin"  to "BNBUSDT",
            "ripple"       to "XRPUSDT",
            "avalanche-2"  to "AVAXUSDT",
            "bitcoin-cash" to "BCHUSDT",
            "wrapped-bitcoin" to "WBTCUSDT",
            "shiba-inu"    to "SHIBUSDT",
            "uniswap"      to "UNIUSDT",
            "chainlink"    to "LINKUSDT",
            "stellar"      to "XLMUSDT",
            "cosmos"       to "ATOMUSDT",
            "near"         to "NEARUSDT",
            "algorand"     to "ALGOUSDT",
            "fantom"       to "FTMUSDT"
        )
        return overrides[geckoId] ?: "${symbol.uppercase()}USDT"
    }

    /**
     * Picks the best Binance kline interval for the requested number of days.
     * Returns Pair(interval, limit).
     */
    private fun toBinanceInterval(days: String): Pair<String, Int> = when {
        days == "1"                            -> Pair("1h",  24)
        (days.toIntOrNull() ?: 30) <= 7        -> Pair("2h",  84)
        (days.toIntOrNull() ?: 30) <= 30       -> Pair("1d",  30)
        (days.toIntOrNull() ?: 30) <= 90       -> Pair("1d",  90)
        (days.toIntOrNull() ?: 30) <= 365      -> Pair("1d", 365)
        else                                    -> Pair("1w", 500)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMarketChart  (line chart price history)
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun getMarketChart(id: String, days: String = "30"): Resource<List<PricePoint>> =
        withContext(Dispatchers.IO) {

            // ── 1. CoinGecko ─────────────────────────────────────────────────
            if (!isCoinGeckoBlocked()) {
                try {
                    val response = coinGeckoApi.getMarketChart(id, days = days)
                    val prices = response.getAsJsonArray("prices")
                    if (prices != null && prices.size() > 0) {
                        val points = (0 until prices.size()).map { i ->
                            val arr = prices[i].asJsonArray
                            PricePoint(timestamp = arr[0].asLong, price = arr[1].asDouble)
                        }
                        return@withContext Resource.Success(points)
                    }
                } catch (e: HttpException) {
                    if (e.code() == 429) markCoinGeckoRateLimited()
                } catch (_: Exception) { /* fall through */ }
            }

            // ── 2. CoinPaprika OHLCV ─────────────────────────────────────────
            try {
                val paprikaId = resolvePaprikaId(id)
                val start = daysToStartDate(days)
                val end   = todayString()
                val response = coinPaprikaApi.getOhlcvHistorical(paprikaId, start, end)
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                val points = (0 until response.size()).mapNotNull { i ->
                    val candle = response[i].asJsonObject
                    val timeStr = candle.get("time_close")?.asString ?: return@mapNotNull null
                    val ts = try { isoFormat.parse(timeStr)?.time ?: 0L } catch (_: Exception) { 0L }
                    val close = candle.get("close")?.asDouble ?: return@mapNotNull null
                    PricePoint(timestamp = ts, price = close)
                }
                if (points.isNotEmpty()) return@withContext Resource.Success(points)
            } catch (_: Exception) { /* fall through */ }

            // ── 3. CoinCap ───────────────────────────────────────────────────
            try {
                val coinCapId = resolveCoinCapId(id)
                val interval  = daysToInterval(days)
                val now       = System.currentTimeMillis()
                val daysInt   = if (days == "max") 365 * 5 else (days.toIntOrNull() ?: 30)
                val startMs   = now - daysInt.toLong() * 86_400_000L
                val response  = coinCapApi.getAssetHistory(coinCapId, interval, startMs, now)
                val data      = response.getAsJsonArray("data")
                if (data != null && data.size() > 0) {
                    val points = (0 until data.size()).mapNotNull { i ->
                        val item  = data[i].asJsonObject
                        val ts    = item.get("time")?.asLong ?: return@mapNotNull null
                        val price = item.get("priceUsd")?.asString?.toDoubleOrNull() ?: return@mapNotNull null
                        PricePoint(timestamp = ts, price = price)
                    }
                    if (points.isNotEmpty()) return@withContext Resource.Success(points)
                }
            } catch (_: Exception) { /* fall through */ }

            // ── 4. Binance (free, no key, always works) ───────────────────────
            return@withContext try {
                val cachedSymbol = getCachedCrypto(id)?.symbol ?: id.uppercase()
                val binanceSymbol = toBinanceSymbol(id, cachedSymbol)
                val (interval, limit) = toBinanceInterval(days)
                val klines = binanceApi.getKlines(
                    symbol   = binanceSymbol,
                    interval = interval,
                    limit    = limit
                )
                val points = (0 until klines.size()).map { i ->
                    val arr = klines[i].asJsonArray
                    // arr[0]=openTime, arr[4]=closePrice
                    PricePoint(timestamp = arr[0].asLong, price = arr[4].asString.toDoubleOrNull() ?: 0.0)
                }
                Resource.Success(points)
            } catch (e: Exception) {
                Resource.Error("Chart unavailable: ${e.message}")
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // getCoinOhlcData  (OHLC candles)
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun getCoinOhlcData(id: String, days: String = "30"): Resource<List<ChartEntry>> =
        withContext(Dispatchers.IO) {

            // ── 1. CoinGecko ─────────────────────────────────────────────────
            if (!isCoinGeckoBlocked()) {
                try {
                    val response = coinGeckoApi.getCoinOhlc(id, days = days)
                    if (response.size() > 0) {
                        val entries = (0 until response.size()).map { i ->
                            val arr = response[i].asJsonArray
                            ChartEntry(
                                timestamp = arr[0].asLong,
                                open  = arr[1].asDouble,
                                high  = arr[2].asDouble,
                                low   = arr[3].asDouble,
                                close = arr[4].asDouble,
                                volume = 0L
                            )
                        }
                        return@withContext Resource.Success(entries)
                    }
                } catch (e: HttpException) {
                    if (e.code() == 429) markCoinGeckoRateLimited()
                } catch (_: Exception) { /* fall through */ }
            }

            // ── 2. CoinPaprika OHLCV ─────────────────────────────────────────
            try {
                val paprikaId = resolvePaprikaId(id)
                val start = daysToStartDate(days)
                val end   = todayString()
                val response = coinPaprikaApi.getOhlcvHistorical(paprikaId, start, end)
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                val entries = (0 until response.size()).mapNotNull { i ->
                    val candle = response[i].asJsonObject
                    val timeStr = candle.get("time_open")?.asString ?: return@mapNotNull null
                    val ts = try { isoFormat.parse(timeStr)?.time ?: 0L } catch (_: Exception) { 0L }
                    ChartEntry(
                        timestamp = ts,
                        open   = candle.get("open")?.asDouble  ?: 0.0,
                        high   = candle.get("high")?.asDouble  ?: 0.0,
                        low    = candle.get("low")?.asDouble   ?: 0.0,
                        close  = candle.get("close")?.asDouble ?: 0.0,
                        volume = candle.get("volume")?.asLong  ?: 0L
                    )
                }
                if (entries.isNotEmpty()) return@withContext Resource.Success(entries)
            } catch (_: Exception) { /* fall through */ }

            // ── 3. CoinCap → synthetic candles ───────────────────────────────
            try {
                val coinCapId = resolveCoinCapId(id)
                val interval  = daysToInterval(days)
                val now       = System.currentTimeMillis()
                val daysInt   = if (days == "max") 365 * 5 else (days.toIntOrNull() ?: 30)
                val startMs   = now - daysInt.toLong() * 86_400_000L
                val response  = coinCapApi.getAssetHistory(coinCapId, interval, startMs, now)
                val data      = response.getAsJsonArray("data")
                if (data != null && data.size() > 0) {
                    val dayMs = 86_400_000L
                    val entries = (0 until data.size()).mapNotNull { i ->
                        val item  = data[i].asJsonObject
                        val ts    = item.get("time")?.asLong ?: return@mapNotNull null
                        val price = item.get("priceUsd")?.asString?.toDoubleOrNull() ?: return@mapNotNull null
                        PricePoint(timestamp = ts, price = price)
                    }.groupBy { it.timestamp / dayMs }
                        .entries.sortedBy { it.key }
                        .map { (dayKey, pts) ->
                            ChartEntry(
                                timestamp = dayKey * dayMs,
                                open  = pts.first().price,
                                high  = pts.maxOf { it.price },
                                low   = pts.minOf { it.price },
                                close = pts.last().price,
                                volume = 0L
                            )
                        }
                    if (entries.isNotEmpty()) return@withContext Resource.Success(entries)
                }
            } catch (_: Exception) { /* fall through */ }

            // ── 4. Binance Klines (free, no key) – always works ───────────────
            return@withContext try {
                val cachedSymbol = getCachedCrypto(id)?.symbol ?: id.uppercase()
                val binanceSymbol = toBinanceSymbol(id, cachedSymbol)
                val (interval, limit) = toBinanceInterval(days)
                val klines = binanceApi.getKlines(
                    symbol   = binanceSymbol,
                    interval = interval,
                    limit    = limit
                )
                // Binance kline: [openTime, open, high, low, close, volume, ...]
                val entries = (0 until klines.size()).map { i ->
                    val arr = klines[i].asJsonArray
                    ChartEntry(
                        timestamp = arr[0].asLong,
                        open      = arr[1].asString.toDoubleOrNull() ?: 0.0,
                        high      = arr[2].asString.toDoubleOrNull() ?: 0.0,
                        low       = arr[3].asString.toDoubleOrNull() ?: 0.0,
                        close     = arr[4].asString.toDoubleOrNull() ?: 0.0,
                        volume    = arr[5].asString.toLongOrNull() ?: 0L
                    )
                }
                Resource.Success(entries)
            } catch (e: Exception) {
                Resource.Error("OHLC unavailable: ${e.message}")
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // searchCrypto / getTrending
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun searchCrypto(query: String): Resource<List<Crypto>> = withContext(Dispatchers.IO) {
        try {
            val response = coinGeckoApi.search(query)
            val coins = response.getAsJsonArray("coins")
            val results = (0 until minOf(coins.size(), 20)).map { i ->
                val item = coins[i].asJsonObject
                Crypto(
                    id = item.get("id")?.asString ?: "",
                    symbol = item.get("symbol")?.asString?.uppercase() ?: "",
                    name = item.get("name")?.asString ?: "",
                    price = 0.0,
                    change24h = 0.0,
                    changePercent24h = 0.0,
                    rank = item.get("market_cap_rank")?.asInt ?: 0,
                    imageUrl = item.get("large")?.asString ?: ""
                )
            }
            Resource.Success(results)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getTrending(): Resource<List<Crypto>> = withContext(Dispatchers.IO) {
        try {
            val response = coinGeckoApi.getTrending()
            val coins = response.getAsJsonArray("coins")
            val results = (0 until coins.size()).map { i ->
                val wrapper = coins[i].asJsonObject.getAsJsonObject("item")
                Crypto(
                    id = wrapper.get("id")?.asString ?: "",
                    symbol = wrapper.get("symbol")?.asString?.uppercase() ?: "",
                    name = wrapper.get("name")?.asString ?: "",
                    price = wrapper.getAsJsonObject("data")?.get("price")?.asDouble ?: 0.0,
                    change24h = 0.0,
                    changePercent24h = wrapper.getAsJsonObject("data")
                        ?.get("price_change_percentage_24h")
                        ?.asJsonObject?.get("usd")?.asDouble ?: 0.0,
                    rank = wrapper.get("market_cap_rank")?.asInt ?: 0,
                    imageUrl = wrapper.get("large")?.asString ?: ""
                )
            }
            Resource.Success(results)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error")
        }
    }
}
