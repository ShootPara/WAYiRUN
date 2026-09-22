package com.example.runningapp.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.io.File
import kotlinx.serialization.json.*

class AchievementsTest {
    @Test fun missingCalendarEvidenceStillCountsVerifiedDistance() {
        val r=run("a","2026-01-01",5000.0).copy(movement=emptyMap(),endDate=LocalDate.parse("2026-01-01"))
        val awards=Achievements.evaluate(listOf(r))
        assertTrue(awards.any { it.id=="distance-2" });assertFalse(awards.any { it.id.startsWith("holiday-") })
    }
    @Test fun yearOfHistoryMatchesBrowserAwardIdentitiesAndNames() {
        val fixture=Json.parseToJsonElement(File("../../testdata/achievement-parity.json").readText()).jsonObject
        val runs=fixture.getValue("runs").jsonArray.map { item -> val r=item.jsonObject
            AchievementRun(r.getValue("id").jsonPrimitive.content,r.getValue("endedMs").jsonPrimitive.long,r.getValue("meters").jsonPrimitive.double,
                r.getValue("activeMs").jsonPrimitive.long,r.getValue("movement").jsonObject.mapKeys { LocalDate.parse(it.key) }.mapValues { it.value.jsonPrimitive.double })
        }
        val actual=Achievements.evaluate(runs).map { listOf(it.id,it.occurrence,it.runId,it.name,it.date) }
        val expected=fixture.getValue("expected").jsonArray.map { item -> listOf("id","occurrence","runId","name","date").map { item.jsonObject.getValue(it).jsonPrimitive.content } }
        assertEquals(expected,actual)
    }
    private fun run(id:String,date:String,meters:Double=5000.0,time:Long=1500000)=AchievementRun(id,LocalDate.parse(date).toEpochDay()*86400000,meters,time,mapOf(LocalDate.parse(date) to meters))
    @Test fun holidayCalendarIncludesMovableDaysAndActualDates() {
        assertEquals("Fresh Tracks",Achievements.holidays(LocalDate.parse("2026-01-01")).single().second)
        for(d in listOf("2024-03-31","2026-04-05","2038-04-25")) assertEquals("easter",Achievements.holidays(LocalDate.parse(d)).single().first)
        assertEquals("thanksgiving",Achievements.holidays(LocalDate.parse("2026-11-26")).single().first)
        assertEquals("memorial",Achievements.holidays(LocalDate.parse("2026-05-25")).single().first)
        assertTrue(Achievements.holidays(LocalDate.parse("2026-07-03")).isEmpty())
        assertEquals("independence",Achievements.holidays(LocalDate.parse("2026-07-04")).single().first)
    }
    @Test fun allThresholdsAndHolidayEarnTogetherWithoutDuplicates() {
        val r=run("a","2026-12-25",50000.0)
        val awards=Achievements.evaluate(listOf(r,run("b","2026-12-25",1000.0)))
        assertEquals(9,awards.count { it.id.startsWith("distance-") })
        assertEquals(1,awards.count { it.id=="holiday-christmas" })
        assertEquals(1,awards.count { it.id=="lifetime-50" })
        assertFalse(awards.any { it.id=="longest" })
    }
    @Test fun zeroRunsDoNotEarnAndExactThresholdDoes() {
        assertTrue(Achievements.evaluate(listOf(run("a","2026-01-01",0.0))).isEmpty())
        assertFalse(Achievements.evaluate(listOf(run("a","2026-01-02",999.99))).any { it.id=="distance-0" })
        assertTrue(Achievements.evaluate(listOf(run("a","2026-01-02",1000.0))).any { it.id=="distance-0" })
    }
    @Test fun distinctDaysAndMondayWeeksAndDeletionRecompute() {
        val runs=listOf(run("a","2026-09-14"),run("b","2026-09-14"),run("c","2026-09-15"),run("d","2026-09-16"))
        assertTrue(Achievements.evaluate(runs).any { it.id=="week-3"&&it.occurrence=="2026-09-14" })
        assertFalse(Achievements.evaluate(runs.dropLast(1)).any { it.id=="week-3" })
        assertEquals(Achievements.evaluate(runs),Achievements.evaluate(runs.reversed()))
    }
    @Test fun weeklyStreakAllowsRestAndBreakStartsNewOccurrence() {
        val runs=(0..3).map { run("a$it",LocalDate.parse("2026-01-05").plusWeeks(it.toLong()).toString()) }
        assertEquals("2026-01-05",Achievements.evaluate(runs).single { it.id=="streak-4" }.occurrence)
        assertFalse(Achievements.evaluate(runs.filterIndexed { i,_ -> i!=1 }).any { it.id=="streak-4" })
    }
    @Test fun monthlyTotalsRepeatAndCannotCrossMonthBoundary() {
        val awards=Achievements.evaluate(listOf(run("a","2026-01-31",25000.0),run("b","2026-02-01",25000.0)))
        assertEquals(2,awards.count { it.id=="month-25" });assertFalse(awards.any { it.id=="month-50" })
    }
    @Test fun performanceComesFromCurveNotAverageAndTiesDoNotAward() {
        val a=run("a","2026-09-14",3000.0).copy(points=listOf(AchievementPoint(0.0,0.0),AchievementPoint(1000.0,360000.0),AchievementPoint(2000.0,660000.0),AchievementPoint(3000.0,900000.0)))
        val b=a.copy(id="b",endedMs=a.endedMs+1)
        val c=b.copy(id="c",endedMs=b.endedMs+1,points=b.points.map { it.copy(activeMs=it.activeMs*0.9) })
        val awards=Achievements.evaluate(listOf(a,b,c))
        assertEquals(1,awards.count { it.id=="pr-0" });assertEquals("c",awards.single { it.id=="pr-0" }.runId)
        assertTrue(awards.any { it.id=="progression" });assertTrue(awards.any { it.id=="negative-split" })
        assertFalse(Achievements.evaluate(listOf(a.copy(points=emptyList()))).any { it.id=="progression" })
    }
    @Test fun multipleCalendarDatesEarnOnlyMovementDays() {
        val r=run("a","2026-01-01").copy(movement=mapOf(LocalDate.parse("2025-12-31") to 1000.0,LocalDate.parse("2026-01-01") to 4000.0))
        assertEquals(2,Achievements.evaluate(listOf(r)).count { it.id.startsWith("holiday-") })
    }
    @Test(expected=IllegalArgumentException::class) fun duplicateRunIsRejected() { val r=run("a","2026-01-01");Achievements.evaluate(listOf(r,r)) }
}
