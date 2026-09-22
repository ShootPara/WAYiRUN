package com.example.runningapp.coaching

import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class CoachingWaveTest {
    @Test fun streamedHeaderIsFinalizedForAndroidPlayback() {
        val bytes=ByteArray(751244)
        val header="52494646FFFFFFFF57415645666D74201000000001000100C05D000080BB00000200100064617461FFFFFFFF"
        header.chunked(2).forEachIndexed {i,s->bytes[i]=s.toInt(16).toByte()}
        val file=File(ApplicationProvider.getApplicationContext<Context>().cacheDir,"wave-regression.wav")
        fun prepares():Boolean {val p=MediaPlayer();return try {p.setDataSource(file.absolutePath);p.prepare();true} catch(_:Exception){false} finally{p.release()}}
        try {
            file.writeBytes(bytes)
            file.writeBytes(finalizeCoachingWave(bytes));assertTrue("Finalized WAV must prepare",prepares())
        }finally{file.delete()}
    }
}
