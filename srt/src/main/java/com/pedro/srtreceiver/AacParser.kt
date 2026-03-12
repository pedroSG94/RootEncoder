package com.pedro.srtreceiver

import android.util.Log

data class AacFrame(
    val data: ByteArray,
    val sampleRate: Int,
    val channelConfig: Int,
    val pts: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AacFrame

        if (!data.contentEquals(other.data)) return false
        if (sampleRate != other.sampleRate) return false
        if (channelConfig != other.channelConfig) return false
        if (pts != other.pts) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channelConfig
        result = 31 * result + pts.hashCode()
        return result
    }
}

class AacParser {
    
    companion object {
        private const val TAG = "AacParser"
        
        private val SAMPLE_RATES = intArrayOf(
            96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
            16000, 12000, 11025, 8000, 7350, 0, 0, 0
        )
    }
    
    var onAacFrame: ((AacFrame) -> Unit)? = null
    
    fun parse(pesData: ByteArray, pts: Long) {
        var offset = 0
        
        while (offset < pesData.size) {
            // Check for ADTS sync word (0xFFF)
            if (offset + 7 > pesData.size) break
            
            val syncWord = ((pesData[offset].toInt() and 0xFF) shl 4) or 
                          ((pesData[offset + 1].toInt() and 0xF0) shr 4)
            
            if (syncWord != 0xFFF) {
                offset++
                continue
            }
            
            // Parse ADTS header
            val byte1 = pesData[offset + 1].toInt() and 0xFF
            val byte2 = pesData[offset + 2].toInt() and 0xFF
            val byte3 = pesData[offset + 3].toInt() and 0xFF
            val byte4 = pesData[offset + 4].toInt() and 0xFF
            val byte5 = pesData[offset + 5].toInt() and 0xFF
            val byte6 = pesData[offset + 6].toInt() and 0xFF
            
            val profile = (byte2 and 0xC0) shr 6
            val sampleRateIndex = (byte2 and 0x3C) shr 2
            val channelConfig = ((byte2 and 0x01) shl 2) or ((byte3 and 0xC0) shr 6)
            
            val frameLength = ((byte3 and 0x03) shl 11) or 
                            (byte4 shl 3) or 
                            ((byte5 and 0xE0) shr 5)
            
            if (sampleRateIndex >= SAMPLE_RATES.size) {
                offset++
                continue
            }
            
            val sampleRate = SAMPLE_RATES[sampleRateIndex]
            if (sampleRate == 0) {
                offset++
                continue
            }
            
            // Extract AAC frame (skip 7-byte ADTS header)
            val headerSize = 7
            val payloadSize = frameLength - headerSize
            
            if (offset + frameLength > pesData.size) {
                break
            }
            
            if (payloadSize > 0) {
                val aacData = ByteArray(payloadSize)
                System.arraycopy(pesData, offset + headerSize, aacData, 0, payloadSize)
                
                val frame = AacFrame(aacData, sampleRate, channelConfig, pts)
                onAacFrame?.invoke(frame)
            }
            
            offset += frameLength
        }
    }
}
