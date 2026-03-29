package com.tradescreenerai.app.util

object Constants {
    const val ALPHA_VANTAGE_BASE_URL = "https://www.alphavantage.co/"
    const val COINGECKO_BASE_URL = "https://api.coingecko.com/api/v3/"
    const val COINPAPRIKA_BASE_URL = "https://api.coinpaprika.com/v1/"
    const val COINCAP_BASE_URL = "https://api.coincap.io/v2/"
    const val FINNHUB_BASE_URL = "https://finnhub.io/api/v1/"
    const val NEWS_API_BASE_URL = "https://newsapi.org/v2/"
    const val ALTERNATIVE_ME_BASE_URL = "https://api.alternative.me/"
    const val POLYGON_BASE_URL = "https://api.polygon.io/"
    const val BINANCE_BASE_URL = "https://api.binance.com/api/v3/"
    /** Yahoo Finance – free, no API key, real-time screener + quotes */
    const val YAHOO_FINANCE_BASE_URL = "https://query2.finance.yahoo.com/"

    /** How long (ms) to stay on the CoinPaprika fallback after a 429 from CoinGecko. */
    const val RATE_LIMIT_BACKOFF_MS = 90_000L   // 90 seconds

    // Default screener values
    const val DEFAULT_MIN_PRICE = 0.0
    const val DEFAULT_MAX_PRICE = 10000.0
    const val DEFAULT_MIN_VOLUME = 0L
    const val DEFAULT_MIN_MARKET_CAP = 0L

    // Chart time ranges
    val TIME_RANGES = listOf("1D", "1W", "1M", "3M", "1Y", "ALL")

    // Top stock symbols for dashboard
    val TOP_STOCKS = listOf(
        "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA",
        "META", "NVDA", "JPM", "V", "WMT",
        "DIS", "NFLX", "PYPL", "AMD", "INTC",
        "BA", "CRM", "UBER", "SQ", "COIN"
    )

    // Top crypto IDs for CoinGecko
    val TOP_CRYPTOS = listOf(
        "bitcoin", "ethereum", "binancecoin", "solana", "cardano",
        "ripple", "polkadot", "dogecoin", "avalanche-2", "chainlink",
        "polygon", "litecoin", "uniswap", "stellar", "cosmos",
        "near", "algorand", "fantom", "aave", "maker"
    )

    // Sectors
    val SECTORS = listOf(
        "Technology", "Healthcare", "Finance", "Energy",
        "Consumer Cyclical", "Industrials", "Communication Services",
        "Consumer Defensive", "Real Estate", "Utilities", "Materials"
    )

    // Commodity refresh interval (cached for 1 hour — uses Polygon ETF proxies, no premium needed)
    const val COMMODITY_CACHE_TTL_MS = 3_600_000L   // 1 hour

    // Expanded stock pool for ranking engine (beyond TOP_STOCKS)
    val RANKING_STOCK_POOL = listOf(
        "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "META", "NVDA", "JPM", "V", "WMT",
        "DIS", "NFLX", "PYPL", "AMD", "INTC", "BA", "CRM", "UBER", "SQ", "COIN",
        "ORCL", "ADBE", "CSCO", "PEP", "KO", "MCD", "NKE", "HD", "LOW", "TGT",
        "COST", "CVX", "XOM", "PFE", "JNJ", "UNH", "ABBV", "MRK", "LLY", "BMY",
        "GS", "MS", "C", "BAC", "WFC", "AXP", "BLK", "SCHW", "T", "VZ",
        "QCOM", "AVGO", "TXN", "MU", "LRCX", "AMAT", "KLAC", "MRVL", "ON", "SWKS",
        "F", "GM", "RIVN", "LCID", "NIO", "LI", "XPEV", "PLTR", "SNOW", "NET",
        "DDOG", "ZS", "CRWD", "PANW", "FTNT", "MNST", "CELH", "DASH", "ABNB", "RBLX",
        "SE", "SHOP", "MELI", "BABA", "JD", "PDD", "SPOT", "SQ", "SOFI", "HOOD"
    ).distinct()

    /**
     * Curated universe of frequently traded penny/small-cap stocks used by the
     * Polygon snapshot endpoint for fast penny-stock discovery (no full market
     * dump needed). Prices are verified at runtime — stocks above $5 are filtered out.
     */
    val PENNY_STOCK_UNIVERSE = listOf(
        // Cannabis
        "SNDL", "CGC", "ACB", "TLRY", "OGI", "HEXO", "VFF", "CRON",
        // Biotech small-cap
        "OCGN", "BNGO", "CTXR", "SRNE", "APDN", "CTIC", "NVAX", "MMAT", "CLOV",
        // EV / clean energy speculative
        "NKLA", "WKHS", "MULN", "FFIE", "RIDE", "BLNK", "CHPT", "EVGO",
        // Mining / metals
        "HL", "CDE", "AG", "MUX", "GPL", "EGO", "AUY", "KGC", "PAAS", "SVM", "FSM",
        // Shipping
        "CTRM", "IMPP", "SHIP", "GLBS", "GOGL", "TOPS", "EGLE",
        // Meme / high-attention stocks
        "AMC", "NOK", "BB", "MVIS", "KOSS", "EXPR",
        // Resources / uranium
        "UEC", "URG", "DNN", "EU",
        // Small tech / fintech
        "GFAI", "ATER", "ACMR", "VNET", "TIGR", "AGBA", "PHUN", "DPRO", "INPX",
        // Small pharma / biotech
        "ZOM", "OBSV", "TLSS", "XELA", "NCTY", "SENS", "AGTC", "PRTS",
        "RIGL", "ACRS", "BDSX", "CLVS", "EDSA", "AQST",
        // Fintech / sometimes penny-range
        "HOOD", "SOFI", "PSFE", "UWMC",
        // Energy small
        "SUNW", "PEIX",
        // Financial small
        "FRBK", "TPVG",
        // Other frequently traded small-caps
        "ABEV", "VALE", "SPCE", "IDEX", "ONTX"
    ).distinct()
}
