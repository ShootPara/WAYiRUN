package com.example.runningapp

import android.Manifest
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import com.example.runningapp.account.AccountViewModel
import com.example.runningapp.domain.RunState
import com.example.runningapp.tracking.*
import com.example.runningapp.ui.WayirunApp

class MainActivity : ComponentActivity() {
    private val accountModel by lazy { ViewModelProvider(this)[AccountViewModel::class.java] }
    private val entry by lazy { ViewModelProvider(this)[RunEntryState::class.java] }
    private var startupPending by mutableStateOf(true)
    private var requesting = false
    private var settingsError by mutableStateOf<String?>(null)
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requesting = false
        settingsError = if (missingPermissions().isEmpty()) null else
            "Some permissions are off. You can enable them in App permissions. Local running is still available."
        TrackingService.send(this, TrackingService.OPEN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        startupPending = savedInstanceState?.getBoolean("startupPending") ?: true
        requesting = savedInstanceState?.getBoolean("requesting") ?: false
        entry.externalRunId = entry.externalRunId ?: savedInstanceState?.getString("externalRunId")
        enableEdgeToEdge()
        // Keep Android's existing launcher splash. There is no in-app setup overlay.
        setContent {
            val account by accountModel.state.collectAsState()
            val tracking by TrackingService.view.collectAsState()
            CompositionLocalProvider(com.example.runningapp.photos.LocalExternalRunAction provides { runId -> entry.externalRunId = runId }) {
                WayirunApp(onPermissions = { requestPermissions() }, accountView = account,
                    onSignIn = { accountModel.signIn(this) }, onSignOut = { accountModel.signOut() },
                    onImport = { accountModel.importRuns(it) }, onRetrySync = { accountModel.retrySync() },
                    onAppPermissions = { openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())) },
                    settingsError = settingsError) {
                    TrackingService.send(this, TrackingService.START, it)
                }
            }
            LaunchedEffect(startupPending, tracking.ready) {
                if (startupPending && tracking.ready) {
                    startupPending = false
                    if (tracking.snapshot?.state !in listOf(RunState.COUNTDOWN, RunState.RUNNING, RunState.PAUSED)) {
                        requestPermissions()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.runningapp.health.HealthScheduler.enqueue(this)
        val appEntry = entry.needsEntry || entry.externalRunId != null || entry.recreationRunId != null
        val returnRunId = entry.resumed()
        TrackingService.send(this, if (appEntry) TrackingService.ENTER else TrackingService.OPEN, runId = returnRunId)
    }

    override fun onStop() {
        entry.stopped(isChangingConfigurations,
            TrackingService.view.value.snapshot?.takeIf { it.state == RunState.FINISHED }?.runId)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("startupPending", startupPending)
        outState.putBoolean("requesting", requesting)
        outState.putString("externalRunId", entry.externalRunId)
        super.onSaveInstanceState(outState)
    }

    private fun missingPermissions(): List<String> = buildList {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 29 && !activityAllowed()) add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestPermissions() {
        if (requesting) return
        val needed = missingPermissions()
        if (needed.isNotEmpty()) {
            requesting = true
            permissions.launch(needed.toTypedArray())
        }
    }

    private fun openSettings(intent: Intent): Boolean = try {
        startActivity(intent)
        settingsError = null
        true
    } catch (_: android.content.ActivityNotFoundException) {
        settingsError = "This phone could not open that settings page. Your run can still start."
        false
    } catch (_: SecurityException) {
        settingsError = "Android blocked this settings page. You can manage permissions in Android settings."
        false
    }
}
