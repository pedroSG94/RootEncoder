package com.pedro.encoder.input.audio

class AdjustVolumeEffect: CustomAudioEffect() {

    var volume = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    override fun process(pcmBuffer: ByteArray): ByteArray {
        for (i in pcmBuffer.indices step 2) {
            var buf1 = pcmBuffer[i + 1].toShort()
            var buf2 = pcmBuffer[i].toShort()
            buf1 = ((buf1.toInt() and 0xff) shl 8).toShort()
            buf2 = (buf2.toInt() and 0xff).toShort()
            var res = (buf1.toInt() or buf2.toInt()).toShort()
            res = (res * volume).toInt().toShort()
            pcmBuffer[i] = res.toByte()
            pcmBuffer[i + 1] = (res.toInt() shr 8).toByte()
        }
        return pcmBuffer
    }
}