package com.example.runningapp.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.runningapp.domain.*
import com.example.runningapp.storage.RoutePoint
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class PhotoRenderTest {
    @Test fun wideRouteMovesIntoStatsPanelWithoutChangingHorizontalPositionOrScale() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val directory=File(context.getExternalFilesDir(null),"route-position").apply {mkdirs()}
        val run=RunController("position-photo",RunSettings(RunMode.OUTDOOR,RunUnits.MILES,0,RunGoal.None,null),RunClock {RunTime(0,0)})
        run.start();run.finish()
        val route=listOf(0.0 to 0.0,0.0 to .02,.004 to .02,.004 to 0.0,0.0 to 0.0)
            .mapIndexed { i,(lat,lon)->RoutePoint(i.toLong()+1,"position-photo",1,i*1000L,lat,lon,5f) }
        for((w,h) in listOf(1200 to 1600,600 to 1000,1000 to 600,1000 to 1000)) {
            for(weather in listOf(null,PhotoWeather.parse(weatherFixture())!!)) {
                val source=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.BLUE)}
                val bytes=renderPhoto(source,run.snapshot(),route,listOf(true,true,true,true,true),weather)
                val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
                var minX=w;var maxX=-1;var minY=h;var maxY=-1
                for(y in 0 until h) for(x in 0 until w) {
                    val color=bitmap.getPixel(x,y)
                    if(Color.green(color)>170 && Color.green(color)>Color.red(color)+20 && Color.red(color)>120 && Color.blue(color)<150) {
                        minX=minOf(minX,x);maxX=maxOf(maxX,x);minY=minOf(minY,y);maxY=maxOf(maxY,y)
                    }
                }
                val textSize=minOf(w/20f,h/16f)
                val creditHeight=if(weather!=null) minOf(w*.021f,h*.024f)*3.4f else 0f
                val panel=textSize*1.45f*5+creditHeight
                val routeTop=if(weather!=null) h*.05f+textSize*3.3f else h*.04f
                val size=minOf(w*.38f,maxOf(0f,h-panel-h*.04f-routeTop))
                val routeHeight=size*.004/(.02*kotlin.math.cos(Math.toRadians(.002)))
                assertTrue("Route was rendered",maxX>=minX)
                assertEquals((w-size-w*.07f+size/2).toDouble(),(minX+maxX)/2.0,3.0)
                assertEquals(size.toDouble(),(maxX-minX).toDouble(),10.0)
                assertEquals(routeHeight,(maxY-minY).toDouble(),10.0)
                assertEquals((h-panel+(panel-creditHeight)/2).toDouble(),(minY+maxY)/2.0,3.0)
                assertTrue("Route is below panel top",minY>=h-panel)
                assertTrue("Route is above attribution/bottom",maxY<h-creditHeight)
                File(directory,"route-$w-$h-${weather!=null}.jpg").writeBytes(bytes)
                bitmap.recycle();source.recycle()
            }
        }
    }

    @Test fun routeHasNoRectangleAndDoesNotJoinSeparateSegments() {
        val run=RunController("route-photo",RunSettings(RunMode.OUTDOOR,RunUnits.MILES,0,RunGoal.None,null),RunClock {RunTime(0,0)})
        run.start();run.finish()
        val source=Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.WHITE)}
        val flags=listOf(false,false,false,true)
        val separated=listOf(RoutePoint(1,"route-photo",1,0,40.0,-74.0,5f),RoutePoint(2,"route-photo",2,20000,40.01,-73.99,5f))
        val plain=renderPhoto(source,run.snapshot(),emptyList(),flags)
        val gap=renderPhoto(source,run.snapshot(),separated,flags)
        assertArrayEquals(plain,gap)
        assertArrayEquals(plain,renderPhoto(source,run.snapshot(),separated.map {it.copy(segmentId=1)},flags))
        val joined=renderPhoto(source,run.snapshot(),separated.mapIndexed {i,p->p.copy(segmentId=1,monotonicMs=i*1000L)},flags)
        assertFalse(plain.contentEquals(joined))
        val bitmap=BitmapFactory.decodeByteArray(joined,0,joined.size)
        assertTrue(Color.red(bitmap.getPixel(560,500))>230)
        bitmap.recycle();source.recycle()
    }

    @Test fun weatherToggleChangesOnlyNewRenderingAcrossPhotoShapes() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val directory=File(context.getExternalFilesDir(null),"milestone-5-1").apply {mkdirs()}
        val run=RunController("weather-photo",RunSettings(RunMode.OUTDOOR,RunUnits.MILES,0,RunGoal.None,null),RunClock {RunTime(0,0)})
        run.start();run.finish()
        val weather=PhotoWeather.parse(weatherFixture())!!
        val route=listOf(RoutePoint(1,"weather-photo",1,0,40.0,-74.0,5f),RoutePoint(2,"weather-photo",1,1000,40.01,-73.99,5f))
        for((w,h) in listOf(600 to 1000,1000 to 600,1000 to 1000)) {
            val source=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.BLUE)}
            val off=renderPhoto(source,run.snapshot(),route,listOf(true,true,true,true,false),weather)
            assertArrayEquals(off,renderPhoto(source,run.snapshot(),route,listOf(true,true,true,true,true),null))
            val on=renderPhoto(source,run.snapshot(),route,listOf(true,true,true,true,true),weather)
            assertFalse(off.contentEquals(on));assertTrue(on.size<=1_000_000)
            assertArrayEquals(on,renderPhoto(source,run.snapshot(),route,listOf(true,true,true,true,true),weather))
            File(directory,"weather-$w-$h.jpg").writeBytes(on);source.recycle()
        }
    }
    @Test fun largeImageIsBoundedAndOverlayChangesOnlyWhenSelected() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val input=Bitmap.createBitmap(3200,2400,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.BLUE)}
        val file=File(context.cacheDir,"render-test.jpg")
        file.outputStream().use {input.compress(Bitmap.CompressFormat.JPEG,95,it)};input.recycle()
        val decoded=decodePhoto(context,Uri.fromFile(file));assertEquals(1600,decoded.width);assertEquals(1200,decoded.height)
        val r=RunController("photo-test",RunSettings(RunMode.OUTDOOR,RunUnits.MILES,0,RunGoal.None,null),RunClock {RunTime(0,0)})
        r.start();r.finish()
        val plain=renderPhoto(decoded,r.snapshot(),emptyList(),listOf(false,false,false,false))
        val stats=renderPhoto(decoded,r.snapshot(),emptyList(),listOf(true,true,true,false))
        assertTrue(stats.size<=1000000);assertFalse(plain.contentEquals(stats))
        assertNotNull(BitmapFactory.decodeByteArray(stats,0,stats.size))
        val separated=listOf(RoutePoint(1,"photo-test",1,0,40.0,-70.0,1f),RoutePoint(2,"photo-test",2,20000,41.0,-71.0,1f))
        val gap=renderPhoto(decoded,r.snapshot(),separated,listOf(false,false,false,true))
        assertTrue(gap.size<=1000000)
        decoded.recycle();file.delete()
    }
}
