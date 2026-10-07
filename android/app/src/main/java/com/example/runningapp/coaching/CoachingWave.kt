package com.example.runningapp.coaching

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Finalize streaming WAV length placeholders for Android's file-based MediaPlayer.
 * Only header lengths change; the generated speech samples remain byte-for-byte intact.
 */
internal fun finalizeCoachingWave(input: ByteArray): ByteArray {
    require(input.size in 44..4_194_304)
    fun tag(offset: Int) = String(input, offset, 4, Charsets.US_ASCII)
    require(tag(0) == "RIFF" && tag(8) == "WAVE")
    val source = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN)
    fun unsigned(offset: Int) = source.getInt(offset).toLong() and 0xffffffffL
    val riff = unsigned(4)
    require(riff == 0xffffffffL || riff == input.size.toLong() - 8)
    val output = input.copyOf()
    val target = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
    if (riff == 0xffffffffL) target.putInt(4, input.size - 8)
    var offset = 12
    var dataFound = false
    while (offset < input.size) {
        require(input.size - offset >= 8)
        val declared = unsigned(offset + 4)
        val isData = tag(offset) == "data"
        val remaining = input.size - offset - 8
        val size = if (declared == 0xffffffffL) {
            require(isData && remaining > 0 && remaining % 2 == 0)
            target.putInt(offset + 4, remaining)
            remaining
        } else {
            require(declared <= remaining.toLong())
            declared.toInt()
        }
        if (isData) { require(!dataFound && size > 0); dataFound = true }
        offset += 8 + size + size % 2
        require(offset <= input.size)
    }
    require(dataFound)
    return output
}
