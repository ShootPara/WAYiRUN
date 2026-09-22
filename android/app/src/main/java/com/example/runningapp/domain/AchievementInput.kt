package com.example.runningapp.domain

import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate

data class AchievementSample(val delta: Double,val total: Double,val active: Long,val segment: Long)

fun achievementInput(s: RunSnapshot, zone: String, samples: List<AchievementSample>): AchievementRun {
    val z=ZoneId.of(zone)
    fun date(ms:Long)=Instant.ofEpochMilli(ms).atZone(z).toLocalDate()
    val movement=linkedMapOf<LocalDate,Double>()
    for(m in samples) {
        if(m.delta<=0 || !m.delta.isFinite()) continue
        val interval=s.activeIntervals.firstOrNull { m.active>it.startActiveMs && m.active<=(it.endActiveMs?:s.activeDurationMs) } ?: continue
        val d=date(interval.startUtcMs+m.active-interval.startActiveMs)
        movement[d]=(movement[d]?:0.0)+m.delta
    }
    // Old/synthetic archives lacking timed measurements qualify only when the entire run shares one local date.
    if(samples.isEmpty() && s.startedUtcMs!=null && s.endedUtcMs!=null && date(s.startedUtcMs)==date(s.endedUtcMs)) movement[date(s.startedUtcMs)]=s.distanceMeters
    val points=mutableListOf(AchievementPoint(0.0,0.0)); var total=0.0; var lastActive=0L
    var valid=s.segments.size==1 && s.activeIntervals.size==1
    for(m in samples) {
        if(m.delta<=0) continue
        if(kotlin.math.abs(m.total-total-m.delta)>0.000001 || m.active<=lastActive || m.active-lastActive>10000) valid=false
        points.add(AchievementPoint(m.total,m.active.toDouble())); total=m.total; lastActive=m.active
    }
    return AchievementRun(s.runId,s.endedUtcMs?:Long.MAX_VALUE,s.distanceMeters,s.activeDurationMs,movement,if(valid) points else emptyList(),
        (s.endedUtcMs?:s.startedUtcMs)?.let { date(it) })
}
