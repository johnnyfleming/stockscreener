package com.tradescreenerai.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tradescreenerai.app.BuildConfig
import com.tradescreenerai.app.data.model.AssetType
import com.tradescreenerai.app.data.model.PriceAlert
import com.tradescreenerai.app.data.remote.RetrofitClient
import com.tradescreenerai.app.data.repository.LocalDataRepository
import com.tradescreenerai.app.util.NotificationHelper
import kotlinx.coroutines.flow.first

/**
 * Periodic background worker (every 15 minutes) that:
 *  1. Checks all active price alerts and fires a notification if triggered.
 *  2. Fetches the latest top business headlines and notifies the user if
 *     there is a new article since the last check.
 */
class PriceAlertWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repo      = LocalDataRepository(context)
    private val yahooApi  = RetrofitClient.yahooFinanceApi
    private val geckoApi  = RetrofitClient.coinGeckoApi
    private val newsApi   = RetrofitClient.newsApi

    override suspend fun doWork(): Result {
        return try {
            checkPriceAlerts()
            checkUrgentNews()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    // ── Price alerts ──────────────────────────────────────────────────────────

    private suspend fun checkPriceAlerts() {
        val prefs = repo.notificationPrefs.first()
        if (!prefs.priceAlertsEnabled) return

        val alerts = repo.alerts.first().filter { it.isActive }
        if (alerts.isEmpty()) return

        // Batch crypto fetches by collecting unique IDs
        val cryptoIds   = alerts.filter { it.type == AssetType.CRYPTO }.map { it.symbol.lowercase() }.distinct()
        val cryptoPrices: Map<String, Double> = if (cryptoIds.isNotEmpty()) {
            fetchCryptoPrices(cryptoIds)
        } else emptyMap()

        alerts.forEachIndexed { index, alert ->
            val price: Double = when (alert.type) {
                AssetType.CRYPTO               -> cryptoPrices[alert.symbol.lowercase()] ?: return@forEachIndexed
                AssetType.STOCK, AssetType.COMMODITY -> fetchStockPrice(alert.symbol) ?: return@forEachIndexed
            }

            val triggered = if (alert.isAbove) price >= alert.targetPrice
                            else               price <= alert.targetPrice

            if (triggered) {
                NotificationHelper.sendPriceAlert(
                    context      = applicationContext,
                    notifId      = NotificationHelper.ID_PRICE_ALERT_BASE + index,
                    symbol       = alert.symbol,
                    currentPrice = price,
                    targetPrice  = alert.targetPrice,
                    isAbove      = alert.isAbove
                )
                // Deactivate so it doesn't keep firing every 15 min
                repo.toggleAlert(alert.id)
            }
        }
    }

    private suspend fun fetchCryptoPrices(ids: List<String>): Map<String, Double> {
        return try {
            val json = geckoApi.getSimplePrice(ids = ids.joinToString(","))
            ids.mapNotNull { id ->
                val price = json.getAsJsonObject(id)?.get("usd")?.asDouble
                if (price != null && price > 0) id to price else null
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun fetchStockPrice(symbol: String): Double? {
        return try {
            val json = yahooApi.getChart(
                symbol   = symbol.uppercase(),
                interval = "1d",
                range    = "1d"
            )
            val result = json.getAsJsonObject("chart")
                ?.getAsJsonArray("result")
                ?.takeIf { it.size() > 0 }
                ?.get(0)?.asJsonObject
            val closes = result
                ?.getAsJsonObject("indicators")
                ?.getAsJsonArray("quote")
                ?.takeIf { it.size() > 0 }
                ?.get(0)?.asJsonObject
                ?.getAsJsonArray("close")
            closes?.let { arr ->
                (arr.size() - 1 downTo 0).firstNotNullOfOrNull {
                    arr[it]?.asDouble?.takeIf { v -> !v.isNaN() && v > 0 }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── Urgent news ───────────────────────────────────────────────────────────

    private suspend fun checkUrgentNews() {
        val prefs = repo.notificationPrefs.first()
        if (!prefs.urgentNewsEnabled) return

        try {
            val response = newsApi.getTopHeadlines(
                category = "business",
                country  = "us",
                pageSize = 5,
                apiKey   = BuildConfig.NEWS_API_KEY
            )
            val articles = response.getAsJsonArray("articles") ?: return
            if (articles.size() == 0) return

            val topArticle  = articles[0].asJsonObject
            val title       = topArticle.get("title")?.asString ?: return
            val source      = topArticle.getAsJsonObject("source")?.get("name")?.asString ?: "News"
            val articleId   = topArticle.get("url")?.asString ?: title

            val lastId = repo.getLastNewsNotifId()
            if (articleId == lastId) return   // already notified about this headline

            NotificationHelper.sendNewsAlert(
                context  = applicationContext,
                notifId  = NotificationHelper.ID_NEWS_BASE,
                headline = title,
                source   = source
            )
            repo.saveLastNewsNotifId(articleId)
        } catch (_: Exception) {}
    }
}

