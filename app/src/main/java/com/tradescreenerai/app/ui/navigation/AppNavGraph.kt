package com.tradescreenerai.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tradescreenerai.app.ui.screens.alerts.AlertsScreen
import com.tradescreenerai.app.ui.screens.commodities.CommoditiesScreen
import com.tradescreenerai.app.ui.screens.commodities.CommodityDetailScreen
import com.tradescreenerai.app.ui.screens.dashboard.DashboardScreen
import com.tradescreenerai.app.ui.screens.dashboard.HeatmapScreen
import com.tradescreenerai.app.ui.screens.daytrader.DayTraderScreen
import com.tradescreenerai.app.ui.screens.detail.CryptoDetailScreen
import com.tradescreenerai.app.ui.screens.detail.StockDetailScreen
import com.tradescreenerai.app.ui.screens.learn.LearnScreen
import com.tradescreenerai.app.ui.screens.news.NewsScreen
import com.tradescreenerai.app.ui.screens.portfolio.PortfolioScreen
import com.tradescreenerai.app.ui.screens.screener.ScreenerScreen
import com.tradescreenerai.app.ui.screens.search.SearchScreen
import com.tradescreenerai.app.ui.screens.settings.SettingsScreen
import com.tradescreenerai.app.ui.screens.signals.BuySignalsScreen
import com.tradescreenerai.app.ui.screens.stocks.StocksScreen
import com.tradescreenerai.app.ui.screens.watchlist.WatchlistScreen

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        // Bottom nav destinations
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                onNavigateToCryptoDetail = { navController.navigate(Screen.CryptoDetail.createRoute(it)) },
                onNavigateToStockDetail = { navController.navigate(Screen.StockDetail.createRoute(it)) },
                onNavigateToAlerts = { navController.navigate(Screen.Alerts.route) },
                onNavigateToHeatmap = { navController.navigate(Screen.Heatmap.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Screener.route) {
            ScreenerScreen(
                onNavigateToCryptoDetail = { navController.navigate(Screen.CryptoDetail.createRoute(it)) },
                onNavigateToStockDetail = { navController.navigate(Screen.StockDetail.createRoute(it)) }
            )
        }

        // Stocks section
        composable(Screen.Stocks.route) {
            StocksScreen(
                onNavigateToStockDetail = { navController.navigate(Screen.StockDetail.createRoute(it)) },
                onNavigateToDayTrader = { symbol -> navController.navigate(Screen.DayTrader.createRoute(symbol, "STOCK")) },
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                onNavigateToNews = { navController.navigate(Screen.News.route) }
            )
        }

        // Commodities section
        composable(Screen.Commodities.route) {
            CommoditiesScreen(
                onNavigateToCommodityDetail = { navController.navigate(Screen.CommodityDetail.createRoute(it)) },
                onNavigateToDayTrader = { symbol -> navController.navigate(Screen.DayTrader.createRoute(symbol, "COMMODITY")) }
            )
        }

        composable(Screen.Watchlist.route) {
            WatchlistScreen(
                onNavigateToCryptoDetail = { navController.navigate(Screen.CryptoDetail.createRoute(it)) },
                onNavigateToStockDetail = { navController.navigate(Screen.StockDetail.createRoute(it)) },
                onNavigateToSearch = { navController.navigate(Screen.Search.route) }
            )
        }

        composable(Screen.Portfolio.route) {
            PortfolioScreen()
        }

        composable(Screen.News.route) {
            NewsScreen()
        }

        composable(Screen.BuySignals.route) {
            BuySignalsScreen(
                onNavigateToCryptoDetail = { navController.navigate(Screen.CryptoDetail.createRoute(it)) }
            )
        }

        // Detail screens
        composable(
            route = Screen.CryptoDetail.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            CryptoDetailScreen(
                coinId = id,
                onBack = { navController.popBackStack() },
                onLearn = { navController.navigate(Screen.Learn.route) },
                onDayTrader = { navController.navigate(Screen.DayTrader.createRoute(id, "CRYPTO")) }
            )
        }

        composable(
            route = Screen.StockDetail.route,
            arguments = listOf(navArgument("symbol") { type = NavType.StringType })
        ) { backStackEntry ->
            val symbol = backStackEntry.arguments?.getString("symbol") ?: return@composable
            StockDetailScreen(
                symbol = symbol,
                onBack = { navController.popBackStack() },
                onDayTrader = { navController.navigate(Screen.DayTrader.createRoute(symbol, "STOCK")) }
            )
        }

        // Day Trader Mode
        composable(
            route = Screen.DayTrader.route,
            arguments = listOf(
                navArgument("symbol") { type = NavType.StringType },
                navArgument("assetType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sym = backStackEntry.arguments?.getString("symbol") ?: return@composable
            val type = backStackEntry.arguments?.getString("assetType") ?: "CRYPTO"
            DayTraderScreen(
                symbol = sym,
                assetType = type,
                onBack = { navController.popBackStack() }
            )
        }

        // Other screens
        composable(Screen.Search.route) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onNavigateToStockDetail = { navController.navigate(Screen.StockDetail.createRoute(it)) },
                onNavigateToCryptoDetail = { navController.navigate(Screen.CryptoDetail.createRoute(it)) },
                onNavigateToCommodityDetail = { navController.navigate(Screen.CommodityDetail.createRoute(it)) }
            )
        }

        composable(Screen.Alerts.route) {
            AlertsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Learn.route) {
            LearnScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAlerts = { navController.navigate(Screen.Alerts.route) }
            )
        }

        composable(Screen.Heatmap.route) {
            HeatmapScreen(
                onBack = { navController.popBackStack() },
                onNavigateToCryptoDetail = { navController.navigate(Screen.CryptoDetail.createRoute(it)) }
            )
        }

        // Commodity detail screen
        composable(
            route = Screen.CommodityDetail.route,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { backStackEntry ->
            val commodityType = backStackEntry.arguments?.getString("type") ?: return@composable
            CommodityDetailScreen(
                commodityTypeStr = commodityType,
                onBack = { navController.popBackStack() },
                onDayTrader = { navController.navigate(Screen.DayTrader.createRoute(commodityType, "COMMODITY")) }
            )
        }
    }
}

