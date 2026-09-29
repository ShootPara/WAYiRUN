package com.example.runningapp.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.runningapp.domain.*
import org.junit.Assert.*
import org.junit.Test

class AnnouncementPreferencesTest {
    @Test fun migrationPreservesLegacyMasterAndRunsOnlyOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("announcement-migration-test", Context.MODE_PRIVATE)
        try {
            for (legacy in AnnouncementInterval.entries) for (master in listOf(false, true)) {
                prefs.edit().clear().putBoolean("announcements-enabled", master)
                    .putString("announcement-interval", legacy.name).commit()
                assertEquals(AnnouncementSelection.legacy(legacy), AnnouncementPreferences.read(prefs))
                assertEquals(master, prefs.getBoolean("announcements-enabled", !master))
                val both = AnnouncementSelection(distanceEnabled = true)
                AnnouncementPreferences.write(prefs, both)
                prefs.edit().putString("announcement-interval", AnnouncementInterval.TEN_MINUTES.name).commit()
                assertEquals(both, AnnouncementPreferences.read(prefs))
            }
            prefs.edit().clear().commit()
            assertEquals(AnnouncementSelection(), AnnouncementPreferences.read(prefs))
        } finally { prefs.edit().clear().commit() }
    }
}
