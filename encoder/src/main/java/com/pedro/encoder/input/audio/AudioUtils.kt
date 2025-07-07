package com.pedro.encoder.input.audio

class AudioUtils {

    fun applyVolumeAndMix(
        buffer: ByteArray, volume: Float,
        buffer2: ByteArray, volume2: Float,
        dst: ByteArray
    ) {
        if (buffer.size != buffer2.size) return
        if (volume == 1f && volume2 == 1f) {
            for (i in buffer.indices) {
                dst[i] = (buffer[i] + buffer2[i]).toByte()
            }
            return
        }
        for (i in buffer.indices step 2) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF))
            val adjustedSample = (sample * volume).toInt()
            val sample2 = ((buffer2[i + 1].toInt() shl 8) or (buffer2[i].toInt() and 0xFF))
            val adjustedSample2 = (sample2 * volume2).toInt()

            dst[i] = (adjustedSample.toByte() + adjustedSample2.toByte()).toByte()
            dst[i + 1] = ((adjustedSample shr 8).toByte() + (adjustedSample2 shr 8).toByte()).toByte()
        }
    }

    fun applyVolume(buffer: ByteArray, volume: Float) {
        if (volume == 1f) return

        for (i in buffer.indices step 2) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF))
            val adjustedSample = (sample * volume).toInt()
            buffer[i] = adjustedSample.toByte()
            buffer[i + 1] = (adjustedSample shr 8).toByte()
        }
    }

    /**
     * assume always pcm 16bit
     * @return value from 0f to 100f
     */
    fun calculateAmplitude(buffer: ByteArray): Float {
        if (buffer.size % 2 != 0) return 0f //invalid buffer
        var amplitude = 0
        for (i in buffer.indices step 2) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF))
            if (sample > amplitude) amplitude = sample
        }
        return (amplitude / Short.MAX_VALUE.toFloat()) * 100
    }
}