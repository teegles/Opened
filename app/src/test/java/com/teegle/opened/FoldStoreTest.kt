package com.teegle.opened

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FoldStoreTest {
    private val zone = ZoneId.of("America/Denver")

    @Test
    fun countsOnlyCompleteClosedToOpenedTransitions() {
        val store = store()
        val start = millis(2026, 8, 20, 8, 0)
        store.resumeTracking(interactive = false, now = start)

        assertTrue(store.recordAngle(0f, start))
        assertFalse(store.recordAngle(90f, start + 1_000))
        assertTrue(store.recordAngle(180f, start + 2_000))
        assertFalse(store.recordAngle(170f, start + 3_000))
        assertTrue(store.recordAngle(0f, start + 4_000))
        assertTrue(store.recordAngle(180f, start + 5_000))

        val snapshot = store.snapshot(start + 6_000)
        assertEquals(2, snapshot.todayUnfolds)
        assertEquals(0L, snapshot.todayOpenMs)
        assertEquals(0L, snapshot.todayFoldedMs)
    }

    @Test
    fun splitsInteractiveTimeAtMidnight() {
        val store = store()
        val beforeMidnight = millis(2026, 8, 20, 23, 55)
        val afterMidnight = millis(2026, 8, 21, 0, 5)
        store.resumeTracking(interactive = true, now = beforeMidnight)
        store.recordAngle(0f, beforeMidnight)

        store.checkpoint(afterMidnight)

        val days = store.lastSevenDays(afterMidnight)
        assertEquals(5 * 60_000L, days[5].foldedMs)
        assertEquals(5 * 60_000L, days[6].foldedMs)
    }

    @Test
    fun serviceResumeDoesNotCountDowntime() {
        val store = store()
        val start = millis(2026, 8, 20, 8, 0)
        store.resumeTracking(interactive = true, now = start)
        store.recordAngle(180f, start)
        store.checkpoint(start + 10 * 60_000L)

        val restart = start + 2 * 60 * 60_000L
        store.resumeTracking(interactive = true, now = restart)

        assertEquals(15 * 60_000L, store.snapshot(restart + 5 * 60_000L).todayOpenMs)
    }

    @Test
    fun lifetimeTotalsIncludePastAndCurrentDays() {
        val store = store()
        val dayOne = millis(2026, 8, 19, 12, 0)
        store.resumeTracking(interactive = true, now = dayOne)
        store.recordAngle(0f, dayOne)
        store.checkpoint(dayOne + 20 * 60_000L)
        store.recordAngle(180f, dayOne + 20 * 60_000L)
        store.checkpoint(dayOne + 50 * 60_000L)

        val dayTwo = millis(2026, 8, 20, 12, 0)
        store.resumeTracking(interactive = true, now = dayTwo)
        val lifetime = store.lifetimeUsage(dayTwo + 10 * 60_000L)

        assertEquals("2026-08-19", lifetime.started.toString())
        assertEquals(1, lifetime.unfolds)
        assertEquals(40 * 60_000L, lifetime.openMs)
        assertEquals(20 * 60_000L, lifetime.foldedMs)
    }

    @Test
    fun resetStartsANewLifetimeWithoutStoppingTracking() {
        val store = store()
        val start = millis(2026, 8, 20, 8, 0)
        store.resumeTracking(interactive = true, now = start)
        store.recordAngle(0f, start)
        store.recordAngle(180f, start + 1_000)

        val resetAt = millis(2026, 8, 21, 9, 0)
        store.reset(resetAt)
        val snapshot = store.snapshot(resetAt)
        val lifetime = store.lifetimeUsage(resetAt)

        assertTrue(snapshot.tracking)
        assertEquals(0, lifetime.unfolds)
        assertEquals(0L, lifetime.openMs)
        assertEquals(0L, lifetime.foldedMs)
        assertEquals("2026-08-21", lifetime.started.toString())
    }

    @Test
    fun screenTimeMigrationKeepsUnfoldCounts() {
        val prefs = InMemorySharedPreferences(
            mapOf<String, Any?>(
                "metric_version" to 1,
                "day_2026-08-20_open_ms" to 123L,
                "day_2026-08-20_folded_ms" to 456L,
                "day_2026-08-20_unfolds" to 7,
                "total_unfolds" to 7
            )
        )
        val store = FoldStore(prefs, zone)
        val now = millis(2026, 8, 20, 12, 0)

        val lifetime = store.lifetimeUsage(now)
        assertEquals(7, lifetime.unfolds)
        assertEquals(0L, lifetime.openMs)
        assertEquals(0L, lifetime.foldedMs)
        assertFalse(prefs.contains("day_2026-08-20_open_ms"))
        assertTrue(prefs.contains("day_2026-08-20_unfolds"))
    }

    private fun store() = FoldStore(
        InMemorySharedPreferences(mapOf("metric_version" to 2)),
        zone
    )

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
}

private class InMemorySharedPreferences(initial: Map<String, Any?> = emptyMap()) : SharedPreferences {
    private val values = initial.toMutableMap()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        listeners += listener
    }
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        listeners -= listener
    }

    private inner class Editor : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearFirst = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = put(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
            put(key, values?.toSet())
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = put(key, value)
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = put(key, value)
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = put(key, value)
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = put(key, value)
        override fun remove(key: String): SharedPreferences.Editor = apply {
            removals += key
            updates -= key
        }
        override fun clear(): SharedPreferences.Editor = apply { clearFirst = true }
        override fun commit(): Boolean {
            val changed = mutableSetOf<String>()
            if (clearFirst) {
                changed += values.keys
                values.clear()
            }
            removals.forEach {
                if (values.remove(it) != null) changed += it
            }
            updates.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
                changed += key
            }
            changed.forEach { key ->
                listeners.forEach { it.onSharedPreferenceChanged(this@InMemorySharedPreferences, key) }
            }
            return true
        }
        override fun apply() {
            commit()
        }

        private fun put(key: String, value: Any?): SharedPreferences.Editor = apply {
            updates[key] = value
            removals -= key
        }
    }
}
