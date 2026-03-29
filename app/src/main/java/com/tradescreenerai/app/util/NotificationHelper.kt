package com.tradescreenerai.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tradescreenerai.app.MainActivity
import com.tradescreenerai.app.R

/**
 * Creates notification channels and sends all push notifications for:
 *  – Price alerts (above / below target)
 *  – Market session (open, close, 5-min warnings)
 *  – Breaking news
 */
object NotificationHelper {

    // ── Channel IDs ──────────────────────────────────────────────────────────
    const val CHANNEL_PRICE_ALERTS    = "price_alerts"
    const val CHANNEL_MARKET_SESSION  = "market_session"
    const val CHANNEL_NEWS            = "breaking_news"

    // ── Notification IDs ─────────────────────────────────────────────────────
    const val ID_PRICE_ALERT_BASE     = 1000  // +index per alert
    const val ID_MARKET_OPEN          = 2001
    const val ID_MARKET_CLOSE         = 2002
    const val ID_MARKET_OPEN_WARNING  = 2003
    const val ID_MARKET_CLOSE_WARNING = 2004
    const val ID_NEWS_BASE            = 3000  // +index per article

    // ── Channel creation (call once in Application / MainActivity.onCreate) ──
    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PRICE_ALERTS,
                "Price Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notified when an asset price hits your target"
                enableVibration(true)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MARKET_SESSION,
                "Market Sessions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "US stock market open and close reminders"
                enableVibration(true)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NEWS,
                "Breaking News",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Urgent market news headlines"
            }
        )
    }

    // ── Shared builder ────────────────────────────────────────────────────────
    private fun baseBuilder(context: Context, channelId: String): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi)
            .setAutoCancel(true)
    }

    // ── Price alert ───────────────────────────────────────────────────────────
    fun sendPriceAlert(
        context: Context,
        notifId: Int,
        symbol: String,
        currentPrice: Double,
        targetPrice: Double,
        isAbove: Boolean
    ) {
        val direction = if (isAbove) "risen above" else "fallen below"
        val fmt = { v: Double -> "$${String.format(java.util.Locale.US, "%.2f", v)}" }
        notify(
            context, notifId,
            baseBuilder(context, CHANNEL_PRICE_ALERTS)
                .setContentTitle("📈 Price Alert: $symbol")
                .setContentText("$symbol has $direction ${fmt(targetPrice)} — now at ${fmt(currentPrice)}")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "$symbol has $direction your target of ${fmt(targetPrice)}.\n" +
                        "Current price: ${fmt(currentPrice)}"
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
        )
    }

    // ── Market session ────────────────────────────────────────────────────────
    fun sendMarketOpenWarning(context: Context) {
        notify(
            context, ID_MARKET_OPEN_WARNING,
            baseBuilder(context, CHANNEL_MARKET_SESSION)
                .setContentTitle("⏰ Market Opens in 5 Minutes")
                .setContentText("US stock market opens at 9:30 AM ET — get ready to trade!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
        )
    }

    fun sendMarketOpen(context: Context) {
        notify(
            context, ID_MARKET_OPEN,
            baseBuilder(context, CHANNEL_MARKET_SESSION)
                .setContentTitle("🔔 Market is Now Open")
                .setContentText("US stock market opened at 9:30 AM ET — penny stocks are live!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
        )
    }

    fun sendMarketCloseWarning(context: Context) {
        notify(
            context, ID_MARKET_CLOSE_WARNING,
            baseBuilder(context, CHANNEL_MARKET_SESSION)
                .setContentTitle("⏰ Market Closes in 5 Minutes")
                .setContentText("US stock market closes at 4:00 PM ET — manage your positions!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
        )
    }

    fun sendMarketClose(context: Context) {
        notify(
            context, ID_MARKET_CLOSE,
            baseBuilder(context, CHANNEL_MARKET_SESSION)
                .setContentTitle("🔔 Market is Now Closed")
                .setContentText("US stock market closed at 4:00 PM ET. See you tomorrow!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
        )
    }

    // ── News ──────────────────────────────────────────────────────────────────
    fun sendNewsAlert(context: Context, notifId: Int, headline: String, source: String) {
        notify(
            context, notifId,
            baseBuilder(context, CHANNEL_NEWS)
                .setContentTitle("📰 Breaking: $source")
                .setContentText(headline)
                .setStyle(NotificationCompat.BigTextStyle().bigText(headline))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        )
    }

    // ── Internal helper ───────────────────────────────────────────────────────
    private fun notify(context: Context, id: Int, builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not yet granted — silently skip
        }
    }
}

