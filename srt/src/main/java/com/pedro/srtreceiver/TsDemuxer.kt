package com.pedro.srtreceiver

import android.util.Log
import java.nio.ByteBuffer

class TsDemuxer {
    
    companion object {
        private const val TAG = "TsDemuxer"
        private const val TS_PACKET_SIZE = 188
        private const val SYNC_BYTE = 0x47
        private const val PAT_PID = 0x00
    }
    
    private val patParser = PatParser()
    private val pmtParser = PmtParser()
    private val pesAssembler = PesAssembler()
    
    private var pmtPid: Int? = null
    private var videoPid: Int? = null
    private var audioPid: Int? = null
    
    var onVideoPes: ((ByteArray, Long) -> Unit)? = null
    var onAudioPes: ((ByteArray, Long) -> Unit)? = null
    
    fun process(data: ByteArray, size: Int) {
        var offset = 0
        
        while (offset + TS_PACKET_SIZE <= size) {
            val packet = parseTsPacket(data, offset)
            offset += TS_PACKET_SIZE
            
            if (packet == null) {
                Log.w(TAG, "Failed to parse TS packet")
                continue
            }
            
            when (packet.pid) {
                PAT_PID -> {
                    if (packet.payloadStart) {
                        val newPmtPid = patParser.parse(packet.payload)
                        if (newPmtPid != null && newPmtPid != pmtPid) {
                            pmtPid = newPmtPid
                            Log.i(TAG, "PMT PID updated: $pmtPid")
                        }
                    }
                }
                pmtPid -> {
                    if (packet.payloadStart) {
                        val (newVideoPid, newAudioPid) = pmtParser.parse(packet.payload)
                        if (newVideoPid != null && newVideoPid != videoPid) {
                            videoPid = newVideoPid
                            Log.i(TAG, "Video PID updated: $videoPid")
                        }
                        if (newAudioPid != null && newAudioPid != audioPid) {
                            audioPid = newAudioPid
                            Log.i(TAG, "Audio PID updated: $audioPid")
                        }
                    }
                }
                videoPid -> {
                    pesAssembler.addPacket(packet, true) { pesData, pts ->
                        onVideoPes?.invoke(pesData, pts)
                    }
                }
                audioPid -> {
                    pesAssembler.addPacket(packet, false) { pesData, pts ->
                        onAudioPes?.invoke(pesData, pts)
                    }
                }
            }
        }
    }
    
    private fun parseTsPacket(data: ByteArray, offset: Int): TsPacket? {
        if (offset + TS_PACKET_SIZE > data.size) return null
        
        // Check sync byte
        val syncByte = data[offset].toInt() and 0xFF
        if (syncByte != SYNC_BYTE) {
            return null
        }
        
        // Parse header
        val byte1 = data[offset + 1].toInt() and 0xFF
        val byte2 = data[offset + 2].toInt() and 0xFF
        val byte3 = data[offset + 3].toInt() and 0xFF
        
        val payloadUnitStartIndicator = (byte1 and 0x40) != 0
        val pid = ((byte1 and 0x1F) shl 8) or byte2
        val adaptationFieldControl = (byte3 and 0x30) shr 4
        val continuityCounter = byte3 and 0x0F
        
        var payloadOffset = offset + 4
        
        // Handle adaptation field
        if (adaptationFieldControl == 2 || adaptationFieldControl == 3) {
            if (payloadOffset >= offset + TS_PACKET_SIZE) return null
            val adaptationFieldLength = data[payloadOffset].toInt() and 0xFF
            payloadOffset += 1 + adaptationFieldLength
        }
        
        // Check if there's payload
        if (adaptationFieldControl == 1 || adaptationFieldControl == 3) {
            val payloadLength = (offset + TS_PACKET_SIZE) - payloadOffset
            if (payloadLength > 0 && payloadOffset < offset + TS_PACKET_SIZE) {
                val payload = ByteArray(payloadLength)
                System.arraycopy(data, payloadOffset, payload, 0, payloadLength)
                return TsPacket(pid, payloadUnitStartIndicator, continuityCounter, payload)
            }
        }
        
        return null
    }
}
