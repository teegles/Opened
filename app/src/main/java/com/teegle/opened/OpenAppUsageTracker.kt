package com.teegle.opened

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process

object UsageAccess {
    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }
}

class OpenAppUsageTracker(
    private val context: Context,
    private val store: FoldStore
) {
    private val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val homePackage = context.packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        PackageManager.MATCH_DEFAULT_ONLY
    )?.activityInfo?.packageName

    private var lastSample = System.currentTimeMillis()
    private var currentPackage: String? = null
    private var wasEligible = false

    init {
        if (UsageAccess.isGranted(context)) {
            currentPackage = findForegroundPackage(lastSample - INITIAL_LOOKBACK_MS, lastSample)
        }
        wasEligible = store.isOpenAndInteractive()
    }

    fun sample(now: Long = System.currentTimeMillis()) {
        val elapsed = (now - lastSample).coerceIn(0L, MAX_SAMPLE_GAP_MS)
        val packageToRecord = currentPackage?.takeUnless(::shouldIgnore)
        if (wasEligible && packageToRecord != null && UsageAccess.isGranted(context)) {
            store.recordAppUsage(packageToRecord, now - elapsed, now)
        }

        currentPackage = if (UsageAccess.isGranted(context)) {
            findForegroundPackage(lastSample - EVENT_OVERLAP_MS, now) ?: currentPackage
        } else {
            null
        }
        lastSample = now
        wasEligible = store.isOpenAndInteractive()
    }

    private fun findForegroundPackage(begin: Long, end: Long): String? {
        val events = usageStats.queryEvents(begin.coerceAtLeast(0L), end)
        val event = UsageEvents.Event()
        var foreground: String? = currentPackage
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> foreground = packageName
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> if (foreground == packageName) foreground = null
            }
        }
        return foreground
    }

    private fun shouldIgnore(packageName: String): Boolean =
        packageName == context.packageName ||
            packageName == "com.android.systemui" ||
            packageName == homePackage

    companion object {
        private const val INITIAL_LOOKBACK_MS = 6 * 60 * 60 * 1_000L
        private const val EVENT_OVERLAP_MS = 1_000L
        private const val MAX_SAMPLE_GAP_MS = 30_000L
    }
}
