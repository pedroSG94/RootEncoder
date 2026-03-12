package com.pedro.srtreceiver

import android.util.Log
import java.io.ByteArrayOutputStream

class PesAssembler {
    
    companion object {
        private const val TAG = "PesAssembler"
    }
    
    private val videoBuffer = ByteArrayOutputStream()
    private val audioBuffer = ByteArrayOutputStream()
    private var videoPts: Long = 0
    private var audioPts: Long = 0
    
    fun addPacket(packet: TsPacket, isVideo: Boolean, onPesComplete: (ByteArray, Long) -> Unit) {
        val buffer = if (isVideo) videoBuffer else audioBuffer
        
        if (packet.payloadStart) {
            // Flush previous PES if any
            if (buffer.size() > 0) {
                val pts = if (isVideo) videoPts else audioPts
                onPesComplete(buffer.toByteArray(), pts)
                buffer.reset()
            }
            
            // Check for PES start code
            if (packet.payload.size >= 6) {
                val startCode1 = packet.payload[0].toInt() and 0xFF
                val startCode2 = packet.payload[1].toInt() and 0xFF
                val startCode3 = packet.payload[2].toInt() and 0xFF
                
                if (startCode1 == 0x00 && startCode2 == 0x00 && startCode3 == 0x01) {
                    // Valid PES packet
                    var offset = 6 // Skip packet_start_code_prefix (3) + stream_id (1) + PES_packet_length (2)
                    
                    if (packet.payload.size > offset + 2) {
                        val flags1 = packet.payload[offset].toInt() and 0xFF
                        val flags2 = packet.payload[offset + 1].toInt() and 0xFF
                        val pesHeaderLength = packet.payload[offset + 2].toInt() and 0xFF
                        
                        offset += 3
                        
                        // Check for PTS
                        val ptsDtsFlags = (flags2 and 0xC0) shr 6
                        if (ptsDtsFlags >= 2 && packet.payload.size >= offset + 5) {
                            // Extract PTS
                            val pts = extractPts(packet.payload, offset)
                            if (isVideo) {
                                videoPts = pts
                            } else {
                                audioPts = pts
                            }
                        }
                        
                        // Skip PES header
                        offset += pesHeaderLength
                        
                        // Add payload
                        if (offset < packet.payload.size) {
                            buffer.write(packet.payload, offset, packet.payload.size - offset)
                        }
                    }
                } else {
                    // Not a valid PES start, just add payload
                    buffer.write(packet.payload, 0, packet.payload.size)
                }
            }
        } else {
            // Continuation packet, just append payload
            buffer.write(packet.payload, 0, packet.payload.size)
        }
    }
    
    private fun extractPts(data: ByteArray, offset: Int): Long {
        // PTS is 33 bits, encoded in 5 bytes
        val byte0 = (data[offset].toLong() and 0x0E) shr 1
        val byte1 = data[offset + 1].toLong() and 0xFF
        val byte2 = (data[offset + 2].toLong() and 0xFE) shr 1
        val byte3 = data[offset + 3].toLong() and 0xFF
        val byte4 = (data[offset + 4].toLong() and 0xFE) shr 1
        
        val pts90k = (byte0 shl 30) or (byte1 shl 22) or (byte2 shl 15) or (byte3 shl 7) or byte4
        
        // Convert 90kHz to microseconds
        return (pts90k * 1_000_000) / 90000
    }
}
