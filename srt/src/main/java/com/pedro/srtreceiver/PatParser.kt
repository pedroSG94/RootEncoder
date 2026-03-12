package com.pedro.srtreceiver

import android.util.Log

class PatParser {
    
    companion object {
        private const val TAG = "PatParser"
        private const val PAT_TABLE_ID = 0x00
    }
    
    fun parse(payload: ByteArray): Int? {
        if (payload.isEmpty()) return null
        
        // Skip pointer field if present (first byte)
        var offset = 0
        val pointerField = payload[offset].toInt() and 0xFF
        offset += 1 + pointerField
        
        if (offset >= payload.size) return null
        
        val tableId = payload[offset].toInt() and 0xFF
        if (tableId != PAT_TABLE_ID) {
            return null
        }
        
        offset += 1 // table_id
        
        if (offset + 2 >= payload.size) return null
        
        val sectionSyntaxIndicator = (payload[offset].toInt() and 0x80) != 0
        val sectionLength = ((payload[offset].toInt() and 0x0F) shl 8) or 
                           (payload[offset + 1].toInt() and 0xFF)
        offset += 2
        
        if (!sectionSyntaxIndicator) return null
        
        // Skip transport_stream_id (2 bytes)
        offset += 2
        
        // Skip version and current_next_indicator (1 byte)
        offset += 1
        
        // Skip section_number (1 byte)
        offset += 1
        
        // Skip last_section_number (1 byte)
        offset += 1
        
        // Calculate the number of programs
        // section_length includes everything after section_length field minus CRC32 (4 bytes)
        val dataLength = sectionLength - 9 // Subtract fixed header after section_length and CRC
        val numPrograms = dataLength / 4
        
        // Parse program associations
        for (i in 0 until numPrograms) {
            if (offset + 4 > payload.size) break
            
            val programNumber = ((payload[offset].toInt() and 0xFF) shl 8) or 
                               (payload[offset + 1].toInt() and 0xFF)
            val programMapPid = ((payload[offset + 2].toInt() and 0x1F) shl 8) or 
                               (payload[offset + 3].toInt() and 0xFF)
            
            offset += 4
            
            // Skip program 0 (network PID)
            if (programNumber != 0) {
                Log.d(TAG, "Found PMT PID: $programMapPid for program $programNumber")
                return programMapPid
            }
        }
        
        return null
    }
}
