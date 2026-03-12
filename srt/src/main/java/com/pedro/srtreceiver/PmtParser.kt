package com.pedro.srtreceiver

import android.util.Log

data class StreamInfo(
    val streamType: Int,
    val pid: Int
)

class PmtParser {
    
    companion object {
        private const val TAG = "PmtParser"
        private const val PMT_TABLE_ID = 0x02
        
        const val STREAM_TYPE_H264 = 0x1B
        const val STREAM_TYPE_H265 = 0x24
        const val STREAM_TYPE_AAC = 0x0F
        const val STREAM_TYPE_MP3 = 0x03
    }
    
    fun parse(payload: ByteArray): Pair<Int?, Int?> {
        if (payload.isEmpty()) return Pair(null, null)
        
        // Skip pointer field if present (first byte)
        var offset = 0
        val pointerField = payload[offset].toInt() and 0xFF
        offset += 1 + pointerField
        
        if (offset >= payload.size) return Pair(null, null)
        
        val tableId = payload[offset].toInt() and 0xFF
        if (tableId != PMT_TABLE_ID) {
            return Pair(null, null)
        }
        
        offset += 1 // table_id
        
        if (offset + 2 >= payload.size) return Pair(null, null)
        
        val sectionSyntaxIndicator = (payload[offset].toInt() and 0x80) != 0
        val sectionLength = ((payload[offset].toInt() and 0x0F) shl 8) or 
                           (payload[offset + 1].toInt() and 0xFF)
        offset += 2
        
        if (!sectionSyntaxIndicator) return Pair(null, null)
        
        // Skip program_number (2 bytes)
        offset += 2
        
        // Skip version and current_next_indicator (1 byte)
        offset += 1
        
        // Skip section_number (1 byte)
        offset += 1
        
        // Skip last_section_number (1 byte)
        offset += 1
        
        if (offset + 2 > payload.size) return Pair(null, null)
        
        // Skip PCR_PID (2 bytes)
        offset += 2
        
        if (offset + 2 > payload.size) return Pair(null, null)
        
        val programInfoLength = ((payload[offset].toInt() and 0x0F) shl 8) or 
                               (payload[offset + 1].toInt() and 0xFF)
        offset += 2
        
        // Skip program descriptors
        offset += programInfoLength
        
        var videoPid: Int? = null
        var audioPid: Int? = null
        
        // Parse stream info
        val sectionEnd = 3 + sectionLength - 4 // -4 for CRC
        while (offset + 5 <= payload.size && offset < sectionEnd) {
            val streamType = payload[offset].toInt() and 0xFF
            val elementaryPid = ((payload[offset + 1].toInt() and 0x1F) shl 8) or 
                               (payload[offset + 2].toInt() and 0xFF)
            val esInfoLength = ((payload[offset + 3].toInt() and 0x0F) shl 8) or 
                              (payload[offset + 4].toInt() and 0xFF)
            
            offset += 5 + esInfoLength
            
            when (streamType) {
                STREAM_TYPE_H264, STREAM_TYPE_H265 -> {
                    videoPid = elementaryPid
                    Log.d(TAG, "Found video stream: type=0x${streamType.toString(16)}, pid=$elementaryPid")
                }
                STREAM_TYPE_AAC, STREAM_TYPE_MP3 -> {
                    audioPid = elementaryPid
                    Log.d(TAG, "Found audio stream: type=0x${streamType.toString(16)}, pid=$elementaryPid")
                }
            }
        }
        
        return Pair(videoPid, audioPid)
    }
}
