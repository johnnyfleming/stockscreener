package com.tradescreenerai.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null,
    val selectedIcon: ImageVector? = null
) {
    // Bottom nav destinations
    data object Dashboard   : Screen("dashboard",    "Markets",      Icons.Outlined.Dashboard,      Icons.Filled.Dashboard)
    data object Stocks      : Screen("stocks",       "Stocks",       Icons.AutoMirrored.Outlined.ShowChart, Icons.AutoMirrored.Filled.ShowChart)
    data object Screener    : Screen("screener",     "Screener",     Icons.Outlined.FilterList,     Icons.Filled.FilterList)
    data object Commodities : Screen("commodities",  "Commodities",  Icons.Outlined.Public,         Icons.Filled.Public)
    data object Watchlist   : Screen("watchlist",    "Watchlist",    Icons.Outlined.Star,           Icons.Filled.Star)
    data object Portfolio   : Screen("portfolio",    "Portfolio",    Icons.Outlined.PieChart,       Icons.Filled.PieChart)
    data object News        : Screen("news",         "News",         Icons.Outlined.Newspaper,      Icons.Filled.Newspaper)
    data object BuySignals  : Screen("buy_signals",  "Signals",      Icons.Outlined.BubbleChart,    Icons.Filled.BubbleChart)

    // Detail screens
    data object StockDetail : Screen("stock_detail/{symbol}", "Stock Detail") {
        fun createRoute(symbol: String) = "stock_detail/$symbol"
    }
    data object CryptoDetail : Screen("crypto_detail/{id}", "Crypto Detail") {
        fun createRoute(id: String) = "crypto_detail/$id"
    }
    data object CommodityDetail : Screen("commodity_detail/{type}", "Commodity Detail") {
        fun createRoute(type: String) = "commodity_detail/$type"
    }

    // Other screens
    data object Search   : Screen("search",   "Search")
    data object Alerts   : Screen("alerts",   "Alerts")
    data object Learn    : Screen("learn",    "Learn Indicators")
    data object Settings : Screen("settings", "Settings")
    data object Heatmap  : Screen("heatmap",  "Heatmap")
    data object DayTrader : Screen("day_trader/{symbol}/{assetType}", "Day Trader") {
        fun createRoute(symbol: String, assetType: String) = "day_trader/$symbol/$assetType"
    }

    companion object {
        val bottomNavItems = listOf(Dashboard, Stocks, Screener, Commodities, Watchlist)
    }
}

