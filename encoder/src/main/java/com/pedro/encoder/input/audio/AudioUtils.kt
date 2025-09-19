package com.pedro.encoder.input.audio

import kotlin.math.abs

class AudioUtils {

    fun applyVolumeAndMix(
        buffer: ByteArray, volume: Float,
        buffer2: ByteArray, volume2: Float,
        dst: ByteArray
    ) {
        if (buffer.size != buffer2.size) return
        for (i in buffer.indices step 2) {
            val sample1 = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toInt()
            val sample2 = ((buffer2[i + 1].toInt() shl 8) or (buffer2[i].toInt() and 0xFF)).toShort().toInt()

            val adjustedSample1 = (sample1 * volume).toInt()
            val adjustedSample2 = (sample2 * volume2).toInt()
            val mixedSample = (adjustedSample1 + adjustedSample2).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()) and 0xFFFF
            dst[i] = (mixedSample and 0xFF).toByte()
            dst[i + 1] = ((mixedSample shr 8) and 0xFF).toByte()
        }
    }

    fun applyVolume(buffer: ByteArray, volume: Float) {
        if (volume == 1f) return

        for (i in buffer.indices step 2) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toInt()
            val adjustedSample = (sample * volume).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()) and 0xFFFF
            buffer[i] = (adjustedSample and 0xFF).toByte()
            buffer[i + 1] = ((adjustedSample shr 8) and 0xFF).toByte()
        }
    }

    /**
     * Calculate amplitude peaks. Assume always pcm 16bit
     * @return value from 0f to 100f
     */
    fun calculateAmplitude(buffer: ByteArray): Float {
        if (buffer.size % 2 != 0) return 0f //invalid buffer
        var amplitude = 0
        for (i in buffer.indices step 2) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toInt()
            val sampleAmplitude = abs(sample)
            if (sampleAmplitude > amplitude) amplitude = sampleAmplitude
        }
        return (amplitude / Short.MAX_VALUE.toFloat()) * 100
    }
}