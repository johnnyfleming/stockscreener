package com.tradescreenerai.app.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Schedules OneTimeWorkRequests for the four daily market-session notifications:
 *   9:25 AM ET  – "Market opens in 5 minutes"
 *   9:30 AM ET  – "Market is now open"
 *   3:55 PM ET  – "Market closes in 5 minutes"
 *   4:00 PM ET  – "Market is now closed"
 *
 * Works are de-duplicated by a date+event key so calling [scheduleAll] repeatedly
 * (e.g. on every app start) never double-fires.
 */
object MarketNotificationScheduler {

    // ── Events ────────────────────────────────────────────────────────────────
    enum class Event(
        val tag: String,
        val hour: Int,
        val minute: Int
    ) {
        OPEN_WARNING ("open_warn",   9, 25),
        OPEN         ("open",        9, 30),
        CLOSE_WARNING("close_warn", 15, 55),
        CLOSE        ("close",      16,  0);

        companion object {
            fun fromTag(tag: String) = entries.firstOrNull { it.tag == tag }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Enqueue all four events if they are not already scheduled. */
    fun scheduleAll(context: Context) {
        val et  = TimeZone.getTimeZone("America/New_York")
        val now = Calendar.getInstance(et)
        Event.entries.forEach { scheduleEvent(context, now, it) }
    }

    /** Schedule a single event, forwarding to the next business day if it has already passed. */
    fun scheduleEvent(context: Context, now: Calendar, event: Event) {
        val et     = TimeZone.getTimeZone("America/New_York")
        val target = Calendar.getInstance(et).apply {
            set(Calendar.HOUR_OF_DAY, event.hour)
            set(Calendar.MINUTE,      event.minute)
            set(Calendar.SECOND,      0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the event has already passed today (or is <60 s away), move to next business day
        if (target.timeInMillis <= now.timeInMillis + 60_000L) {
            target.add(Calendar.DAY_OF_YEAR, 1)
            skipWeekends(target)
        }

        val delayMs  = target.timeInMillis - System.currentTimeMillis()
        val dateKey  = "${target.get(Calendar.YEAR)}_${target.get(Calendar.DAY_OF_YEAR)}"
        val workName = "market_${event.tag}_$dateKey"

        val work = OneTimeWorkRequestBuilder<MarketSessionWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                MarketSessionWorker.KEY_EVENT    to event.tag,
                MarketSessionWorker.KEY_DATE_KEY to dateKey
            ))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, work)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun skipWeekends(cal: Calendar) {
        while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
               cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}

