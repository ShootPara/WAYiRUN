package com.example.runningapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertEquals("933230558080-hlnp4ooq98sdv67ed6ok4fo9buhie0ll.apps.googleusercontent.com", BuildConfig.GOOGLE_WEB_CLIENT_ID)
        assertEquals("933230558080-l6iica70or7astsl7er2sq6h1pt5alr2.apps.googleusercontent.com", BuildConfig.GOOGLE_ANDROID_CLIENT_ID)
        assertTrue(BuildConfig.PRODUCTION_READY)
    }
}
