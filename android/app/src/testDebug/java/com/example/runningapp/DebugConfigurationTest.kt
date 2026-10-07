package com.example.runningapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DebugConfigurationTest {
    @Test fun developmentIdentityAndEnvironmentRemainExact() {
        assertEquals("com.example.runningapp.debug", BuildConfig.APPLICATION_ID)
        assertEquals(1, BuildConfig.VERSION_CODE)
        assertEquals("1.0.0-dev", BuildConfig.VERSION_NAME)
        assertEquals("development", BuildConfig.APP_ENVIRONMENT)
        assertEquals("https://wayirun-dev.unopenedparachute.workers.dev", BuildConfig.API_ORIGIN)
        assertEquals("933230558080-ko4r7v0kmhip4i0n7u32diaimv1in73q.apps.googleusercontent.com", BuildConfig.GOOGLE_WEB_CLIENT_ID)
        assertEquals("933230558080-8o82hopmd4ibnt2fllqpr8252lg3q44t.apps.googleusercontent.com", BuildConfig.GOOGLE_ANDROID_CLIENT_ID)
        assertFalse(BuildConfig.PRODUCTION_READY)
    }
}
