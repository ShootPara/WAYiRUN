package com.example.runningapp.ui

import android.content.SharedPreferences
import com.example.runningapp.domain.AnnouncementInterval
import com.example.runningapp.domain.AnnouncementSelection

internal object AnnouncementPreferences {
    val keys = listOf("announcement-selection-version", "announcement-time-enabled", "announcement-time-interval",
        "announcement-distance-enabled", "announcement-distance-interval")

    fun read(prefs: SharedPreferences): AnnouncementSelection {
        if (!prefs.contains(keys[0])) {
            val legacy = AnnouncementInterval.entries.firstOrNull {
                it.name == prefs.getString("announcement-interval", null)
            } ?: AnnouncementInterval.FIVE_MINUTES
            return AnnouncementSelection.legacy(legacy).also { write(prefs, it) }
        }
        fun interval(key: String, fallback: AnnouncementInterval) = AnnouncementInterval.entries.firstOrNull {
            it.name == prefs.getString(key, null) && (it.timeMs != null) == (fallback.timeMs != null)
        } ?: fallback
        return AnnouncementSelection(
            timeEnabled = prefs.getBoolean(keys[1], true),
            timeInterval = interval(keys[2], AnnouncementInterval.FIVE_MINUTES),
            distanceEnabled = prefs.getBoolean(keys[3], false),
            distanceInterval = interval(keys[4], AnnouncementInterval.ONE_UNIT),
        )
    }

    fun write(prefs: SharedPreferences, selection: AnnouncementSelection) {
        // One editor transaction makes the migration marker and both channels atomic.
        prefs.edit().putInt(keys[0], selection.version)
            .putBoolean(keys[1], selection.timeEnabled).putString(keys[2], selection.timeInterval.name)
            .putBoolean(keys[3], selection.distanceEnabled).putString(keys[4], selection.distanceInterval.name).apply()
    }
}
