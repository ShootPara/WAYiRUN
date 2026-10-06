package com.example.runningapp.tracking

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.content.edit
import com.example.runningapp.MainActivity
import com.example.runningapp.R
import com.example.runningapp.domain.*
import com.example.runningapp.storage.*
import com.example.runningapp.account.SessionStore
import com.example.runningapp.sync.SyncScheduler
import com.example.runningapp.coaching.CoachingView
import com.example.runningapp.coaching.PostRunCoaching
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime
import java.util.UUID

data class TrackingView(
    val snapshot: RunSnapshot? = null, val interrupted: Boolean = false, val distanceAvailable: Boolean = false,
    val ready: Boolean = false, val busy: Boolean = false, val error: String? = null,
    val coaching: CoachingView = CoachingView(),
    val achievements: List<Achievement> = emptyList(),
)

/** All commands, measurements and disk commits pass through one serial consumer. */
class TrackingService : Service() {
    private sealed interface Message {
        data class Command(val action: String, val settings: RunSettings? = null,
            val expectedRunId: String? = null) : Message
        data object Tick : Message
        data object AudioIdle : Message
        data class Gps(val generation: Long, val fix: GpsFix) : Message
        data class Steps(val generation: Long, val time: Long, val count: Long) : Message
        data class StepDetected(val generation: Long, val time: Long, val received: Long) : Message
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val messages = Channel<Message>(Channel.UNLIMITED)
    private val clock = RunClock { RunTime(SystemClock.elapsedRealtime(), System.currentTimeMillis()) }
    private lateinit var sensors: SensorAdapters
    private lateinit var cues: RunCues
    private lateinit var coaching: PostRunCoaching
    private lateinit var repository: RunRepository
    private var actionError: String? = null
    private var finishAwards: List<Achievement> = emptyList()
    private lateinit var wakeLock: PowerManager.WakeLock
    private var controller: RunController? = null
    private var input: TrackingInput? = null
    private var lastSaved: RunCheckpoint? = null
    private var owner = ""
    private var localOwner = ""
    private var cloudOwner: String? = null
    private var zone = ""
    private var offset = 0
    private var interrupted = false
    private var listening = false
    private var loaded = false
    private var foregroundStarted = false
    private var notificationChannel = "run-tracking"
    private var permissionSignature = ""

    override fun onCreate() {
        super.onCreate()
        sensors = sensorFactory(this)
        cues = RunCues(this) { messages.trySend(Message.AudioIdle) }
        coaching = PostRunCoaching(this, scope, { cues.isPlaying }) {
            if (loaded) publish()
            messages.trySend(Message.AudioIdle)
        }
        repository = RunRepository(RunDatabase.get(this).runs())
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WAYiRUN:tracking")
        wakeLock.setReferenceCounted(false)
        val preferences = getSharedPreferences("local-settings", MODE_PRIVATE)
        owner = preferences.getString("local-owner", null) ?: UUID.randomUUID().toString().also {
            preferences.edit { putString("local-owner", it) }
        }
        localOwner = owner
        notificationChannel = ensureTrackingChannel(this)
        scope.launch {
            for (message in messages) {
                try {
                    if (!loaded) load()
                    handle(message)
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) {
                    // Stop measurement intake and retain the most recent successfully committed data.
                    sensors.stop(); listening = false
                    input?.reset()
                    controller = lastSaved?.let { RunController.recover(it, clock) }
                    input = controller?.let { TrackingInput(it, clock) }
                    releaseWakeLock()
                    view.value = TrackingView(controller?.snapshot(), true, false, true, false,
                        "Couldn't save this update. Tracking is paused. Free some storage, then try again.")
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(1_000)
                val snapshot = controller?.snapshot()
                if (snapshot?.state in listOf(RunState.RUNNING, RunState.COUNTDOWN) || snapshot?.pauseReason == PauseReason.AUTOMATIC)
                    messages.send(Message.Tick)
            }
        }
    }

    private suspend fun load() {
        val stored = repository.active()
        if (stored != null) {
            val saved = stored.decode()
            controller = RunController.recover(saved, clock)
            input = TrackingInput(requireNotNull(controller), clock)
            owner = stored.ownerId; cloudOwner = stored.cloudOwnerId; zone = stored.zoneId; offset = stored.startOffsetSeconds
            interrupted = stored.interrupted || saved.snapshot.state == RunState.RUNNING ||
                saved.snapshot.pauseReason == PauseReason.AUTOMATIC
            lastSaved = saved
            if (saved.snapshot.state != RunState.FINISHED) {
                repository.save(controller!!.checkpoint(), owner, zone, offset, interrupted, cloudOwnerId = cloudOwner)
                lastSaved = controller!!.checkpoint()
            }
        }
        loaded = true
        publish()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: OPEN
        val settings = intent?.getStringExtra(SETTINGS)?.let { Json.decodeFromString<RunSettings>(it) }
        val mayStart = action == START && (!loaded || controller == null)
        val mayResume = action == RESUME && (!loaded || controller?.snapshot()?.state == RunState.PAUSED)
        if (mayStart || mayResume) {
            try { promote(settings?.mode ?: controller?.snapshot()?.settings?.mode ?: RunMode.INDOOR) }
            catch (_: RuntimeException) {
                view.value = view.value.copy(busy = false, error = "Open WAYiRUN and try starting again. Android couldn't start tracking.")
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        messages.trySend(Message.Command(action, settings, intent?.getStringExtra(RUN_ID)))
        return START_NOT_STICKY
    }

    private suspend fun handle(message: Message) {
        if (message is Message.Command && message.action != ENTER && message.expectedRunId != null &&
            message.expectedRunId != controller?.snapshot()?.runId) {
            if (controller == null && foregroundStarted) { stopForeground(STOP_FOREGROUND_REMOVE); foregroundStarted = false }
            publish(); return
        }
        if (message is Message.Command && message.action != OPEN) actionError = null
        var result: TrackingResult? = null
        // Reconcile permission/provider changes before accepting queued sensor generations.
        syncSensors()
        when (message) {
            is Message.Command -> when (message.action) {
                DISCARD -> {
                    val saved = controller?.snapshot()
                    if (saved?.state == RunState.FINISHED && message.expectedRunId == saved.runId) {
                        try {
                            check(repository.discard(saved.runId, owner))
                            com.example.runningapp.health.HealthScheduler.enqueue(this)
                            coaching.cancel()
                            SyncScheduler.enqueue(this)
                            cues.cancel()
                            controller = null; input = null; lastSaved = null; interrupted = false
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) {
                            actionError = "Couldn't discard this run. It is still saved. Please try again."
                        }
                    }
                }
                OPEN, ENTER -> {
                    val selected = withContext(Dispatchers.IO) { SessionStore(this@TrackingService).read()?.ownerId }
                    if (controller?.snapshot()?.state == RunState.FINISHED) {
                        val stored = RunDatabase.get(this).runs().get(controller!!.snapshot().runId)
                        if (stored == null || (stored.cloudOwnerId != null && stored.cloudOwnerId != selected) ||
                            (message.action == ENTER && message.expectedRunId != stored.id)) {
                            clearFinishedRun()
                        } else cloudOwner = stored.cloudOwnerId
                    }
                    // An external photo return can restore its exact owned run, never the latest run.
                    if (message.action == ENTER && controller == null && message.expectedRunId != null) {
                        val stored = RunDatabase.get(this).runs().get(message.expectedRunId)
                        if (stored?.state == RunState.FINISHED.name &&
                            (stored.cloudOwnerId == selected && (selected != null || stored.ownerId == localOwner))) {
                            val saved = stored.decode()
                            controller = RunController.recover(saved, clock)
                            input = TrackingInput(requireNotNull(controller), clock)
                            owner = stored.ownerId; cloudOwner = stored.cloudOwnerId
                            zone = stored.zoneId; offset = stored.startOffsetSeconds
                            lastSaved = saved; interrupted = false
                        }
                    }
                    // Recovered paused runs also need a notification entry back to their controls.
                    if (controller?.snapshot()?.state == RunState.PAUSED && !foregroundStarted) {
                        promote(controller!!.snapshot().settings.mode)
                    }
                    if (controller?.snapshot()?.state == RunState.RUNNING) {
                        promote(controller!!.snapshot().settings.mode)
                        if (permissionSignature != capabilities()) {
                            sensors.stop(); listening = false; input?.reset()
                            controller!!.clearSource()
                        }
                        syncSensors()
                    }
                }
                NEW -> if (controller?.snapshot()?.state == RunState.FINISHED) {
                    clearFinishedRun()
                }
                START -> if (controller == null) {
                    val settings = requireNotNull(message.settings)
                    cloudOwner = withContext(Dispatchers.IO) { SessionStore(this@TrackingService).read()?.ownerId }
                    owner = cloudOwner ?: localOwner
                    val date = ZonedDateTime.now()
                    zone = date.zone.id; offset = date.offset.totalSeconds
                    controller = RunController(UUID.randomUUID().toString(), settings, clock)
                    input = TrackingInput(controller!!, clock)
                    interrupted = false
                    result = TrackingResult(controller!!.start())
                }
                PAUSE -> if (controller != null) {
                    sensors.stop(); listening = false; input?.reset()
                    result = TrackingResult(controller!!.pause())
                }
                RESUME -> if (controller != null) {
                    if (controller!!.snapshot().state == RunState.PAUSED && !foregroundStarted) promote(controller!!.snapshot().settings.mode)
                    interrupted = false
                    input?.reset()
                    result = TrackingResult(controller!!.resume())
                }
                DISMISS_COACHING -> { coaching.dismiss(); finishAwards=emptyList() }
                FINISH, FINISH_WITHOUT_COACHING -> if (controller != null) {
                    sensors.stop(); listening = false; input?.reset()
                    result = TrackingResult(controller!!.finish())
                }
            }
            Message.Tick -> result = input?.tick()
            // Audio completion still releases the finished run's foreground notification below.
            Message.AudioIdle -> Unit
            is Message.Gps -> if (message.generation == sensors.generation) result = input?.gps(message.fix)
            is Message.Steps -> if (message.generation == sensors.generation) result = input?.steps(message.time, message.count)
            is Message.StepDetected -> if (message.generation == sensors.generation)
                result = input?.detectedStep(message.time, message.received)?.takeIf { it.update.events.isNotEmpty() }
        }
        syncSensors()
        val c = controller
        if (c != null && result != null && c.snapshot().state in listOf(RunState.RUNNING, RunState.PAUSED, RunState.FINISHED)) {
            val checkpoint = c.checkpoint()
            if (lastSaved?.snapshot?.state != RunState.FINISHED) {
                val s = checkpoint.snapshot
                val measurement = result.measurement?.let {
                    StoredMeasurement(runId = s.runId, segmentId = it.segmentId, monotonicMs = it.monotonicMs,
                        source = if (it is RunMeasurement.Gps) "GPS" else "STEPS",
                        deltaMeters = s.distanceMeters - (lastSaved?.snapshot?.distanceMeters ?: 0.0),
                        totalMeters = s.distanceMeters, activeMs = s.activeDurationMs, reading = Json.encodeToString(it))
                }
                val point = result.fix?.let {
                    RoutePoint(runId = s.runId, segmentId = requireNotNull(result.measurement).segmentId,
                        monotonicMs = it.monotonicMs, latitude = it.latitude, longitude = it.longitude, accuracyMeters = it.accuracyMeters)
                }
                repository.save(checkpoint, owner, zone, offset, interrupted, point, measurement, cloudOwner)
                if (s.state == RunState.FINISHED) com.example.runningapp.health.HealthScheduler.enqueue(this)
                if (s.state == RunState.FINISHED && cloudOwner != null) SyncScheduler.enqueue(this)
                lastSaved = checkpoint
                // Persist event IDs/goal state before cues; a crash may omit a cue but cannot replay it.
                cues.play(result.update.events, s.settings.units)
                if (s.state == RunState.FINISHED && message is Message.Command) {
                    finishAwards = runCatching {
                        val dao=RunDatabase.get(this).runs()
                        dao.rebuildAchievements(cloudOwner,owner).filter { it.runId==s.runId }
                    }.getOrDefault(emptyList())
                    // Coaching setup failure must never turn a committed finish into a storage error.
                    val selected = message.action == FINISH
                    if (selected && cloudOwner != null) runCatching { RunDatabase.get(this).runs().queueCoaching(s.runId, requireNotNull(cloudOwner)) }
                    runCatching { coaching.start(s.runId, cloudOwner, selected) }
                }
            }
        }
        publish()
        if ((controller?.snapshot()?.state == RunState.FINISHED && !cues.isPlaying && !coaching.view.busy) || controller == null) {
            if (foregroundStarted) { stopForeground(STOP_FOREGROUND_REMOVE); foregroundStarted = false }
        } else if (foregroundStarted) getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
    }

    private fun syncSensors() {
        val s = controller?.snapshot()
        val observe = s?.state == RunState.RUNNING ||
            (s?.state == RunState.PAUSED && s.pauseReason == PauseReason.AUTOMATIC)
        if (listening && permissionSignature != capabilities()) {
            sensors.stop(); listening = false
            input?.reset()
            input?.stepsUsable = false
            input?.detectorUsable = false
            controller?.clearSource()
        }
        if (observe && !listening) {
            input?.stepsUsable = sensors.start(s.settings.mode, s.settings.strideLengthMeters,
                { generation, fix -> messages.trySend(Message.Gps(generation, fix)) },
                { generation, time, count -> messages.trySend(Message.Steps(generation, time, count)) })
            input?.detectorUsable = s.settings.autoPauseEnabled && sensors.startMotion(
                { generation, time, received -> messages.trySend(Message.StepDetected(generation, time, received)) })
            listening = true
            permissionSignature = capabilities()
        }
        if (!observe && s?.state != RunState.COUNTDOWN) {
            if (listening) sensors.stop()
            listening = false
            input?.stepsUsable = false
            input?.detectorUsable = false
            releaseWakeLock()
        } else {
            // Automatic pauses retain observation and the timer; manual pauses release both.
            // The timeout bounds battery use if the consumer stalls.
            wakeLock.acquire(10 * 60 * 1_000L)
        }
        if (input != null && !activityAllowed()) {
            input!!.stepsUsable = false
            input!!.detectorUsable = false
        }
    }

    private fun capabilities() = "${activityAllowed()}:${locationAllowed()}"

    private fun publish() {
        view.value = TrackingView(controller?.snapshot(), interrupted, input?.distanceAvailable == true, true, error = actionError, coaching = coaching.view,
            achievements = if(controller?.snapshot()?.state==RunState.FINISHED) finishAwards.filter { it.runId==controller?.snapshot()?.runId } else emptyList())
    }

    private fun clearFinishedRun() {
        coaching.cancel(); cues.cancel()
        controller = null; input = null; lastSaved = null; interrupted = false
        cloudOwner = null; finishAwards = emptyList()
    }

    private fun promote(mode: RunMode) {
        if (Build.VERSION.SDK_INT >= 34) {
            var types = 0
            if (activityAllowed()) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            if (mode == RunMode.OUTDOOR && locationAllowed()) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            // Explicit debug timer-only use when no sensor permission can satisfy health/location.
            if (types == 0) types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            startForeground(NOTIFICATION, notification(), types)
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION, notification(), if (mode == RunMode.OUTDOOR && locationAllowed())
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        } else startForeground(NOTIFICATION, notification())
        foregroundStarted = true
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = Notification.Builder(this, notificationChannel).setSmallIcon(R.drawable.ic_launcher).setContentTitle("WAYiRUN")
            .setContentText(when (controller?.snapshot()?.state) {
                RunState.PAUSED -> "Run paused · Tap to resume or finish"
                RunState.FINISHED -> "Run complete · Playing summary"
                else -> "Run in progress · Tap to open"
            })
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        return builder.build()
    }

    private fun releaseWakeLock() { if (wakeLock.isHeld) wakeLock.release() }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        sensors.stop(); releaseWakeLock(); cues.close(); coaching.cancel(); scope.cancel(); messages.close()
        view.value = view.value.copy(ready = false, busy = false)
        super.onDestroy()
    }

    companion object {
        // Instrumentation replaces only sensor IO; the real serial service and storage still run.
        internal var sensorFactory: (Context) -> SensorAdapters = { SensorAdapters(it) }
        const val OPEN = "open"
        const val ENTER = "enter"
        const val NEW = "new"
        const val START = "start"
        const val PAUSE = "pause"
        const val RESUME = "resume"
        const val FINISH = "finish"
        const val FINISH_WITHOUT_COACHING = "finish-without-coaching"
        const val DISMISS_COACHING = "dismiss-coaching"
        const val DISCARD = "discard"
        private const val RUN_ID = "run-id"
        private const val SETTINGS = "settings"
        private const val NOTIFICATION = 1
        val view = MutableStateFlow(TrackingView())
        fun send(context: Context, action: String, settings: RunSettings? = null, runId: String? = null) {
            val current = view.value
            // Reject known stale controls before requesting a foreground-service start.
            // Android requires foreground promotion even when that command would be a no-op.
            if (current.ready && ((action != ENTER && runId != null && runId != current.snapshot?.runId) ||
                    (action == RESUME && current.snapshot?.state != RunState.PAUSED))) return
            view.value = view.value.copy(busy = true)
            val intent = Intent(context, TrackingService::class.java).setAction(action)
            if (runId != null) intent.putExtra(RUN_ID, runId)
            if (settings != null) intent.putExtra(SETTINGS, Json.encodeToString(settings))
            try {
                if (action == START || action == RESUME) context.startForegroundService(intent) else context.startService(intent)
            } catch (_: RuntimeException) {
                view.value = view.value.copy(busy = false, error = "Android couldn't open tracking. Please try again from WAYiRUN.")
            }
        }
    }
}
