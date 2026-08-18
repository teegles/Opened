package com.teegle.opened

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
    private lateinit var percentageBar: PercentageBarView
    private lateinit var weeklyChart: WeeklyChartView
    private lateinit var appsContainer: LinearLayout
    private lateinit var appAccessText: TextView
    private lateinit var trackingButton: Button
    private lateinit var resetButton: Button
    private var dashboardReady = false

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            if (dashboardReady) render()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FoldStore(this)
        if (getSharedPreferences("opened_ui", MODE_PRIVATE).getBoolean(KEY_USAGE_INTRO_SEEN, false)) {
            showDashboard()
        } else {
            setContentView(buildUsageIntro())
        }

        if (store.isTracking()) startForegroundService(Intent(this, FoldTrackingService::class.java))
    }

    override fun onResume() {
        super.onResume()
        if (dashboardReady) render()
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
        percentageBar.setUsage(snapshot.todayOpenMs, snapshot.todayFoldedMs)
        weeklyChart.setDays(store.lastSevenDays())
        renderApps()

        trackingButton.text = if (snapshot.tracking) "Stop tracking" else "Start tracking"
        trackingButton.setBackgroundColor(
            if (snapshot.tracking) color(R.color.opened_stop) else color(R.color.opened_accent)
        )
        resetButton.visibility = if (snapshot.tracking) View.GONE else View.VISIBLE
    }

    private fun formatDuration(ms: Long): String {
        val totalMinutes = ms / 60_000
        if (ms > 0 && totalMinutes == 0L) return "<1m"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun showDashboard() {
        dashboardReady = true
        setContentView(buildDashboard())
        trackingButton.setOnClickListener {
            if (store.isTracking()) stopTracking() else requestPermissionAndStart()
        }
        resetButton.setOnClickListener {
            store.reset()
            render()
        }
        render()
    }

    private fun buildUsageIntro(): ScrollView {
        val scroll = ScrollView(this).apply { setBackgroundColor(color(R.color.opened_background)) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(64), dp(28), dp(48))
        }
        content.addView(text("More detail, when you want it", 30f, color(R.color.opened_ink), true).bottom(18))
        content.addView(
            text(
                "Opened can show which apps you use on the inner screen. To do that, Android requires optional Usage Access.",
                17f,
                color(R.color.opened_muted),
                false
            ).bottom(18)
        )
        content.addView(
            text(
                "Only time spent with the phone open and the screen on is included. App details stay on this phone and are never sent anywhere.",
                16f,
                color(R.color.opened_muted),
                false
            ).bottom(32)
        )
        val enable = actionButton("Enable app details") {
            markUsageIntroSeen()
            showDashboard()
            startActivity(
                Intent(
                    Settings.ACTION_USAGE_ACCESS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        content.addView(enable, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(10) })
        val skip = actionButton("Continue without app details", transparent = true) {
            markUsageIntroSeen()
            showDashboard()
        }
        content.addView(skip, LinearLayout.LayoutParams(-1, dp(54)))
        scroll.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return scroll
    }

    private fun buildDashboard(): ScrollView {
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
        content.addView(statRow("Screen time open", "", 1).also { openTimeText = it.second }.first)
        content.addView(statRow("Screen time folded", "", 2).also { foldedTimeText = it.second }.first)
        content.addView(statRow("Share of screen time", "", 3).also { shareText = it.second }.first)
        percentageBar = PercentageBarView(this)
        content.addView(percentageBar, LinearLayout.LayoutParams(-1, dp(16)).apply { bottomMargin = dp(34) })

        content.addView(label("LAST 7 DAYS"))
        weeklyChart = WeeklyChartView(this)
        content.addView(weeklyChart, LinearLayout.LayoutParams(-1, dp(190)))
        content.addView(
            text("Green: open screen time   Gray: folded screen time", 13f, color(R.color.opened_muted), false)
                .bottom(32)
        )

        content.addView(label("APPS USED WHILE OPEN"))
        appAccessText = text("", 14f, color(R.color.opened_muted), false)
        content.addView(appAccessText.top(8))
        appsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(appsContainer, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val accessButton = actionButton("Manage Usage Access", transparent = true) {
            startActivity(
                Intent(
                    Settings.ACTION_USAGE_ACCESS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        content.addView(accessButton, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(24) })

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

    private fun renderApps() {
        appsContainer.removeAllViews()
        if (!UsageAccess.isGranted(this)) {
            appAccessText.text = "Optional Usage Access is off. Basic fold statistics still work."
            return
        }
        val apps = store.appUsageFor().take(6)
        appAccessText.text = if (apps.isEmpty()) {
            "No open-screen app usage recorded yet."
        } else {
            "Today"
        }
        apps.forEach { usage ->
            val label = runCatching {
                val info = packageManager.getApplicationInfo(usage.packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(usage.packageName)
            appsContainer.addView(statRow(label, formatDuration(usage.durationMs), 1).first)
        }
    }

    private fun actionButton(label: String, transparent: Boolean = false, action: () -> Unit) =
        Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            setTextColor(if (transparent) color(R.color.opened_accent) else color(R.color.opened_button_text))
            setBackgroundColor(if (transparent) Color.TRANSPARENT else color(R.color.opened_accent))
            setOnClickListener { action() }
        }

    private fun markUsageIntroSeen() {
        getSharedPreferences("opened_ui", MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USAGE_INTRO_SEEN, true)
            .apply()
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
        private const val KEY_USAGE_INTRO_SEEN = "usage_intro_seen"
    }
}
