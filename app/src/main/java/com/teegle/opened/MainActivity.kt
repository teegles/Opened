package com.teegle.opened

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var store: FoldStore
    private lateinit var stateText: TextView
    private lateinit var angleText: TextView
    private lateinit var countText: TextView
    private lateinit var openTimeText: TextView
    private lateinit var foldedTimeText: TextView
    private lateinit var shareText: TextView
    private lateinit var trackingButton: Button
    private lateinit var resetButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FoldStore(this)
        setContentView(buildScreen())
        trackingButton.setOnClickListener {
            if (store.isTracking()) stopTracking() else requestPermissionAndStart()
        }
        resetButton.setOnClickListener {
            store.reset()
            render()
        }

        if (store.isTracking()) startForegroundService(Intent(this, FoldTrackingService::class.java))
    }

    override fun onStart() {
        super.onStart()
        handler.post(refresh)
    }

    override fun onStop() {
        handler.removeCallbacks(refresh)
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) startTracking()
    }

    private fun requestPermissionAndStart() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        } else {
            startTracking()
        }
    }

    private fun startTracking() {
        store.setTracking(true)
        startForegroundService(Intent(this, FoldTrackingService::class.java))
        render()
    }

    private fun stopTracking() {
        store.setTracking(false)
        stopService(Intent(this, FoldTrackingService::class.java))
        render()
    }

    private fun render() {
        val snapshot = store.snapshot()
        stateText.text = when (snapshot.state) {
            FoldState.FOLDED -> "Folded"
            FoldState.OPEN -> "Open"
            FoldState.UNKNOWN -> if (snapshot.tracking) "Waiting…" else "Not tracking"
        }
        angleText.text = snapshot.angle?.let { "${"%.1f".format(it)}° hinge angle" } ?: "No reading yet"
        countText.text = snapshot.todayUnfolds.toString()
        openTimeText.text = formatDuration(snapshot.todayOpenMs)
        foldedTimeText.text = formatDuration(snapshot.todayFoldedMs)

        val measured = snapshot.todayOpenMs + snapshot.todayFoldedMs
        shareText.text = if (measured == 0L) {
            "—"
        } else {
            "${(snapshot.todayOpenMs * 100 / measured)}% open"
        }

        trackingButton.text = if (snapshot.tracking) "Stop tracking" else "Start tracking"
        trackingButton.setBackgroundColor(
            if (snapshot.tracking) color(R.color.opened_stop) else color(R.color.opened_accent)
        )
        resetButton.visibility = if (snapshot.tracking) View.GONE else View.VISIBLE
    }

    private fun formatDuration(ms: Long): String {
        val totalMinutes = ms / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun buildScreen(): ScrollView {
        val scroll = ScrollView(this).apply { setBackgroundColor(color(R.color.opened_background)) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(48))
        }

        content.addView(text("Opened", 34f, color(R.color.opened_ink), true))
        content.addView(text("Your foldable, in perspective", 16f, color(R.color.opened_muted), false).bottom(30))

        content.addView(label("CURRENT STATE"))
        stateText = text("Waiting…", 30f, color(R.color.opened_ink), true)
        content.addView(stateText)
        angleText = text("No reading yet", 15f, color(R.color.opened_muted), false)
        content.addView(angleText.bottom(28))

        content.addView(label("TODAY"))
        content.addView(statRow("Unfolds", "", 0).also { countText = it.second }.first)
        content.addView(statRow("Time open", "", 1).also { openTimeText = it.second }.first)
        content.addView(statRow("Time folded", "", 2).also { foldedTimeText = it.second }.first)
        content.addView(statRow("Share", "", 3).also { shareText = it.second }.first.bottom(30))

        trackingButton = Button(this).apply {
            text = "Start tracking"
            setTextColor(color(R.color.opened_button_text))
            textSize = 16f
            isAllCaps = false
            minHeight = dp(54)
        }
        content.addView(trackingButton, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(12) })

        resetButton = Button(this).apply {
            text = "Reset all data"
            setTextColor(color(R.color.opened_danger))
            textSize = 15f
            isAllCaps = false
            setBackgroundColor(Color.TRANSPARENT)
        }
        content.addView(resetButton, LinearLayout.LayoutParams(-1, dp(52)))

        content.addView(
            text(
                "Tracking stays entirely on this phone. A quiet notification remains visible while tracking is active.",
                14f,
                color(R.color.opened_muted),
                false
            ).top(22)
        )

        scroll.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return scroll
    }

    private fun statRow(title: String, initial: String, index: Int): Pair<LinearLayout, TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(if (index == 0) 12 else 8), 0, dp(8))
        }
        val titleView = text(title, 17f, color(R.color.opened_muted), false)
        val valueView = text(initial, 20f, color(R.color.opened_ink), true).apply { gravity = Gravity.END }
        row.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(valueView, LinearLayout.LayoutParams(dp(150), -2))
        return row to valueView
    }

    private fun label(value: String) = text(value, 12f, color(R.color.opened_accent), true)

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        gravity = Gravity.START
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun TextView.bottom(value: Int): TextView = apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(value) }
    }

    private fun LinearLayout.bottom(value: Int): LinearLayout = apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(value) }
    }

    private fun TextView.top(value: Int): TextView = apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(value) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun color(resource: Int) = getColor(resource)

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 7
    }
}
