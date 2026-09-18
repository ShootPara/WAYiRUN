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
    val musicStatus: String = "Music controls off · Runs still work",
    val coaching: CoachingView = CoachingView(),
)

/** All commands, measurements and disk commits pass through one serial consumer. */
class TrackingService : Service() {
    private sealed interface Message {
        data class Command(val action: String, val settings: RunSettings? = null,
            val expectedRunId: String? = null, val mediaGeneration: Long? = null) : Message
        data object Tick : Message
        data object AudioIdle : Message
        data class Player(val generation: Long, val status: PlayerStatus) : Message
        data object RefreshMusic : Message
        data class MusicTimeout(val generation: Long, val serial: Long) : Message
        data class CueSettled(val serial: Long) : Message
        data class Gps(val generation: Long, val fix: GpsFix) : Message
        data class Steps(val generation: Long, val time: Long, val count: Long) : Message
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val messages = Channel<Message>(Channel.UNLIMITED)
    private val clock = RunClock { RunTime(SystemClock.elapsedRealtime(), System.currentTimeMillis()) }
    private lateinit var sensors: SensorAdapters
    private lateinit var cues: RunCues
    private lateinit var coaching: PostRunCoaching
    private lateinit var repository: RunRepository
    private lateinit var music: MusicSessionAdapter
    private val musicPolicy = MusicLinkPolicy()
    private var musicSerial = 0L
    private var cueSerial = 0L
    private var musicError: String? = null
    private var actionError: String? = null
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
        sensors = SensorAdapters(this)
        cues = RunCues(this) { messages.trySend(Message.AudioIdle) }
        coaching = PostRunCoaching(this, scope, { cues.isPlaying }) {
            if (loaded) publish()
            messages.trySend(Message.AudioIdle)
        }
        music = MusicSessionAdapter(this) { generation, state -> messages.trySend(Message.Player(generation, state)) }
        repository = RunRepository(RunDatabase.get(this).runs())
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WAYiRUN:tracking")
        wakeLock.setReferenceCounted(false)
        val preferences = getSharedPreferences("local-settings", MODE_PRIVATE)
        owner = preferences.getString("local-owner", null) ?: UUID.randomUUID().toString().also {
            preferences.edit { putString("local-owner", it) }
        }
        localOwner = owner
        notificationChannel = ensureTrackingChannel(this)
        scope.launch { MusicAccessService.connected.collect { messages.send(Message.RefreshMusic) } }
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
                    musicPolicy.detach()
                    musicPolicy.runChanged(controller?.snapshot()?.state)
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
        val selected = withContext(Dispatchers.IO) { SessionStore(this@TrackingService).read()?.ownerId }
        val stored = repository.active() ?: RunDatabase.get(this).runs().latestVisible(selected)
        if (stored != null) {
            val saved = stored.decode()
            controller = RunController.recover(saved, clock)
            input = TrackingInput(requireNotNull(controller), clock)
            owner = stored.ownerId; cloudOwner = stored.cloudOwnerId; zone = stored.zoneId; offset = stored.startOffsetSeconds
            interrupted = stored.interrupted || saved.snapshot.state == RunState.RUNNING
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
        if (message is Message.Command && message.expectedRunId != null &&
            (message.expectedRunId != controller?.snapshot()?.runId ||
                (message.mediaGeneration != null && message.mediaGeneration != music.generation))) {
            if (controller == null && foregroundStarted) { stopForeground(STOP_FOREGROUND_REMOVE); foregroundStarted = false }
            publish(); return
        }
        if (message is Message.Command && message.action != OPEN) actionError = null
        var result: TrackingResult? = null
        when (message) {
            is Message.Command -> when (message.action) {
                DISCARD -> {
                    val saved = controller?.snapshot()
                    if (saved?.state == RunState.FINISHED && message.expectedRunId == saved.runId) {
                        try {
                            check(repository.discard(saved.runId, owner))
                            coaching.cancel()
                            SyncScheduler.enqueue(this)
                            cues.cancel(); musicPolicy.detach()
                            controller = null; input = null; lastSaved = null; interrupted = false
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) {
                            actionError = "Couldn't discard this run. It is still saved. Please try again."
                        }
                    }
                }
                OPEN -> {
                    val selected = withContext(Dispatchers.IO) { SessionStore(this@TrackingService).read()?.ownerId }
                    if (controller?.snapshot()?.state == RunState.FINISHED) {
                        val stored = RunDatabase.get(this).runs().get(controller!!.snapshot().runId)
                        if (stored == null || (stored.cloudOwnerId != null && stored.cloudOwnerId != selected)) {
                            coaching.cancel()
                            controller = null; input = null; lastSaved = null; interrupted = false; cloudOwner = null
                            load()
                        } else cloudOwner = stored.cloudOwnerId
                    }
                    music.refresh()
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
                    coaching.cancel()
                    cues.cancel()
                    controller = null; input = null; lastSaved = null; interrupted = false
                }
                START -> if (controller == null) {
                    music.refresh()
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
                    result = TrackingResult(controller!!.resume())
                }
                DISMISS_COACHING -> coaching.dismiss()
                FINISH, FINISH_WITHOUT_COACHING -> if (controller != null) {
                    sensors.stop(); listening = false; input?.reset()
                    result = TrackingResult(controller!!.finish())
                }
            }
            Message.Tick -> result = input?.tick()
            Message.AudioIdle -> {
                val serial = ++cueSerial
                scope.launch { delay(600); messages.send(Message.CueSettled(serial)) }
            }
            is Message.CueSettled -> if (message.serial == cueSerial && !cues.isPlaying) musicPolicy.cueSettled()
            Message.RefreshMusic -> music.refresh()
            is Message.Player -> if (message.generation == music.generation) {
                performMusic(musicPolicy.playerChanged(message.status))
            }
            is Message.MusicTimeout -> if (message.generation == music.generation && message.serial == musicSerial) {
                val wasLinked = musicPolicy.linked
                musicPolicy.commandExpired(music.current)
                if (wasLinked && !musicPolicy.linked) musicError = "Music didn't respond · Run controls still work"
            }
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
                repository.save(checkpoint, owner, zone, offset, interrupted, point, measurement, cloudOwner)
                if (s.state == RunState.FINISHED && cloudOwner != null) SyncScheduler.enqueue(this)
                lastSaved = checkpoint
                performMusic(musicPolicy.runChanged(s.state))
                // Persist event IDs/goal state before cues; a crash may omit a cue but cannot replay it.
                cues.play(result.update.events, s.settings.units)
                if (result.update.events.isNotEmpty() && cues.isPlaying) { cueSerial++; musicPolicy.cueStarted() }
                if (s.state == RunState.FINISHED && message is Message.Command) {
                    // Coaching setup failure must never turn a committed finish into a storage error.
                    runCatching { coaching.start(s.runId, cloudOwner, message.action == FINISH) }
                }
            }
        }
        if (controller == null) musicPolicy.runChanged(null)
        publish()
        if ((controller?.snapshot()?.state == RunState.FINISHED && !cues.isPlaying && !coaching.view.busy) || controller == null) {
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
        val status = musicError ?: when {
            !music.allowed -> "Music controls off · Runs still work"
            musicPolicy.linked -> "YouTube Music linked"
            !music.available -> "Music off · Run starts normally"
            music.current == PlayerStatus.PLAYING -> "YouTube Music available · Links when tracking starts"
            else -> "YouTube Music not playing · Run starts normally"
        }
        view.value = TrackingView(controller?.snapshot(), interrupted, input?.distanceAvailable == true, true, error = actionError, musicStatus = status, coaching = coaching.view)
    }

    private fun performMusic(actions: List<MusicAction>) {
        actions.forEach { action ->
            when (action) {
                MusicAction.PAUSE_RUN, MusicAction.RESUME_RUN -> {
                    val s = controller?.snapshot() ?: return@forEach
                    if (s.state !in listOf(RunState.RUNNING, RunState.PAUSED)) return@forEach
                    messages.trySend(Message.Command(if (action == MusicAction.PAUSE_RUN) PAUSE else RESUME,
                        expectedRunId = s.runId, mediaGeneration = music.generation))
                }
                else -> {
                    if (!music.command(action)) {
                        musicPolicy.detach()
                        musicError = "Music control unavailable · Run controls still work"
                    } else {
                        musicError = null
                        val generation = music.generation; val serial = ++musicSerial
                        scope.launch { delay(2000); messages.send(Message.MusicTimeout(generation, serial)) }
                    }
                }
            }
        }
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
        sensors.stop(); releaseWakeLock(); music.close(); cues.close(); coaching.cancel(); scope.cancel(); messages.close()
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
            if (current.ready && ((runId != null && runId != current.snapshot?.runId) ||
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
