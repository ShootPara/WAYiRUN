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

fun Context.granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
fun Context.activityAllowed() = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACTIVITY_RECOGNITION)
fun Context.locationAllowed() = granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
    getSystemService(LocationManager::class.java).isLocationEnabled

/** Each registration has its own callback, invalidated before pause/resume or permission changes. */
class SensorAdapters(private val context: Context) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val locations = context.getSystemService(LocationManager::class.java)
    private var stepListener: SensorEventListener? = null
    private var locationListener: LocationListener? = null
    var generation = 0L
        private set

    fun start(mode: RunMode, stride: Double?, onGps: (Long, GpsFix) -> Unit, onSteps: (Long, Long, Long) -> Unit): Boolean {
        stop()
        val token = generation
        val stepSensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        var hasSteps = false
        if (stride != null && context.activityAllowed() && stepSensor != null) {
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
                        location.latitude, location.longitude, location.accuracy))
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

    fun stop() {
        generation++
        stepListener?.let { sensors.unregisterListener(it) }
        locationListener?.let { locations.removeUpdates(it) }
        stepListener = null
        locationListener = null
    }
}
