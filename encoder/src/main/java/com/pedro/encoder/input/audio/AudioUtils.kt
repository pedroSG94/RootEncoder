package com.pedro.encoder.input.audio

class AudioUtils {

    private val minShort = Short.MIN_VALUE.toInt()
    private val maxShort = Short.MAX_VALUE.toInt()

    fun applyVolumeAndMix(
        buffer: ByteArray, volume: Float,
        buffer2: ByteArray, volume2: Float,
    ) {
        if (buffer.size != buffer2.size) return
        if (volume == 1f && volume2 == 1f) {
            for (i in buffer.indices) {
                buffer[i] = (buffer[i] + buffer2[i]).toByte().coerceIn(Byte.MIN_VALUE, Byte.MAX_VALUE)
            }
            return
        }
        for (i in buffer.indices step 2) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF))
            val adjustedSample = (sample * volume).toInt()

            val sample2 = ((buffer2[i + 1].toInt() shl 8) or (buffer2[i].toInt() and 0xFF))
            val adjustedSample2 = (sample2 * volume2).toInt().coerceIn(minShort, maxShort)

            buffer[i] = (adjustedSample.toByte() + adjustedSample2.toByte()).toByte()
            buffer[i + 1] = ((adjustedSample shr 8).toByte() + (adjustedSample2 shr 8).toByte()).toByte()
        }
    }

    fun applyVolume(buffer: ByteArray, volume: Float) {
        if (volume == 1f) return

        for (i in buffer.indices step 2) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF))
            val adjustedSample = (sample * volume).toInt().coerceIn(minShort, maxShort)
            buffer[i] = adjustedSample.toByte()
            buffer[i + 1] = (adjustedSample shr 8).toByte()
        }
    }
}