package com.example.runningapp.domain

/** Platform-free projection of a completed run; never changes its source measurements. */
data class HealthRun(val id: String, val startMs: Long, val endMs: Long, val activeMs: Long,
    val meters: Double, val indoor: Boolean, val pauses: List<Pair<Long,Long>>) {
    val sessionId get() = "wayirun:$id:session"
    val distanceId get() = "wayirun:$id:distance"
}
fun healthRun(s: RunSnapshot): HealthRun {
    require(s.state == RunState.FINISHED)
    val start=requireNotNull(s.startedUtcMs);val end=requireNotNull(s.endedUtcMs)
    require(end>start && s.activeDurationMs>0 && s.activeDurationMs<=end-start+2000)
    require(s.distanceMeters.isFinite() && s.distanceMeters>=0)
    val pauses=mutableListOf<Pair<Long,Long>>()
    if(s.activeIntervals.isNotEmpty()) {
        var cursor=start
        for(interval in s.activeIntervals) {
            val finish=requireNotNull(interval.endUtcMs)
            require(interval.startUtcMs>=cursor && finish>=interval.startUtcMs && finish<=end)
            if(interval.startUtcMs>cursor)pauses+=cursor to interval.startUtcMs
            cursor=finish
        }
        if(cursor<end)pauses+=cursor to end
    }
    return HealthRun(s.runId,start,end,s.activeDurationMs,s.distanceMeters,s.settings.mode==RunMode.INDOOR,pauses)
}
