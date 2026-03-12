package com.pedro.srtreceiver

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

class VideoDecoder(private val surface: Surface) {
    
    companion object {
        private const val TAG = "VideoDecoder"
        private const val MIME_TYPE = "video/avc"
        private const val TIMEOUT_US = 10000L
    }
    
    private var codec: MediaCodec? = null
    private var isConfigured = false
    private var width = 1920
    private var height = 1080
    
    @Volatile
    private var isRunning = false
    
    fun configure(sps: List<ByteArray>, pps: List<ByteArray>) {
        if (isConfigured) {
            Log.w(TAG, "Decoder already configured")
            return
        }
        
        try {
            // Parse SPS to get resolution
            if (sps.isNotEmpty()) {
                val dimensions = parseSps(sps[0])
                width = dimensions.first
                height = dimensions.second
            }
            
            Log.i(TAG, "Configuring video decoder: ${width}x${height}")
            
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)
            
            // Add SPS/PPS as CSD (Codec Specific Data)
            val csd0 = ByteBuffer.allocate(sps[0].size + 4)
            csd0.put(byteArrayOf(0x00, 0x00, 0x00, 0x01))
            csd0.put(sps[0])
            csd0.flip()
            format.setByteBuffer("csd-0", csd0)
            
            val csd1 = ByteBuffer.allocate(pps[0].size + 4)
            csd1.put(byteArrayOf(0x00, 0x00, 0x00, 0x01))
            csd1.put(pps[0])
            csd1.flip()
            format.setByteBuffer("csd-1", csd1)
            
            codec = MediaCodec.createDecoderByType(MIME_TYPE)
            codec?.configure(format, surface, null, 0)
            codec?.start()
            
            isConfigured = true
            isRunning = true
            Log.i(TAG, "Video decoder started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure decoder", e)
            codec?.release()
            codec = null
        }
    }
    
    fun decode(nalUnit: ByteArray, pts: Long) {
        if (!isConfigured || codec == null) {
            return
        }
        
        try {
            val inputIndex = codec?.dequeueInputBuffer(TIMEOUT_US) ?: -1
            if (inputIndex >= 0) {
                val inputBuffer = codec?.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                
                // Add start code
                inputBuffer?.put(byteArrayOf(0x00, 0x00, 0x00, 0x01))
                inputBuffer?.put(nalUnit)
                
                codec?.queueInputBuffer(inputIndex, 0, nalUnit.size + 4, pts, 0)
            }
            
            // Dequeue output
            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = codec?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
            
            while (outputIndex >= 0) {
                codec?.releaseOutputBuffer(outputIndex, true) // Render to surface
                outputIndex = codec?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
            }
            
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec?.outputFormat
                Log.i(TAG, "Output format changed: $newFormat")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding video", e)
        }
    }
    
    fun stop() {
        isRunning = false
        
        try {
            codec?.stop()
            codec?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping decoder", e)
        }
        
        codec = null
        isConfigured = false
        Log.i(TAG, "Video decoder stopped")
    }
    
    private fun parseSps(sps: ByteArray): Pair<Int, Int> {
        // Simplified SPS parsing to get width and height
        // In production, use a proper H.264 parser
        // For now, return default values
        return Pair(1920, 1080)
    }
    
    fun isReady(): Boolean = isConfigured
}
