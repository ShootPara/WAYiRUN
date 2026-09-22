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
