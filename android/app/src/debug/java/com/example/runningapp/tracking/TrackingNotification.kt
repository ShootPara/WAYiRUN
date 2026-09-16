package com.example.runningapp.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

internal fun ensureTrackingChannel(context: Context, legacyId: String = "run-tracking", current: String = "run-tracking-prominent"): String {
    val manager = context.getSystemService(NotificationManager::class.java)
    val legacy = manager.getNotificationChannel(legacyId)
    if (manager.getNotificationChannel(current) != null) return current
    // Migrate only the untouched prototype LOW channel. Never route around a mute.
    if (legacy != null) {
        val untouched = Build.VERSION.SDK_INT >= 29 && !legacy.hasUserSetImportance() &&
            legacy.importance == NotificationManager.IMPORTANCE_LOW && legacy.sound != null &&
            (Build.VERSION.SDK_INT < 30 || !legacy.hasUserSetSound())
        if (!untouched) return legacy.id
    }
    manager.createNotificationChannel(NotificationChannel(current, "Run tracking", NotificationManager.IMPORTANCE_DEFAULT).apply {
        // Spoken cues own audio focus; the ongoing status must not play a competing alert.
        setSound(null, null)
        enableVibration(false)
    })
    return current
}
