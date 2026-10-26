package com.example.runningapp.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.*
import android.location.*
import android.os.Build
import android.os.Bundle
import android.os.Looper
import com.example.runningapp.domain.GpsFix
import com.example.runningapp.domain.RunMode
import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

fun Context.granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
fun Context.activityAllowed() = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACTIVITY_RECOGNITION)
fun Context.locationAllowed() = granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
    getSystemService(LocationManager::class.java).isLocationEnabled

/** Each registration has its own callback, invalidated before pause/resume or permission changes. */
open class SensorAdapters(private val context: Context) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val locations = context.getSystemService(LocationManager::class.java)
    private var stepListener: SensorEventListener? = null
    private var locationListener: LocationListener? = null
    private var detectorListener: SensorEventListener? = null
    var generation = 0L
        private set

    /** One network fix for weather; never registers indoor GPS tracking or requests permission. */
    open suspend fun weatherLocation(): Pair<Double,Double>? {
        if ((!context.granted(Manifest.permission.ACCESS_COARSE_LOCATION) &&
            !context.granted(Manifest.permission.ACCESS_FINE_LOCATION)) || !locations.isLocationEnabled) return null
        fun usable(location: Location): Boolean {
            val age = SystemClock.elapsedRealtime() - location.elapsedRealtimeNanos / 1_000_000
            return age in 0..120_000 && location.hasAccuracy() && location.accuracy.isFinite() &&
                location.accuracy in 0f..10_000f && location.latitude.isFinite() && location.longitude.isFinite()
        }
        return try {
            val cached = locations.allProviders.mapNotNull { locations.getLastKnownLocation(it) }
                .filter(::usable).maxByOrNull { it.elapsedRealtimeNanos }
            if (cached != null) return cached.latitude to cached.longitude
            withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            locations.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(if (usable(location)) location.latitude to location.longitude else null)
                        }
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) {
                            locations.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(null)
                        }
                        @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    }
                    continuation.invokeOnCancellation { locations.removeUpdates(listener) }
                    @Suppress("DEPRECATION")
                    locations.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
                }
            }
        } catch (_: SecurityException) { null } catch (_: IllegalArgumentException) { null }
    }

    open fun start(mode: RunMode, stride: Double?, onGps: (Long, GpsFix) -> Unit, onSteps: (Long, Long, Long) -> Unit): Boolean {
        stop()
        val token = generation
        val stepSensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        var hasSteps = false
        if (context.activityAllowed() && stepSensor != null) {
            val listener = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                override fun onSensorChanged(event: SensorEvent) {
                    val value = event.values.firstOrNull() ?: return
                    if (value.isFinite() && value >= 0) onSteps(token, event.timestamp / 1_000_000, value.toLong())
                }
            }
            stepListener = listener
            hasSteps = try { sensors.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL, 0) }
                catch (_: SecurityException) { false }
        }
        if (mode == RunMode.OUTDOOR && context.locationAllowed()) {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (location.hasAccuracy()) onGps(token, GpsFix(location.elapsedRealtimeNanos / 1_000_000,
                        location.latitude, location.longitude, location.accuracy,
                        if (location.hasSpeed()) location.speed.toDouble() else null,
                        if (Build.VERSION.SDK_INT >= 26 && location.hasSpeedAccuracy())
                            location.speedAccuracyMetersPerSecond.toDouble() else null))
                }
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
                @Deprecated("Legacy callback")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            locationListener = listener
            try { locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper()) }
            catch (_: SecurityException) { locationListener = null }
            catch (_: IllegalArgumentException) { locationListener = null }
        }
        return hasSteps
    }

    open fun startMotion(onStep: (Long, Long, Long) -> Unit): Boolean {
        val token = generation
        if (!context.activityAllowed()) return false
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) ?: return false
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.firstOrNull() == 1f)
                    onStep(token, event.timestamp / 1_000_000, SystemClock.elapsedRealtime())
            }
        }
        return try {
            sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, 0).also {
                if (it) detectorListener = listener
            }
        } catch (_: SecurityException) {
            false
        }
    }

    open fun stop() {
        generation++
        stepListener?.let { sensors.unregisterListener(it) }
        detectorListener?.let { sensors.unregisterListener(it) }
        detectorListener = null
        locationListener?.let { locations.removeUpdates(it) }
        stepListener = null
        locationListener = null
    }
}
