package com.example.runningapp.domain

import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import kotlinx.serialization.Serializable

@Serializable
data class Achievement(val id: String, val occurrence: String, val runId: String, val name: String,
    val detail: String, val date: String, val version: Int = 1)
data class AchievementPoint(val meters: Double, val activeMs: Double)
data class AchievementRun(val id: String, val endedMs: Long, val meters: Double, val activeMs: Long,
    val movement: Map<LocalDate, Double>, val points: List<AchievementPoint> = emptyList(), val endDate: LocalDate? = null)

/** Pure, deterministic projection of one owner's retained history; never depends on today's date or display units. */
object Achievements {
    private val distances = listOf(1000.0,1609.344,5000.0,10000.0,16093.44,21097.5,32186.88,42195.0,50000.0)
    private val firstNames = listOf("Kicking It Off","Mile Marker","Five Alive","Double Digits","Going the Distance","Halfway to Legendary","The Long Game","Marathon Mindset","Beyond the Finish")
    private val lifetime = listOf(50,100,250,500,1000,2500,5000)
    private val lifetimeNames = listOf("Finding Your Stride","Making Tracks","Road Regular","Horizon Hunter","Distance Devourer","Long Haul Legend","Beyond the Horizon")
    private fun monday(d: LocalDate) = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun evaluate(input: List<AchievementRun>): List<Achievement> {
        require(input.map { it.id }.distinct().size == input.size)
        val result = linkedMapOf<String, Achievement>()
        val runs = input.sortedWith(compareBy<AchievementRun> { it.endedMs }.thenBy { it.id })
        var total = 0.0; var count = 0; var longest = 0.0
        val days = sortedSetOf<LocalDate>(); val months = mutableMapOf<String,Double>()
        val best = mutableMapOf<Double,Double>()
        var anniversary: LocalDate? = null
        for (r in runs) {
            require(r.meters.isFinite() && r.meters >= 0 && r.activeMs >= 0)
            if (r.meters <= 0 || r.activeMs <= 0) continue
            val date = r.movement.keys.maxOrNull() ?: r.endDate ?: continue
            fun award(id: String, occurrence: String, name: String, detail: String, at: LocalDate = date) {
                result.putIfAbsent("$id:$occurrence", Achievement(id,occurrence,r.id,name,detail,at.toString()))
            }
            count++; total += r.meters
            award("first-run","once","First Footprint","Your first completed run")
            distances.forEachIndexed { i, target -> if(r.meters >= target) award("distance-$i","once",firstNames[i],"One run of ${target / 1000} km") }
            lifetime.forEachIndexed { i, target -> if(total >= target*1000.0) award("lifetime-$target","once",lifetimeNames[i],"$target lifetime km") }
            listOf(10,50,100,1000).forEachIndexed { i,n -> if(count>=n) award("count-$n","once",listOf("Showing Up","Regular Fixture","Triple-Digit Club","A Thousand Starts")[i],"$n completed runs") }
            if(longest>0 && r.meters>longest) award("longest",r.id,"Raising the Bar","Your longest run so far")
            longest=maxOf(longest,r.meters)
            if(anniversary==null) anniversary=r.movement.keys.minOrNull() ?: date
            for((d, meters) in r.movement.toSortedMap()) {
                if(!meters.isFinite() || meters<=0) continue
                days.add(d)
                val month=d.toString().take(7); months[month]=(months[month]?:0.0)+meters
                listOf(25,50,100,200).forEachIndexed { i,n -> if(months.getValue(month)>=n*1000.0) award("month-$n",month,listOf("Making Headway","Month in Motion","Triple-Digit Month","Going Places")[i],"$n km in $month",d) }
                val week=monday(d); val nDays=days.count { monday(it)==week }
                listOf(3,5,7).forEachIndexed { i,n -> if(nDays>=n) award("week-$n",week.toString(),listOf("Three's a Stride","High Five","Full House")[i],"$n run days in the week of $week",d) }
                val weeks=days.map { monday(it) }.toSet(); var start=week; var streak=1
                while(weeks.contains(start.minusWeeks(1))) { start=start.minusWeeks(1); streak++ }
                listOf(4,8,13,52).forEachIndexed { i,n -> if(streak>=n) award("streak-$n",start.toString(),listOf("Finding a Rhythm","On a Roll","Seasoned Strider","Year in Motion")[i],"$n consecutive weeks with a run",d) }
                if(d.dayOfWeek==DayOfWeek.SUNDAY) listOf(1,5,10).forEachIndexed { i,n -> if(meters>=n*1000.0) award("sunday-$n",d.toString(),listOf("Sunday Strollout","Sunday Fiver","Sunday Long Game")[i],"$n km in a Sunday run",d) }
                holidays(d).forEach { (id,name,holiday) -> award("holiday-$id",d.year.toString(),name,holiday,d) }
                val a=anniversary!!
                if(d.year>a.year && d.month==a.month && d.dayOfMonth==a.dayOfMonth) award("anniversary",d.year.toString(),"Another Lap Around the Sun","Your running anniversary",d)
            }
            val p=r.points
            if(validCurve(p,r.meters)) {
                listOf(1000.0,1609.344,5000.0,10000.0,21097.5,42195.0).forEachIndexed { i,target ->
                    if(r.meters>=target) {
                        val time=bestEffort(p,target); val old=best[target]
                        if(old!=null && time<old-0.001) award("pr-$i",r.id,"Personal Best: ${listOf("1K","Mile","5K","10K","Half","Marathon")[i]}","Fastest recorded $target m effort")
                        best[target]=minOf(old?:Double.POSITIVE_INFINITY,time)
                    }
                }
                if(r.meters>=2000) {
                    val half=timeAt(p,r.meters/2); val end=timeAt(p,r.meters)
                    if(end-half<=half*0.98) award("negative-split",r.id,"Strong Finish","Second half at least 2% faster")
                }
                var previous=Double.POSITIVE_INFINITY; var chain=0
                for(k in 1..(r.meters/1000).toInt()) {
                    val duration=timeAt(p,k*1000.0)-timeAt(p,(k-1)*1000.0)
                    chain=if(duration<=previous*0.98) chain+1 else 1; previous=duration
                    if(chain>=3) award("progression",r.id,"Building Steam","Three progressively faster kilometer splits")
                }
            }
        }
        return result.values.toList()
    }
    private fun validCurve(p: List<AchievementPoint>, meters: Double) = p.size>=2 && p.first()==AchievementPoint(0.0,0.0) &&
        kotlin.math.abs(p.last().meters-meters)<0.000001 && p.all { it.meters.isFinite() && it.activeMs.isFinite() } &&
        p.zipWithNext().all { (a,b) -> b.meters>a.meters && b.activeMs>a.activeMs }
    private fun timeAt(p: List<AchievementPoint>, distance: Double): Double {
        if(distance<=0) return 0.0
        var lo=1; var hi=p.lastIndex
        while(lo<hi) { val mid=(lo+hi)/2; if(p[mid].meters<distance) lo=mid+1 else hi=mid }
        val a=p[lo-1]; val b=p[lo]
        return a.activeMs+(b.activeMs-a.activeMs)*(distance-a.meters)/(b.meters-a.meters)
    }
    private fun bestEffort(p: List<AchievementPoint>, distance: Double): Double {
        val max=p.last().meters-distance
        return p.asSequence().flatMap { sequenceOf(it.meters,it.meters-distance) }.plus(sequenceOf(0.0,max))
            .filter { it>=0 && it<=max }.minOf { timeAt(p,it+distance)-timeAt(p,it) }
    }
    fun holidays(d: LocalDate): List<Triple<String,String,String>> {
        val y=d.year
        fun nth(m:Int,day:DayOfWeek,n:Int)=LocalDate.of(y,m,1).with(TemporalAdjusters.dayOfWeekInMonth(n,day))
        val dates=listOf(
            "new-year" to LocalDate.of(y,1,1), "mlk" to nth(1,DayOfWeek.MONDAY,3), "valentine" to LocalDate.of(y,2,14),
            "presidents" to nth(2,DayOfWeek.MONDAY,3), "patrick" to LocalDate.of(y,3,17), "easter" to easter(y),
            "earth" to LocalDate.of(y,4,22), "mother" to nth(5,DayOfWeek.SUNDAY,2), "memorial" to nth(5,DayOfWeek.MONDAY,-1),
            "running" to nth(6,DayOfWeek.WEDNESDAY,1), "juneteenth" to LocalDate.of(y,6,19), "father" to nth(6,DayOfWeek.SUNDAY,3),
            "independence" to LocalDate.of(y,7,4), "labor" to nth(9,DayOfWeek.MONDAY,1), "indigenous" to nth(10,DayOfWeek.MONDAY,2),
            "halloween" to LocalDate.of(y,10,31), "veterans" to LocalDate.of(y,11,11), "thanksgiving" to nth(11,DayOfWeek.THURSDAY,4),
            "christmas-eve" to LocalDate.of(y,12,24), "christmas" to LocalDate.of(y,12,25), "year-end" to LocalDate.of(y,12,31))
        val names=listOf("Fresh Tracks","Strides Toward the Dream","Heart & Sole","Executive Pace","Shamrock & Roll","Spring in Your Step","Leave Only Footprints","Miles of Appreciation","Miles of Remembrance","One World, Many Strides","Freedom in Motion","A Run for the Long Haul","Stars, Stripes & Strides","Putting in the Legwork","Honoring the Path","Sole Survivor","Strides of Honor","Gravy Train","Silent Night Strider","Sleigh the Miles","The Final Lap")
        val labels=listOf("New Year's Day","Martin Luther King Jr. Day","Valentine's Day","Presidents' Day","St. Patrick's Day","Easter","Earth Day","Mother's Day","Memorial Day","Global Running Day","Juneteenth","Father's Day","Independence Day","Labor Day","Indigenous Peoples' Day","Halloween","Veterans Day","Thanksgiving","Christmas Eve","Christmas Day","New Year's Eve")
        return dates.mapIndexedNotNull { i,(id,date) -> if(date==d) Triple(id,names[i],labels[i]) else null }
    }
    private fun easter(y:Int):LocalDate {
        val a=y%19; val b=y/100; val c=y%100; val h=(19*a+b-b/4-(b-(b+8)/25+1)/3+15)%30
        val l=(32+2*(b%4)+2*(c/4)-h-c%4)%7; val m=(a+11*h+22*l)/451
        val n=h+l-7*m+114; return LocalDate.of(y,n/31,n%31+1)
    }
}
