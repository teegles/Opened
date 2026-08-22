package com.teegle.opened

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class FoldState { UNKNOWN, FOLDED, OPEN }

data class FoldSnapshot(
    val tracking: Boolean,
    val state: FoldState,
    val angle: Float?,
    val todayUnfolds: Int,
    val todayOpenMs: Long,
    val todayFoldedMs: Long,
    val totalUnfolds: Int
)

data class DailyUsage(
    val date: LocalDate,
    val unfolds: Int,
    val openMs: Long,
    val foldedMs: Long
)

data class LifetimeUsage(
    val started: LocalDate,
    val unfolds: Int,
    val openMs: Long,
    val foldedMs: Long
)

class FoldStore(context: Context) {
    private val prefs = context.getSharedPreferences("fold_tracking", Context.MODE_PRIVATE)
    private val zone: ZoneId get() = ZoneId.systemDefault()

    init {
        migrateToScreenOnDurations()
        removeLegacyAppUsage()
        ensureTrackingStartDate()
    }

    @Synchronized
    fun setTracking(enabled: Boolean, now: Long = System.currentTimeMillis()) {
        if (enabled && isTracking()) return
        if (!enabled) commitElapsed(now)
        val editor = prefs.edit()
            .putBoolean(KEY_TRACKING, enabled)
            .putLong(KEY_STATE_STARTED, now)
        if (enabled && !prefs.contains(KEY_TRACKING_STARTED)) {
            editor.putString(KEY_TRACKING_STARTED, dayFor(now).toString())
        }
        editor.apply()
    }

    @Synchronized
    fun checkpoint(now: Long = System.currentTimeMillis()) {
        commitElapsed(now)
        prefs.edit().putLong(KEY_STATE_STARTED, now).apply()
    }

    fun isTracking(): Boolean = prefs.getBoolean(KEY_TRACKING, false)

    @Synchronized
    fun recordInteractive(interactive: Boolean, now: Long = System.currentTimeMillis()) {
        val previous = prefs.getBoolean(KEY_INTERACTIVE, false)
        if (previous == interactive) return
        commitElapsed(now)
        prefs.edit()
            .putBoolean(KEY_INTERACTIVE, interactive)
            .putLong(KEY_STATE_STARTED, now)
            .apply()
    }

    @Synchronized
    fun recordAngle(angle: Float, now: Long = System.currentTimeMillis()): Boolean {
        prefs.edit().putFloat(KEY_ANGLE, angle).apply()
        val previous = readState()
        val next = when {
            angle <= FOLDED_THRESHOLD -> FoldState.FOLDED
            angle >= OPEN_THRESHOLD -> FoldState.OPEN
            else -> previous
        }
        if (next == FoldState.UNKNOWN || next == previous) return false

        commitElapsed(now)
        val editor = prefs.edit()
            .putString(KEY_STATE, next.name)
            .putLong(KEY_STATE_STARTED, now)

        if (previous == FoldState.FOLDED && next == FoldState.OPEN) {
            val day = dayFor(now)
            editor.putInt(unfoldKey(day), prefs.getInt(unfoldKey(day), 0) + 1)
            editor.putInt(KEY_TOTAL_UNFOLDS, prefs.getInt(KEY_TOTAL_UNFOLDS, 0) + 1)
        }
        editor.apply()
        return true
    }

    @Synchronized
    fun snapshot(now: Long = System.currentTimeMillis()): FoldSnapshot {
        val today = dayFor(now)
        var openMs = prefs.getLong(openKey(today), 0L)
        var foldedMs = prefs.getLong(foldedKey(today), 0L)
        val state = readState()

        if (isTracking() && prefs.getBoolean(KEY_INTERACTIVE, false) && state != FoldState.UNKNOWN) {
            val started = prefs.getLong(KEY_STATE_STARTED, now).coerceAtMost(now)
            val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            val currentElapsed = now - maxOf(started, todayStart)
            if (state == FoldState.OPEN) openMs += currentElapsed
            if (state == FoldState.FOLDED) foldedMs += currentElapsed
        }

        return FoldSnapshot(
            tracking = isTracking(),
            state = state,
            angle = if (prefs.contains(KEY_ANGLE)) prefs.getFloat(KEY_ANGLE, 0f) else null,
            todayUnfolds = prefs.getInt(unfoldKey(today), 0),
            todayOpenMs = openMs,
            todayFoldedMs = foldedMs,
            totalUnfolds = prefs.getInt(KEY_TOTAL_UNFOLDS, 0)
        )
    }

    @Synchronized
    fun lastSevenDays(now: Long = System.currentTimeMillis()): List<DailyUsage> {
        val today = dayFor(now)
        val current = snapshot(now)
        return (6 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            if (day == today) {
                DailyUsage(day, current.todayUnfolds, current.todayOpenMs, current.todayFoldedMs)
            } else {
                DailyUsage(
                    day,
                    prefs.getInt(unfoldKey(day), 0),
                    prefs.getLong(openKey(day), 0L),
                    prefs.getLong(foldedKey(day), 0L)
                )
            }
        }
    }

    @Synchronized
    fun lifetimeUsage(now: Long = System.currentTimeMillis()): LifetimeUsage {
        val today = dayFor(now)
        val current = snapshot(now)
        val pastDays = storedDates().filter { it != today }
        return LifetimeUsage(
            started = trackingStartDate(now),
            unfolds = current.totalUnfolds,
            openMs = pastDays.sumOf { prefs.getLong(openKey(it), 0L) } + current.todayOpenMs,
            foldedMs = pastDays.sumOf { prefs.getLong(foldedKey(it), 0L) } + current.todayFoldedMs
        )
    }

    @Synchronized
    fun reset(now: Long = System.currentTimeMillis()) {
        val tracking = isTracking()
        val interactive = prefs.getBoolean(KEY_INTERACTIVE, false)
        prefs.edit()
            .clear()
            .putBoolean(KEY_TRACKING, tracking)
            .putBoolean(KEY_INTERACTIVE, interactive)
            .putInt(KEY_METRIC_VERSION, SCREEN_ON_METRIC_VERSION)
            .putLong(KEY_STATE_STARTED, now)
            .putString(KEY_TRACKING_STARTED, dayFor(now).toString())
            .apply()
    }

    private fun readState(): FoldState = runCatching {
        FoldState.valueOf(prefs.getString(KEY_STATE, FoldState.UNKNOWN.name)!!)
    }.getOrDefault(FoldState.UNKNOWN)

    private fun commitElapsed(end: Long) {
        val state = readState()
        if (state == FoldState.UNKNOWN || !prefs.getBoolean(KEY_INTERACTIVE, false)) return
        var cursor = prefs.getLong(KEY_STATE_STARTED, end).coerceAtMost(end)
        val editor = prefs.edit()

        while (cursor < end) {
            val date = dayFor(cursor)
            val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val segmentEnd = minOf(end, nextMidnight)
            val duration = segmentEnd - cursor
            val key = if (state == FoldState.OPEN) openKey(date) else foldedKey(date)
            editor.putLong(key, prefs.getLong(key, 0L) + duration)
            cursor = segmentEnd
        }
        editor.apply()
    }

    private fun migrateToScreenOnDurations() {
        if (prefs.getInt(KEY_METRIC_VERSION, 1) >= SCREEN_ON_METRIC_VERSION) return
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.endsWith("_open_ms") || it.endsWith("_folded_ms") }
            .forEach(editor::remove)
        editor
            .putInt(KEY_METRIC_VERSION, SCREEN_ON_METRIC_VERSION)
            .putBoolean(KEY_INTERACTIVE, false)
            .putLong(KEY_STATE_STARTED, System.currentTimeMillis())
            .apply()
    }

    private fun removeLegacyAppUsage() {
        val oldKeys = prefs.all.keys.filter { it.startsWith("app_") }
        if (oldKeys.isEmpty()) return
        prefs.edit().also { editor -> oldKeys.forEach(editor::remove) }.apply()
    }

    private fun ensureTrackingStartDate(now: Long = System.currentTimeMillis()) {
        if (prefs.contains(KEY_TRACKING_STARTED)) return
        val firstRecordedDay = storedDates().minOrNull()
        if (firstRecordedDay != null || isTracking()) {
            prefs.edit()
                .putString(KEY_TRACKING_STARTED, (firstRecordedDay ?: dayFor(now)).toString())
                .apply()
        }
    }

    private fun trackingStartDate(now: Long): LocalDate {
        val stored = prefs.getString(KEY_TRACKING_STARTED, null)
        return stored?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: storedDates().minOrNull()
            ?: dayFor(now)
    }

    private fun storedDates(): Set<LocalDate> = prefs.all.keys.mapNotNullTo(mutableSetOf()) { key ->
        val match = DAY_KEY.matchEntire(key) ?: return@mapNotNullTo null
        runCatching { LocalDate.parse(match.groupValues[1]) }.getOrNull()
    }

    private fun dayFor(time: Long): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    private fun openKey(day: LocalDate) = "day_${day}_open_ms"
    private fun foldedKey(day: LocalDate) = "day_${day}_folded_ms"
    private fun unfoldKey(day: LocalDate) = "day_${day}_unfolds"

    companion object {
        const val FOLDED_THRESHOLD = 15f
        const val OPEN_THRESHOLD = 165f
        private const val KEY_TRACKING = "tracking"
        private const val KEY_STATE = "state"
        private const val KEY_STATE_STARTED = "state_started"
        private const val KEY_ANGLE = "angle"
        private const val KEY_TOTAL_UNFOLDS = "total_unfolds"
        private const val KEY_INTERACTIVE = "interactive"
        private const val KEY_METRIC_VERSION = "metric_version"
        private const val KEY_TRACKING_STARTED = "tracking_started"
        private const val SCREEN_ON_METRIC_VERSION = 2
        private val DAY_KEY = Regex("^day_(\\d{4}-\\d{2}-\\d{2})_(?:open_ms|folded_ms|unfolds)$")
    }
}
