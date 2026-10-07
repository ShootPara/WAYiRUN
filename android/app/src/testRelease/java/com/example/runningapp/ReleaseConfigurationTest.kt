package com.example.runningapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReleaseConfigurationTest {
    @Test fun structuralReleaseUsesOnlyProductionIdentityAndOrigin() {
        assertEquals("com.unopenedparachute.wayirun", BuildConfig.APPLICATION_ID)
        assertEquals(1, BuildConfig.VERSION_CODE)
        assertEquals("1.0.0", BuildConfig.VERSION_NAME)
        assertFalse(BuildConfig.DEBUG)
        assertEquals("production", BuildConfig.APP_ENVIRONMENT)
        assertEquals("https://wayirun.slopcopy.com", BuildConfig.API_ORIGIN)
        assertNotEquals("https://wayirun-dev.unopenedparachute.workers.dev", BuildConfig.API_ORIGIN)
        assertEquals("__PRODUCTION_GOOGLE_WEB_CLIENT_ID_MILESTONE_7__", BuildConfig.GOOGLE_WEB_CLIENT_ID)
        assertEquals("__PRODUCTION_GOOGLE_ANDROID_CLIENT_ID_MILESTONE_7__", BuildConfig.GOOGLE_ANDROID_CLIENT_ID)
        assertFalse(BuildConfig.PRODUCTION_READY)
    }
}
