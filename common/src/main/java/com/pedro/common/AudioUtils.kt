package com.pedro.common

import java.nio.ByteBuffer

object AudioUtils {
    const val ADTS_SIZE = 7 //Never use crc (with crc the size is 9)

    fun createAdtsHeader(type: Int, length: Int, sampleRate: Int, channels: Int): ByteBuffer {
        val header = ByteArray(ADTS_SIZE)

        header[0] = 0xFF.toByte()
        header[1] = 0xF1.toByte()
        header[2] = (((type - 1) shl 6) or (getFrequency(sampleRate) shl 2) or (channels shr 2)).toByte()
        header[3] = (((channels and 3) shl 6) or (length shr 11)).toByte()
        header[4] = ((length and 0x7FF) shr 3).toByte()
        header[5] = (((length and 7) shl 5).toByte()).plus(0x1F).toByte()
        header[6] = 0xFC.toByte()
        return ByteBuffer.wrap(header)
    }

    fun getFrequency(sampleRate: Int): Int {
        var frequency = AUDIO_SAMPLING_RATES.indexOf(sampleRate)
        //sane check, if sample rate not found using default 44100
        if (frequency == -1) frequency = 4
        return frequency
    }

    private val AUDIO_SAMPLING_RATES = intArrayOf(
        96000,  // 0
        88200,  // 1
        64000,  // 2
        48000,  // 3
        44100,  // 4
        32000,  // 5
        24000,  // 6
        22050,  // 7
        16000,  // 8
        12000,  // 9
        11025,  // 10
        8000,  // 11
        7350,  // 12
        -1,  // 13
        -1,  // 14
        -1)
}