package com.example.runningapp.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runningapp.domain.*
import com.example.runningapp.tracking.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WayirunApp(viewOverride: TrackingView? = null, onCommand: ((String, RunSettings?) -> Unit)? = null, onStart: (RunSettings) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("local-settings", Context.MODE_PRIVATE) }
    val systemDark = isSystemInDarkTheme()
    var dark by rememberSaveable { mutableStateOf(preferences.getBoolean("dark", systemDark)) }
    val liveView by TrackingService.view.collectAsState()
    val view = viewOverride ?: liveView
    val command = onCommand ?: { action: String, settings: RunSettings? -> TrackingService.send(context, action, settings) }
    val colors = if (dark) darkColorScheme(primary = Color(0xFFB7F36B), background = Color(0xFF111914), surface = Color(0xFF19231D))
        else lightColorScheme(primary = Color(0xFF315F30), background = Color(0xFFF7F9F2), surface = Color(0xFFF7F9F2))
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Spacer(Modifier.height(4.dp))
                Text("WAYiRUN", fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                if (!view.ready) Text("Opening your run…", style = MaterialTheme.typography.titleLarge)
                view.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                }
                if (view.ready) {
                    val s = view.snapshot
                    when (s?.state) {
                        null, RunState.READY -> Setup(view.busy, preferences, dark, {
                            dark = it; preferences.edit().putBoolean("dark", it).apply()
                        }, onStart)
                        RunState.COUNTDOWN -> {
                            Text("Get ready", style = MaterialTheme.typography.headlineLarge)
                            Text(((s.countdownRemainingMs + 999) / 1_000).toString(), fontSize = 88.sp, fontWeight = FontWeight.Bold)
                        }
                        RunState.RUNNING, RunState.PAUSED -> ActiveRun(view) { action -> command(action, s.settings) }
                        RunState.FINISHED -> Summary(s) { command(TrackingService.NEW, null) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun Setup(
    busy: Boolean, prefs: android.content.SharedPreferences, dark: Boolean,
    onDark: (Boolean) -> Unit, onStart: (RunSettings) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(prefs.getString("mode", "OUTDOOR")!!) }
    var units by rememberSaveable { mutableStateOf(prefs.getString("units", "")!!) }
    var goal by rememberSaveable { mutableStateOf("None") }
    var target by rememberSaveable { mutableStateOf("") }
    var countdown by rememberSaveable { mutableStateOf(prefs.getInt("countdown", 0).toFloat()) }
    var stride by rememberSaveable { mutableStateOf(prefs.getString("stride-entry", "")!!) }
    var strideUnit by rememberSaveable { mutableStateOf(prefs.getString("stride-unit", "cm")!!) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    Text("Your next run", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Choices(listOf("Outdoor", "Indoor"), if (mode == "OUTDOOR") "Outdoor" else "Indoor") { mode = it.uppercase() }
    Text("Distance units", style = MaterialTheme.typography.titleMedium)
    Choices(listOf("Miles", "Kilometers"), units) { units = it }
    Text("Goal", style = MaterialTheme.typography.titleMedium)
    Choices(listOf("None", "Distance", "Time"), goal) { goal = it; target = "" }
    if (goal != "None") OutlinedTextField(target, { target = it }, modifier = Modifier.fillMaxWidth().testTag("goal-target"),
        label = { Text(if (goal == "Time") "Target minutes" else "Target ${units.lowercase().ifEmpty { "distance" }}") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
    Column {
        Text("Countdown · ${countdown.roundToInt()} seconds", style = MaterialTheme.typography.titleMedium)
        Slider(countdown, { countdown = it }, valueRange = 0f..10f, steps = 9)
    }
    TextButton(onClick = { showSettings = !showSettings }) { Text(if (showSettings) "Close run settings" else "Run settings · Stride & appearance", fontSize = 17.sp) }
    if (showSettings) {
        Choices(listOf("cm", "inches"), strideUnit) { next ->
            val value = stride.toDoubleOrNull()
            if (value != null && value.isFinite()) stride = String.format(Locale.US, "%.3f", if (next == strideUnit) value else if (next == "cm") value * 2.54 else value / 2.54)
            strideUnit = next
        }
        OutlinedTextField(stride, { stride = it }, modifier = Modifier.fillMaxWidth().testTag("stride"),
            label = { Text("Distance per step ($strideUnit)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Text("Enter your measured distance per step. Leave blank to use GPS or time only.", style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(dark, onDark); Spacer(Modifier.width(12.dp)); Text("Dark mode")
        }
    }
    val strideValue = stride.toDoubleOrNull()
    val strideValid = stride.isBlank() || (strideValue != null && strideValue.isFinite() && strideValue > 0)
    val targetValue = target.toDoubleOrNull()
    val targetValid = goal == "None" || (targetValue != null && targetValue.isFinite() && targetValue > 0 &&
        (goal != "Distance" || (targetValue * 1_609.344).isFinite()) &&
        (goal != "Time" || targetValue * 60_000 in 1.0..Long.MAX_VALUE.toDouble()))
    if (!strideValid) Text("Enter a positive stride length, or leave it blank.", color = MaterialTheme.colorScheme.error)
    if (stride.isBlank()) Text("Step-based distance needs a stride length in Run settings.", style = MaterialTheme.typography.bodyMedium)
    Text("${if (mode == "OUTDOOR") "Outdoor" else "Indoor"} · Fallback", fontWeight = FontWeight.SemiBold)
    Text("Runs are saved on this phone. If distance is unavailable, the timer can still run.", style = MaterialTheme.typography.bodyMedium)
    Button(onClick = {
        val chosenUnits = if (units == "Miles") RunUnits.MILES else RunUnits.KILOMETERS
        val selectedGoal = when (goal) {
            "Time" -> RunGoal.Time((targetValue!! * 60_000).toLong())
            "Distance" -> RunGoal.Distance(targetValue!! * chosenUnits.metersPerUnit)
            else -> RunGoal.None
        }
        prefs.edit().putString("mode", mode).putString("units", units).putInt("countdown", countdown.roundToInt())
            .putString("stride-entry", stride).putString("stride-unit", strideUnit).apply()
        onStart(RunSettings(RunMode.valueOf(mode), chosenUnits, countdown.roundToInt(), selectedGoal,
            strideValue?.times(if (strideUnit == "cm") 0.01 else 0.0254)))
    }, enabled = !busy && units.isNotEmpty() && strideValid && targetValid,
        modifier = Modifier.fillMaxWidth().height(68.dp).testTag("start")) {
        Text("START RUNNING", fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Choices(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected == option, { onSelect(option) }, label = { Text(option, fontSize = 16.sp) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActiveRun(view: TrackingView, command: (String) -> Unit) {
    val s = requireNotNull(view.snapshot)
    val paused = s.state == RunState.PAUSED
    Text(if (paused) "Paused" else "Keep moving", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    if (view.interrupted) Text("Tracking was interrupted. Your saved progress is here. Resume or finish when you're ready.", style = MaterialTheme.typography.bodyLarge)
    Metric("Active time", time(s.activeDurationMs), true)
    Metric("Distance · ${unitLabel(s.settings.units)}", number(s.distanceMeters / s.settings.units.metersPerUnit))
    if (!paused && !view.distanceAvailable) Text("Distance unavailable", style = MaterialTheme.typography.titleMedium)
    Metric("Average pace · /${unitLabel(s.settings.units)}", pace(s.averagePaceMsPerUnit))
    if (s.goalReached) Text("Goal reached · Keep going", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    if (s.splits.isNotEmpty()) {
        val last = s.splits.last()
        Text("Split ${last.number} · ${time(last.durationMs)}", style = MaterialTheme.typography.titleLarge)
    }
    Button(onClick = { command(if (paused) TrackingService.RESUME else TrackingService.PAUSE) }, enabled = !view.busy,
        modifier = Modifier.fillMaxWidth().height(72.dp).testTag(if (paused) "resume" else "pause")) {
        Text(if (paused) "RESUME" else "PAUSE", fontSize = 25.sp, fontWeight = FontWeight.Bold)
    }
    if (paused) FinishSwipe(!view.busy) { command(TrackingService.FINISH) }
}

@Composable
private fun FinishSwipe(enabled: Boolean, onFinish: () -> Unit) {
    var width by remember { mutableIntStateOf(0) }
    var drag by remember { mutableFloatStateOf(0f) }
    val thumb = with(LocalDensity.current) { 60.dp.toPx() }
    val maxDrag = (width - thumb).coerceAtLeast(1f)
    val latestFinish by rememberUpdatedState(onFinish)
    Box(Modifier.fillMaxWidth().height(68.dp).testTag("finish-slider")
        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(34.dp)).onSizeChanged { width = it.width }
        .pointerInput(enabled, width) {
            detectHorizontalDragGestures(onDragEnd = {
                if (enabled && drag >= maxDrag * 0.9f) latestFinish()
                drag = 0f
            }, onDragCancel = { drag = 0f }) { change, amount ->
                if (enabled) { change.consume(); drag = (drag + amount).coerceIn(0f, maxDrag) }
            }
        }, contentAlignment = Alignment.Center) {
        Text("Swipe to finish", fontSize = 19.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Surface(Modifier.align(Alignment.CenterStart).offset { IntOffset(drag.roundToInt(), 0) }.padding(4.dp).size(60.dp),
            color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(30.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("→", fontSize = 30.sp) }
        }
    }
}

@Composable
private fun Summary(s: RunSnapshot, onNew: () -> Unit) {
    Text("Run saved", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    s.startedUtcMs?.let { Text(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))) }
    Text("${if (s.settings.mode == RunMode.INDOOR) "Indoor" else "Outdoor"} · Saved on this phone")
    Metric("Active time", time(s.activeDurationMs), true)
    Metric("Distance · ${unitLabel(s.settings.units)}", number(s.distanceMeters / s.settings.units.metersPerUnit))
    Metric("Average pace · /${unitLabel(s.settings.units)}", pace(s.averagePaceMsPerUnit))
    if (s.splits.isNotEmpty() || s.partialSplit() != null) Text("Splits", style = MaterialTheme.typography.titleLarge)
    s.splits.forEach { Text("${it.number} ${unitLabel(s.settings.units)}   ·   ${time(it.durationMs)}", fontSize = 20.sp) }
    s.partialSplit()?.let {
        Text("Partial · ${number(it.distanceMeters / s.settings.units.metersPerUnit)} ${unitLabel(s.settings.units)}", fontSize = 20.sp)
        Text("${time(it.durationMs)} · ${pace(it.paceMsPerUnit)}/${unitLabel(s.settings.units)}", fontSize = 18.sp)
    }
    Button(onClick = onNew, modifier = Modifier.fillMaxWidth().height(64.dp).testTag("new-run")) { Text("NEW RUN", fontSize = 21.sp) }
}

@Composable
private fun Metric(label: String, value: String, large: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = if (large) 54.sp else 38.sp, fontWeight = FontWeight.Bold)
    }
}
private fun unitLabel(units: RunUnits) = if (units == RunUnits.MILES) "mi" else "km"
private fun number(value: Double) = String.format(Locale.getDefault(), "%.2f", value)
private fun pace(value: Double?) = value?.takeIf { it.isFinite() && it >= 0 }?.let { time(it.toLong()) } ?: "—"
private fun time(ms: Long): String {
    val seconds = ms / 1_000
    return if (seconds >= 3_600) String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}
