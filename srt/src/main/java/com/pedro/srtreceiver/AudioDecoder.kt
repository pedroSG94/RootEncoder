package com.pedro.srtreceiver

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer

class AudioDecoder {
    
    companion object {
        private const val TAG = "AudioDecoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val TIMEOUT_US = 10000L
    }
    
    private var codec: MediaCodec? = null
    private var audioTrack: AudioTrack? = null
    private var isConfigured = false
    
    @Volatile
    private var isRunning = false
    
    fun configure(sampleRate: Int, channelConfig: Int) {
        if (isConfigured) {
            return
        }
        
        try {
            Log.i(TAG, "Configuring audio decoder: sampleRate=$sampleRate, channels=$channelConfig")
            
            val channelCount = if (channelConfig == 1) 1 else 2
            val androidChannelConfig = if (channelCount == 1) 
                AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            
            // Create MediaCodec decoder
            val format = MediaFormat.createAudioFormat(MIME_TYPE, sampleRate, channelCount)
            format.setInteger(MediaFormat.KEY_IS_ADTS, 0) // We're providing raw AAC
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            
            codec = MediaCodec.createDecoderByType(MIME_TYPE)
            codec?.configure(format, null, null, 0)
            codec?.start()
            
            // Create AudioTrack
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                androidChannelConfig,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 2
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(androidChannelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            audioTrack?.play()
            
            isConfigured = true
            isRunning = true
            Log.i(TAG, "Audio decoder started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure audio decoder", e)
            codec?.release()
            codec = null
            audioTrack?.release()
            audioTrack = null
        }
    }
    
    fun decode(aacData: ByteArray, pts: Long) {
        if (!isConfigured || codec == null) {
            return
        }
        
        try {
            val inputIndex = codec?.dequeueInputBuffer(TIMEOUT_US) ?: -1
            if (inputIndex >= 0) {
                val inputBuffer = codec?.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(aacData)
                
                codec?.queueInputBuffer(inputIndex, 0, aacData.size, pts, 0)
            }
            
            // Dequeue output
            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = codec?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
            
            while (outputIndex >= 0) {
                val outputBuffer = codec?.getOutputBuffer(outputIndex)
                
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val pcmData = ByteArray(bufferInfo.size)
                    outputBuffer.get(pcmData)
                    outputBuffer.clear()
                    
                    // Play PCM data
                    audioTrack?.write(pcmData, 0, pcmData.size)
                }
                
                codec?.releaseOutputBuffer(outputIndex, false)
                outputIndex = codec?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
            }
            
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec?.outputFormat
                Log.i(TAG, "Output format changed: $newFormat")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding audio", e)
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
        
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio track", e)
        }
        
        codec = null
        audioTrack = null
        isConfigured = false
        Log.i(TAG, "Audio decoder stopped")
    }
    
    fun isReady(): Boolean = isConfigured
}
