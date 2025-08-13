package com.pedro.encoder.input.audio

class AudioUtils {

    fun applyVolumeAndMix(
        buffer: ByteArray, volume: Float,
        buffer2: ByteArray, volume2: Float,
        dst: ByteArray
    ) {
        if (buffer.size != buffer2.size) return
        for (i in buffer.indices step 2) {
            val sample1 = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF))
            val sample2 = ((buffer2[i + 1].toInt() shl 8) or (buffer2[i].toInt() and 0xFF))

            val signedSample1 = if (sample1 > 32767) sample1 - 65536 else sample1
            val signedSample2 = if (sample2 > 32767) sample2 - 65536 else sample2

            val adjustedSample1 = (signedSample1 * volume).toInt()
            val adjustedSample2 = (signedSample2 * volume2).toInt()
            var mixedSample = adjustedSample1 + adjustedSample2

            mixedSample = mixedSample.coerceIn(-32768, 32767)
            val unsignedSample = if (mixedSample < 0) mixedSample + 65536 else mixedSample
            dst[i] = (unsignedSample and 0xFF).toByte()
            dst[i + 1] = ((unsignedSample shr 8) and 0xFF).toByte()
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