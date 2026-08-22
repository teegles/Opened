package com.teegle.opened

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

class MainActivity : ComponentActivity() {
    private lateinit var store: FoldStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = FoldStore(this)
        if (store.isTracking()) startForegroundService(Intent(this, FoldTrackingService::class.java))

        setContent {
            FoldCountTheme {
                FoldCountApp(store, ::startTracking, ::stopTracking)
            }
        }
    }

    private fun startTracking() {
        store.setTracking(true)
        startForegroundService(Intent(this, FoldTrackingService::class.java))
    }

    private fun stopTracking() {
        store.setTracking(false)
        stopService(Intent(this, FoldTrackingService::class.java))
    }
}

@Composable
private fun FoldCountTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    val colors = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun FoldCountApp(store: FoldStore, startTracking: () -> Unit, stopTracking: () -> Unit) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(store.snapshot()) }
    var week by remember { mutableStateOf(store.lastSevenDays()) }
    var lifetime by remember { mutableStateOf(store.lifetimeUsage()) }
    var period by remember { mutableStateOf(UsagePeriod.TODAY) }
    var showResetDialog by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startTracking()
        snapshot = store.snapshot()
    }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = store.snapshot()
            week = store.lastSevenDays()
            lifetime = store.lifetimeUsage()
            delay(1_000)
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset all statistics?") },
            text = { Text("This permanently removes unfold counts and screen-time history from this phone.") },
            confirmButton = {
                Button(onClick = {
                    store.reset()
                    snapshot = store.snapshot()
                    week = store.lastSevenDays()
                    lifetime = store.lifetimeUsage()
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Fold Count",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Opened and closed screen time",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { StateCard(snapshot) }
            item {
                UsageCard(
                    snapshot = snapshot,
                    days = week,
                    lifetime = lifetime,
                    period = period,
                    onSelectPeriod = { period = it }
                )
            }
            item {
                if (snapshot.tracking) {
                    OutlinedButton(
                        onClick = {
                            stopTracking()
                            snapshot = store.snapshot()
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("Stop tracking") }
                } else {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33 &&
                                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                startTracking()
                                snapshot = store.snapshot()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("Start tracking") }
                }
            }
            if (!snapshot.tracking) {
                item {
                    FilledTonalButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Reset all data") }
                }
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "Everything stays on this phone. Fold Count has no internet, location, or app-usage access.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        "https://github.com/teegles/Fold-Count/blob/main/PRIVACY.md".toUri()
                                    )
                                )
                            }
                        ) { Text("Privacy policy") }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun StateCard(snapshot: FoldSnapshot) {
    val stateLabel = when (snapshot.state) {
        FoldState.FOLDED -> "Closed"
        FoldState.OPEN -> "Opened"
        FoldState.UNKNOWN -> if (snapshot.tracking) "Waiting…" else "Not tracking"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FoldGlyph(snapshot.state)
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    "CURRENT STATE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
                Text(
                    stateLabel,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    snapshot.angle?.let { "${"%.1f".format(it)}° hinge angle" } ?: "No reading yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                )
            }
        }
    }
}

@Composable
private fun FoldGlyph(state: FoldState) {
    val color = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(shape = CircleShape, color = color.copy(alpha = 0.12f)) {
        Canvas(Modifier.size(64.dp).padding(14.dp)) {
            if (state == FoldState.FOLDED) {
                drawRoundRect(
                    color,
                    Offset(size.width * 0.2f, 0f),
                    Size(size.width * 0.6f, size.height),
                    CornerRadius(5f, 5f),
                    style = Stroke(width = 3.5f)
                )
                return@Canvas
            }
            val gap = size.width * 0.22f
            val panelWidth = (size.width - gap) / 2f
            drawRoundRect(
                color, Offset.Zero, Size(panelWidth, size.height), CornerRadius(5f, 5f),
                style = Stroke(width = 3.5f)
            )
            drawRoundRect(
                color, Offset(panelWidth + gap, 0f), Size(panelWidth, size.height), CornerRadius(5f, 5f),
                style = Stroke(width = 3.5f)
            )
        }
    }
}

@Composable
private fun UsageCard(
    snapshot: FoldSnapshot,
    days: List<DailyUsage>,
    lifetime: LifetimeUsage,
    period: UsagePeriod,
    onSelectPeriod: (UsagePeriod) -> Unit
) {
    val openMs = when (period) {
        UsagePeriod.TODAY -> snapshot.todayOpenMs
        UsagePeriod.SEVEN_DAYS -> days.sumOf { it.openMs }
        UsagePeriod.ALL_TIME -> lifetime.openMs
    }
    val closedMs = when (period) {
        UsagePeriod.TODAY -> snapshot.todayFoldedMs
        UsagePeriod.SEVEN_DAYS -> days.sumOf { it.foldedMs }
        UsagePeriod.ALL_TIME -> lifetime.foldedMs
    }
    val unfolds = when (period) {
        UsagePeriod.TODAY -> snapshot.todayUnfolds
        UsagePeriod.SEVEN_DAYS -> days.sumOf { it.unfolds }
        UsagePeriod.ALL_TIME -> lifetime.unfolds
    }
    val total = openMs + closedMs
    val fraction = if (total > 0) openMs.toFloat() / total else 0f
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            PeriodToggle(period, onSelectPeriod)
            if (period == UsagePeriod.ALL_TIME) {
                val locale = LocalConfiguration.current.locales[0]
                val formatter = remember(locale) {
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                }
                Text(
                    "Since ${lifetime.started.format(formatter)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "$unfolds unfolds",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Opened screen", formatDuration(openMs), Modifier.weight(1f))
                Metric("Closed screen", formatDuration(closedMs), Modifier.weight(1f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Opened time", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (total == 0L) "—" else "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
            if (period == UsagePeriod.SEVEN_DAYS) {
                WeeklyBars(days)
            }
        }
    }
}

private enum class UsagePeriod { TODAY, SEVEN_DAYS, ALL_TIME }

@Composable
private fun PeriodToggle(period: UsagePeriod, onSelectPeriod: (UsagePeriod) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val indicatorOffset by animateDpAsState(
                targetValue = maxWidth / 3 * period.ordinal,
                animationSpec = tween(durationMillis = 280),
                label = "period indicator"
            )
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxWidth / 3)
                    .offset { IntOffset(indicatorOffset.roundToPx(), 0) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {}
            Row(Modifier.fillMaxSize().selectableGroup()) {
                PeriodOption(
                    label = "Today",
                    selected = period == UsagePeriod.TODAY,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onSelectPeriod(UsagePeriod.TODAY) }
                )
                PeriodOption(
                    label = "7 Days",
                    selected = period == UsagePeriod.SEVEN_DAYS,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onSelectPeriod(UsagePeriod.SEVEN_DAYS) }
                )
                PeriodOption(
                    label = "All Time",
                    selected = period == UsagePeriod.ALL_TIME,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onSelectPeriod(UsagePeriod.ALL_TIME) }
                )
            }
        }
    }
}

@Composable
private fun PeriodOption(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.selectable(
            selected = selected,
            onClick = onClick,
            role = Role.Tab
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WeeklyBars(days: List<DailyUsage>) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMM d", locale) }
    val primary = MaterialTheme.colorScheme.primary
    val closed = MaterialTheme.colorScheme.secondaryContainer
    val max = days.maxOfOrNull { it.openMs + it.foldedMs }?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Screen-on time by day",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        days.asReversed().forEach { day ->
            val total = day.openMs + day.foldedMs
            val usageFraction = total.toFloat() / max.toFloat()
            val openedFraction = if (total > 0) day.openMs.toFloat() / total else 0f
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} · " +
                            day.date.format(dateFormatter),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${formatDuration(total)} total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                ) {
                    val barWidth = maxWidth * usageFraction
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(barWidth)
                            .background(closed, CircleShape)
                    )
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(barWidth * openedFraction)
                            .background(primary, CircleShape)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    LegendDot(primary, "Opened ${formatDuration(day.openMs)}")
                    LegendDot(closed, "Closed ${formatDuration(day.foldedMs)}")
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    if (ms > 0 && totalMinutes == 0L) return "<1m"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
