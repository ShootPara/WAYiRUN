package com.example.runningapp.coaching

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test
import org.junit.Assert.*

class CoachingWaveTest {
    private fun wave():ByteArray {
        val bytes=ByteArray(4844) { (it % 127).toByte() }
        "52494646FFFFFFFF57415645666D74201000000001000100C05D000080BB00000200100064617461FFFFFFFF"
            .chunked(2).forEachIndexed {i,s->bytes[i]=s.toInt(16).toByte()}
        return bytes
    }
    @Test fun streamingLengthsAreFinalizedWithoutChangingSpeechOrInput() {
        val input=wave();val original=input.copyOf();val result=finalizeCoachingWave(input)
        val header=ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(result.size-8,header.getInt(4));assertEquals(result.size-44,header.getInt(40))
        assertArrayEquals(original,input)
        assertArrayEquals(input.copyOfRange(44,input.size),result.copyOfRange(44,result.size))
        assertArrayEquals(input.copyOfRange(8,40),result.copyOfRange(8,40))
    }
    @Test fun alreadyFinalizedAudioIsUnchanged() {
        val finalized=finalizeCoachingWave(wave());assertArrayEquals(finalized,finalizeCoachingWave(finalized))
    }
    @Test fun truncatedOrInvalidChunksFailInsteadOfPlayingCorruptAudio() {
        val good=finalizeCoachingWave(wave())
        for(bad in listOf(good.copyOf(30),good.copyOf(good.size-2),wave().also {it[12]='X'.code.toByte();ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(16,-1)},wave().copyOf(4843))) {
            try {finalizeCoachingWave(bad);fail("Malformed WAV accepted")}catch(_:IllegalArgumentException) {}
        }
    }
}
