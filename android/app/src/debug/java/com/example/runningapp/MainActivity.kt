package com.example.runningapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.runningapp.domain.*
import com.example.runningapp.tracking.*
import com.example.runningapp.ui.WayirunApp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    private var pending: RunSettings? = null
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pending?.let { settings -> TrackingService.send(this, TrackingService.START, settings) }
        pending = null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pending = savedInstanceState?.getString("pending")?.let { Json.decodeFromString<RunSettings>(it) }
        enableEdgeToEdge()
        setContent { WayirunApp { begin(it) } }
    }
    override fun onResume() {
        super.onResume()
        TrackingService.send(this, TrackingService.OPEN)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        pending?.let { outState.putString("pending", Json.encodeToString(it)) }
        super.onSaveInstanceState(outState)
    }
    private fun begin(settings: RunSettings) {
        val needed = buildList {
            if (settings.mode == RunMode.OUTDOOR && !granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION); add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= 29 && settings.strideLengthMeters != null && !activityAllowed()) add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) TrackingService.send(this, TrackingService.START, settings)
        else { pending = settings; permissions.launch(needed.toTypedArray()) }
    }
}
