package com.example.runningapp.domain

import org.junit.Test
import org.junit.Assert.*

class HealthExportTest {
    private var clock=0L
    private fun run()=RunController("run",RunSettings(RunMode.INDOOR,RunUnits.MILES,0,RunGoal.None,null),RunClock {RunTime(clock,1_700_000_000_000+clock)})
    @Test fun pausesUseRecordedWallTimesAndStableIdsDoNotDependOnUnits() {
        val r=run();r.start();clock=10000;r.pause();clock=20000;r.resume();clock=30000;r.finish()
        val p=healthRun(r.snapshot())
        assertEquals(20000,p.activeMs);assertEquals(30000,p.endMs-p.startMs)
        assertEquals(listOf((p.startMs+10000) to (p.startMs+20000)),p.pauses)
        assertEquals("wayirun:run:session",p.sessionId);assertEquals("wayirun:run:distance",p.distanceId)
        assertEquals(p.sessionId,healthRun(r.snapshot().copy(settings=r.snapshot().settings.copy(units=RunUnits.KILOMETERS))).sessionId)
        assertTrue(p.indoor)
    }
    @Test fun zeroDurationOrBackwardsClockCannotInventAHealthSession() {
        val r=run();r.start();r.finish()
        try {healthRun(r.snapshot());fail("Zero duration accepted")}catch(_:IllegalArgumentException) {}
        clock=1000
        val snapshot=r.snapshot().copy(endedUtcMs=1_699_999_999_000,activeDurationMs=1000)
        try {healthRun(snapshot);fail("Backwards wall time accepted")}catch(_:IllegalArgumentException) {}
    }
    @Test fun legacyRunWithoutIntervalsDoesNotInventPauses() {
        val r=run();r.start();clock=5000;r.finish()
        assertTrue(healthRun(r.snapshot().copy(activeIntervals=emptyList())).pauses.isEmpty())
    }
}
