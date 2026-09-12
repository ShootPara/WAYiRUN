package com.example.runningapp.tracking

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import com.example.runningapp.MainActivity
import com.example.runningapp.R
import com.example.runningapp.domain.*
import com.example.runningapp.storage.*
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
)

/** All commands, measurements and disk commits pass through one serial consumer. */
class TrackingService : Service() {
    private sealed interface Message {
        data class Command(val action: String, val settings: RunSettings? = null) : Message
        data object Tick : Message
        data class Gps(val generation: Long, val fix: GpsFix) : Message
        data class Steps(val generation: Long, val time: Long, val count: Long) : Message
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val messages = Channel<Message>(Channel.UNLIMITED)
    private val clock = RunClock { RunTime(SystemClock.elapsedRealtime(), System.currentTimeMillis()) }
    private lateinit var sensors: SensorAdapters
    private lateinit var cues: RunCues
    private lateinit var repository: RunRepository
    private lateinit var wakeLock: PowerManager.WakeLock
    private var controller: RunController? = null
    private var input: TrackingInput? = null
    private var lastSaved: RunCheckpoint? = null
    private var owner = ""
    private var zone = ""
    private var offset = 0
    private var interrupted = false
    private var listening = false
    private var loaded = false
    private var foregroundStarted = false
    private var permissionSignature = ""

    override fun onCreate() {
        super.onCreate()
        sensors = SensorAdapters(this)
        cues = RunCues(this)
        repository = RunRepository(RunDatabase.get(this).runs())
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WAYiRUN:tracking")
        wakeLock.setReferenceCounted(false)
        val preferences = getSharedPreferences("local-settings", MODE_PRIVATE)
        owner = preferences.getString("local-owner", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("local-owner", it).apply()
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Run tracking", NotificationManager.IMPORTANCE_LOW),
        )
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
                if (controller?.snapshot()?.state in listOf(RunState.RUNNING, RunState.COUNTDOWN)) messages.send(Message.Tick)
            }
        }
    }

    private suspend fun load() {
        val stored = repository.active() ?: repository.latest.first()
        if (stored != null) {
            val saved = stored.decode()
            controller = RunController.recover(saved, clock)
            input = TrackingInput(requireNotNull(controller), clock)
            owner = stored.ownerId; zone = stored.zoneId; offset = stored.startOffsetSeconds
            interrupted = stored.interrupted || saved.snapshot.state == RunState.RUNNING
            lastSaved = saved
            if (saved.snapshot.state != RunState.FINISHED) {
                repository.save(controller!!.checkpoint(), owner, zone, offset, interrupted)
                lastSaved = controller!!.checkpoint()
            }
        }
        loaded = true
        publish()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: OPEN
        val settings = intent?.getStringExtra(SETTINGS)?.let { Json.decodeFromString<RunSettings>(it) }
        if (action == START || action == RESUME) {
            try { promote(settings?.mode ?: controller?.snapshot()?.settings?.mode ?: RunMode.INDOOR) }
            catch (_: RuntimeException) {
                view.value = view.value.copy(busy = false, error = "Open WAYiRUN and try starting again. Android couldn't start tracking.")
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        messages.trySend(Message.Command(action, settings))
        return START_NOT_STICKY
    }

    private suspend fun handle(message: Message) {
        var result: TrackingResult? = null
        when (message) {
            is Message.Command -> when (message.action) {
                OPEN -> {
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
                    controller = null; input = null; lastSaved = null; interrupted = false
                }
                START -> if (controller == null) {
                    val settings = requireNotNull(message.settings)
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
                    interrupted = false
                    result = TrackingResult(controller!!.resume())
                }
                FINISH -> if (controller != null) {
                    sensors.stop(); listening = false; input?.reset()
                    result = TrackingResult(controller!!.finish())
                }
            }
            Message.Tick -> result = input?.tick()
            is Message.Gps -> if (message.generation == sensors.generation) result = input?.gps(message.fix)
            is Message.Steps -> if (message.generation == sensors.generation) result = input?.steps(message.time, message.count)
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
                repository.save(checkpoint, owner, zone, offset, interrupted, point, measurement)
                lastSaved = checkpoint
                // Persist event IDs/goal state before cues; a crash may omit a cue but cannot replay it.
                cues.play(result.update.events)
            }
        }
        publish()
        if (controller?.snapshot()?.state == RunState.FINISHED || controller == null) {
            if (foregroundStarted) { stopForeground(STOP_FOREGROUND_REMOVE); foregroundStarted = false }
        } else if (foregroundStarted) getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
    }

    private fun syncSensors() {
        val s = controller?.snapshot()
        if (s?.state == RunState.RUNNING && !listening) {
            input?.stepsUsable = sensors.start(s.settings.mode, s.settings.strideLengthMeters,
                { generation, fix -> messages.trySend(Message.Gps(generation, fix)) },
                { generation, time, count -> messages.trySend(Message.Steps(generation, time, count)) })
            listening = true
            permissionSignature = capabilities()
        }
        if (s?.state !in listOf(RunState.RUNNING, RunState.COUNTDOWN)) {
            if (listening) sensors.stop()
            listening = false
            releaseWakeLock()
        } else {
            // Renewed by the serial consumer; released on pause/finish/failure/destruction.
            // The timeout bounds battery use if the consumer stalls.
            wakeLock.acquire(10 * 60 * 1_000L)
        }
        if (input != null && !activityAllowed()) input!!.stepsUsable = false
    }

    private fun capabilities() = "${activityAllowed()}:${locationAllowed()}"

    private fun publish() {
        view.value = TrackingView(controller?.snapshot(), interrupted, input?.distanceAvailable == true, true)
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
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_launcher).setContentTitle("WAYiRUN")
            .setContentText(if (controller?.snapshot()?.state == RunState.PAUSED) "Run paused · Tap to resume or finish" else "Run in progress · Tap to open")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build()
    }

    private fun releaseWakeLock() { if (wakeLock.isHeld) wakeLock.release() }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        sensors.stop(); releaseWakeLock(); cues.close(); scope.cancel(); messages.close()
        view.value = view.value.copy(ready = false, busy = false)
        super.onDestroy()
    }

    companion object {
        const val OPEN = "open"
        const val NEW = "new"
        const val START = "start"
        const val PAUSE = "pause"
        const val RESUME = "resume"
        const val FINISH = "finish"
        private const val SETTINGS = "settings"
        private const val CHANNEL = "run-tracking"
        private const val NOTIFICATION = 1
        val view = MutableStateFlow(TrackingView())
        fun send(context: Context, action: String, settings: RunSettings? = null) {
            view.value = view.value.copy(busy = true)
            val intent = Intent(context, TrackingService::class.java).setAction(action)
            if (settings != null) intent.putExtra(SETTINGS, Json.encodeToString(settings))
            try {
                if (action == START || action == RESUME) context.startForegroundService(intent) else context.startService(intent)
            } catch (_: RuntimeException) {
                view.value = view.value.copy(busy = false, error = "Android couldn't open tracking. Please try again from WAYiRUN.")
            }
        }
    }
}
