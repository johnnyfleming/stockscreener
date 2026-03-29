package com.tradescreenerai.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tradescreenerai.app.data.repository.LocalDataRepository
import com.tradescreenerai.app.util.NotificationHelper
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.TimeZone

/**
 * OneTimeWorker fired at each of the four daily market-session times.
 * Sends the appropriate notification and then re-schedules itself for
 * the next business day.
 */
class MarketSessionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_EVENT    = "event"
        const val KEY_DATE_KEY = "date_key"
    }

    override suspend fun doWork(): Result {
        val repo  = LocalDataRepository(applicationContext)
        val prefs = repo.notificationPrefs.first()

        if (prefs.marketSessionEnabled) {
            val eventTag = inputData.getString(KEY_EVENT) ?: return Result.success()

            // Guard: skip if we already sent this exact event today
            val storedKey = inputData.getString(KEY_DATE_KEY) ?: ""
            val eventKey  = "$storedKey-$eventTag"
            val sent      = repo.getSentMarketEvents()
            if (eventKey !in sent) {
                when (eventTag) {
                    MarketNotificationScheduler.Event.OPEN_WARNING.tag  ->
                        NotificationHelper.sendMarketOpenWarning(applicationContext)
                    MarketNotificationScheduler.Event.OPEN.tag          ->
                        NotificationHelper.sendMarketOpen(applicationContext)
                    MarketNotificationScheduler.Event.CLOSE_WARNING.tag ->
                        NotificationHelper.sendMarketCloseWarning(applicationContext)
                    MarketNotificationScheduler.Event.CLOSE.tag         ->
                        NotificationHelper.sendMarketClose(applicationContext)
                }
                repo.addSentMarketEvent(eventKey)
            }

            // Re-schedule for the next business day
            val event = MarketNotificationScheduler.Event.fromTag(eventTag)
            if (event != null) {
                val et  = TimeZone.getTimeZone("America/New_York")
                val now = Calendar.getInstance(et)
                // Advance to tomorrow so the scheduler moves to the next occurrence
                now.add(Calendar.DAY_OF_YEAR, 1)
                MarketNotificationScheduler.scheduleEvent(applicationContext, now, event)
            }
        }

        return Result.success()
    }
}

