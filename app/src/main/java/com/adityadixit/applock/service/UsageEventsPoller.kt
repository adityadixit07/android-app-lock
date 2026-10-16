package com.adityadixit.applock.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Build

/**
 * Reads the most recent "activity resumed" event from UsageStatsManager. Incremental: each poll
 * only scans events since the previous poll (plus a small overlap for late-logged events), so
 * the cost per call stays tiny regardless of how long the service has been running.
 */
class UsageEventsPoller(
    private val usageStatsManager: UsageStatsManager,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var windowStart = clock() - INITIAL_LOOKBACK_MS

    /** @return the package that most recently came to the foreground, or null if none since last poll. */
    fun latestForegroundPackage(): String? {
        val now = clock()
        val events = usageStatsManager.queryEvents(windowStart, now) ?: return null
        windowStart = now - OVERLAP_MS

        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTimestamp = Long.MIN_VALUE
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == RESUMED_EVENT && event.timeStamp >= latestTimestamp) {
                latestPackage = event.packageName
                latestTimestamp = event.timeStamp
            }
        }
        return latestPackage
    }

    private companion object {
        /** On (re)start, look back far enough to find the app that is already open. */
        const val INITIAL_LOOKBACK_MS = 10 * 60_000L
        const val OVERLAP_MS = 1_000L

        val RESUMED_EVENT = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }
    }
}
