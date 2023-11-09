package com.pedro.common

import android.media.MediaCodec
import java.nio.ByteBuffer

interface StreamClient {
    val droppedAudioFrames: Long
    val droppedVideoFrames: Long

    val cacheSize: Int
    val sentAudioFrames: Long
    val sentVideoFrames: Long

    fun setVideoCodec(videoCodec: VideoCodec)
    fun setCheckServerAlive(enabled: Boolean)
    fun setOnlyAudio(onlyAudio: Boolean)
    fun setOnlyVideo(onlyVideo: Boolean)
    fun setAuthorization(user: String?, password: String?)
    fun setReTries(reTries: Int)
    fun shouldRetry(reason: String): Boolean
    fun setAudioInfo(sampleRate: Int, isStereo: Boolean)
    fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?)
    fun connect(url: String?)
    fun connect(url: String?, isRetry: Boolean)
    fun disconnect()
    fun reConnect(delay: Long)
    fun reConnect(delay: Long, backupUrl: String?)
    fun sendVideo(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo)
    fun sendAudio(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo)
    fun hasCongestion(): Boolean
    fun hasCongestion(percentUsed: Float = 20f): Boolean
    fun resetSentAudioFrames()
    fun resetSentVideoFrames()
    fun resetDroppedAudioFrames()
    fun resetDroppedVideoFrames()

    @Throws(RuntimeException::class)
    fun resizeCache(newSize: Int)
    fun setLogs(enable: Boolean)
    fun clearCache()
    fun getItemsInCache(): Int
}