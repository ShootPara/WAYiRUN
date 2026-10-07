package com.example.runningapp.ui



import android.content.Context

import androidx.core.content.edit

import androidx.activity.compose.BackHandler

import androidx.compose.ui.res.painterResource

import com.example.runningapp.R

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

import com.example.runningapp.BuildConfig

import com.example.runningapp.account.AccountView

import com.example.runningapp.tracking.*

import java.time.Instant

import java.time.ZoneId

import java.time.format.DateTimeFormatter

import java.util.Locale

import kotlin.math.roundToInt



@Composable

fun WayirunApp(viewOverride: TrackingView? = null, onCommand: ((String, RunSettings?) -> Unit)? = null, onPermissions: () -> Unit = {},

    accountView: AccountView = AccountView(), onSignIn: () -> Unit = {}, onSignOut: () -> Unit = {},

    onImport: (String) -> Unit = {}, onRetrySync: () -> Unit = {},
    onAppPermissions: () -> Unit = {}, settingsError: String? = null,

    onStart: (RunSettings) -> Unit) {

    val context = LocalContext.current

    val preferences = remember { context.getSharedPreferences("local-settings", Context.MODE_PRIVATE) }

    val online by produceState(false, accountView.session) {
        while (true) {
            val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
            value = accountView.session?.expired() == false && connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            kotlinx.coroutines.delay(15000)
        }
    }
    val systemDark = isSystemInDarkTheme()

    var dark by rememberSaveable { mutableStateOf(preferences.getBoolean("dark", systemDark)) }

    var showSettings by remember { mutableStateOf(false) }

    BackHandler(showSettings) { showSettings = false }

    val liveView by TrackingService.view.collectAsState()

    val view = viewOverride ?: liveView

    val command = onCommand ?: { action: String, settings: RunSettings? -> TrackingService.send(context, action, settings,

        if (action == TrackingService.DISCARD) view.snapshot?.runId else null) }

    val colors = if (dark) darkColorScheme(primary = Color(0xFFB7F36B), secondaryContainer = Color(0xFF2B402C),

        onSecondaryContainer = Color(0xFFDBF2D7), background = Color(0xFF111914), surface = Color(0xFF19231D))

        else lightColorScheme(primary = Color(0xFF315F30), secondaryContainer = Color(0xFFDCE9D5),

            onSecondaryContainer = Color(0xFF1B351D), background = Color(0xFFF7F9F2), surface = Color(0xFFF7F9F2))

    MaterialTheme(colorScheme = colors) {

        Surface(Modifier.fillMaxSize()) {

            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                    Text("WAYiRUN", Modifier.weight(1f), fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)

                    IconButton(onClick = { showSettings = !showSettings }, modifier = Modifier.testTag("settings-gear")) {

                        Icon(painterResource(R.drawable.ic_settings), if (showSettings) "Close settings" else "Open settings")

                    }

                }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),

                verticalArrangement = Arrangement.spacedBy(18.dp)) {

                Spacer(Modifier.height(4.dp))

                if (showSettings) {

                    Text("Build ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)

                    Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("settings-screen"))

                    if (view.snapshot?.state in listOf(RunState.RUNNING, RunState.PAUSED, RunState.COUNTDOWN)) {

                        Text("Run settings apply to your next run. Your current run continues unchanged. Finish it before changing accounts or importing runs.")

                    }

                    val accountEnabled = !view.busy && view.snapshot?.state !in listOf(RunState.COUNTDOWN, RunState.RUNNING, RunState.PAUSED)
                    SettingsScreen(
                        busy = view.busy,
                        prefs = preferences,
                        dark = dark,
                        onDark = { dark = it; preferences.edit { putBoolean("dark", it) } },
                        accountView = accountView,
                        accountEnabled = accountEnabled,
                        onSignIn = onSignIn,
                        onSignOut = onSignOut,
                        onImport = onImport,
                        onRetrySync = onRetrySync,
                        onPermissions = onPermissions,
                        onAppPermissions = onAppPermissions,
                        settingsError = settingsError,
                    )

                } else {

                if (!view.ready) Text("Opening your run…", style = MaterialTheme.typography.titleLarge)

                view.error?.let { error ->

                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)

                }

                if (view.ready) {

                    val s = view.snapshot

                    when (s?.state) {

                        null, RunState.READY -> {

                            AccountPanel(accountView, !view.busy, onSignIn, onSignOut, showActions = false)

                            Setup(view.busy, preferences, dark, onStart, online = online)

                        }

                        RunState.COUNTDOWN -> {

                            Text("Get ready", style = MaterialTheme.typography.headlineLarge)

                            Text(((s.countdownRemainingMs + 999) / 1_000).toString(), fontSize = 88.sp, fontWeight = FontWeight.Bold)

                        }

                        RunState.RUNNING, RunState.PAUSED -> ActiveRun(view, online, dark) { action -> command(action, s.settings) }

                        RunState.FINISHED -> if (view.achievements.isNotEmpty()) {
                            AchievementCelebration(view.achievements) { command(TrackingService.DISMISS_COACHING, null) }
                        } else if (view.coaching.visible) {
                            CoachingAnimation(view.coaching.label) { command(TrackingService.DISMISS_COACHING, null) }
                        } else Summary(s, view.busy, view.error, { command(TrackingService.DISCARD, null) }) { command(TrackingService.NEW, null) }

                    }

                }

                }

                Spacer(Modifier.height(20.dp))

            }

            }

        }

    }

}



@Composable

private fun Setup(

    busy: Boolean, prefs: android.content.SharedPreferences, dark: Boolean,

    onStart: (RunSettings) -> Unit, online: Boolean = false,

) {

    var mode by rememberSaveable { mutableStateOf(prefs.getString("mode", "OUTDOOR")!!) }

    var units by rememberSaveable { mutableStateOf(prefs.getString("units", "")!!) }

    var goal by rememberSaveable { mutableStateOf(prefs.getString("goal", "None")!!) }

    var target by rememberSaveable { mutableStateOf(prefs.getString("goal-target", "")!!) }

    var countdown by rememberSaveable { mutableFloatStateOf(prefs.getInt("countdown", 0).toFloat()) }

    var stride by rememberSaveable { mutableStateOf(prefs.getString("stride-entry", "")!!) }

    var strideUnit by rememberSaveable { mutableStateOf(prefs.getString("stride-unit", "cm")!!) }

    var playlist by remember { mutableStateOf(prefs.getString("music-playlist", "").orEmpty()) }

    var announcementsEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("announcements-enabled", true)) }
    var autoPauseEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("auto-pause-enabled", true)) }
    var announcementSelection by remember { mutableStateOf(AnnouncementPreferences.read(prefs)) }

    val playlistContext = LocalContext.current

    RunSetupChoices(listOf("Outdoor" to "🌳", "Indoor" to "🏠"),
        if (mode == "OUTDOOR") "Outdoor" else "Indoor", "run-mode") {

        mode = it.uppercase(); prefs.edit { putString("mode", mode) }

    }

    RunSetupChoices(listOf("None" to "♾️", "Time" to "⏱️", "Distance" to "📏"), goal, "run-goal") {

        goal = it; target = prefs.getString("goal-target-$it", "").orEmpty()

        prefs.edit { putString("goal", goal); putString("goal-target", target) }

    }

    if (goal != "None") OutlinedTextField(target, { target = it; prefs.edit { putString("goal-target", it); putString("goal-target-$goal", it) } }, modifier = Modifier.fillMaxWidth().testTag("goal-target"),

        label = { Text(if (goal == "Time") "Target minutes" else "Target ${units.lowercase().ifEmpty { "distance" }}") },

        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)

    val strideValue = stride.toDoubleOrNull()

    val strideValid = stride.isBlank() || (strideValue != null && strideValue.isFinite() && strideValue > 0)

    val targetValue = target.toDoubleOrNull()

    val targetValid = goal == "None" || (targetValue != null && targetValue.isFinite() && targetValue > 0 &&

        (goal != "Distance" || (targetValue * 1_609.344).isFinite()) &&

        (goal != "Time" || targetValue * 60_000 in 1.0..Long.MAX_VALUE.toDouble()))

    if (!strideValid) Text("Enter a positive stride length, or leave it blank.", color = MaterialTheme.colorScheme.error)

    if (units.isEmpty() || !strideValid) Text("Open the gear to finish your run settings.")

    var playlistError by remember { mutableStateOf<String?>(null) }

    OutlinedButton(onClick = { playlistError = openPlaylist(playlistContext, playlist) },

        enabled = !busy && playlistLink(playlist) != null,

        modifier = Modifier.fillMaxWidth().testTag("open-playlist")) { Text("Open YouTube playlist") }

    playlistError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    RunStatusIndicators(RunMode.valueOf(mode), online, dark)

    Button(onClick = {

        val chosenUnits = if (units == "Miles") RunUnits.MILES else RunUnits.KILOMETERS

        val selectedGoal = when (goal) {

            "Time" -> RunGoal.Time((targetValue!! * 60_000).toLong())

            "Distance" -> RunGoal.Distance(targetValue!! * chosenUnits.metersPerUnit)

            else -> RunGoal.None

        }

        prefs.edit {

            putString("mode", mode); putString("units", units); putInt("countdown", countdown.roundToInt())

            putString("stride-entry", stride); putString("stride-unit", strideUnit)

        }

        onStart(RunSettings(RunMode.valueOf(mode), chosenUnits, countdown.roundToInt(), selectedGoal,

            strideValue?.times(if (strideUnit == "cm") 0.01 else 0.0254),
            announcementsEnabled = announcementsEnabled, autoPauseEnabled = autoPauseEnabled,
            announcementSelection = announcementSelection))

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

private fun ActiveRun(view: TrackingView, online: Boolean, dark: Boolean, command: (String) -> Unit) {

    val s = requireNotNull(view.snapshot)

    RunStatusIndicators(s.settings.mode, online, dark)

    val paused = s.state == RunState.PAUSED

    Text(if (s.pauseReason == PauseReason.AUTOMATIC) "Auto-paused" else if (paused) "Paused" else "Keep moving",
        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

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

    var coachingSelected by rememberSaveable(s.runId) { mutableStateOf(true) }
    if (s.pauseReason == PauseReason.AUTOMATIC) {
        TextButton(onClick = { command(TrackingService.PAUSE) }, enabled = !view.busy,
            modifier = Modifier.testTag("keep-paused")) { Text("Keep paused") }
    }
    if (paused) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = coachingSelected, onCheckedChange = { coachingSelected = it }, enabled = !view.busy,
                modifier = Modifier.testTag("finish-coaching"))
            Text("Post-run coaching")
        }
        FinishSwipe(!view.busy) { command(if (coachingSelected) TrackingService.FINISH else TrackingService.FINISH_WITHOUT_COACHING) }
    }

}



@Composable

private fun FinishSwipe(enabled: Boolean, label: String = "Swipe to finish", tag: String = "finish-slider", onFinish: () -> Unit) {

    var width by remember { mutableIntStateOf(0) }

    var drag by remember { mutableFloatStateOf(0f) }

    val thumb = with(LocalDensity.current) { 60.dp.toPx() }

    val maxDrag = (width - thumb).coerceAtLeast(1f)

    val latestFinish by rememberUpdatedState(onFinish)

    Box(Modifier.fillMaxWidth().height(68.dp).testTag(tag)

        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(34.dp)).onSizeChanged { width = it.width }

        .pointerInput(enabled, width) {

            detectHorizontalDragGestures(onDragEnd = {

                if (enabled && drag >= maxDrag * 0.9f) latestFinish()

                drag = 0f

            }, onDragCancel = { drag = 0f }) { change, amount ->

                if (enabled) { change.consume(); drag = (drag + amount).coerceIn(0f, maxDrag) }

            }

        }, contentAlignment = Alignment.Center) {

        Text(label, fontSize = 19.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)

        Surface(Modifier.align(Alignment.CenterStart).offset { IntOffset(drag.roundToInt(), 0) }.padding(4.dp).size(60.dp),

            color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(30.dp)) {

            Box(contentAlignment = Alignment.Center) { Text("→", fontSize = 30.sp) }

        }

    }

}



@Composable

private fun Summary(s: RunSnapshot, busy: Boolean, error: String?, onDiscard: () -> Unit, onNew: () -> Unit) {

    var confirmDiscard by rememberSaveable(s.runId) { mutableStateOf(false) }

    if (confirmDiscard) {

        AlertDialog(onDismissRequest = { if (!busy) confirmDiscard = false },

            title = { Text("ARE YOU SURE YOU WANT TO DISCARD THIS RUN??") },

            text = { Text(error ?: "This deletes the run from this phone. Any cloud copy is removed when signed in and connected. Achievements and records are recalculated.") },

            confirmButton = {

                FinishSwipe(!busy, "Slide to delete", "discard-slider") { onDiscard() }

            },

            dismissButton = {

                TextButton(onClick = { confirmDiscard = false }, enabled = !busy) { Text("Cancel") }

            })

    }

    com.example.runningapp.photos.RunPhotoButton(s)

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

    com.example.runningapp.sharing.RunSharing(s.runId)

    OutlinedButton(onClick = { confirmDiscard = true }, enabled = !busy,

        modifier = Modifier.fillMaxWidth().testTag("discard-run")) { Text("Discard run") }

    Button(onClick = onNew, enabled = !busy, modifier = Modifier.fillMaxWidth().height(64.dp).testTag("new-run")) { Text("NEW RUN", fontSize = 21.sp) }

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
