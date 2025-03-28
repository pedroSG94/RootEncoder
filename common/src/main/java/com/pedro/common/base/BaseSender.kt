package com.pedro.common.base

import android.util.Log
import com.pedro.common.BitrateManager
import com.pedro.common.ConnectChecker
import com.pedro.common.StreamBlockingQueue
import com.pedro.common.compare
import com.pedro.common.frame.MediaFrame
import com.pedro.common.trySend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.PriorityBlockingQueue

abstract class BaseSender(
    protected val connectChecker: ConnectChecker,
    protected val TAG: String
) {

    @Volatile
    protected var running = false
    private var cacheSize = 400
    @Volatile
    protected var queue = StreamBlockingQueue(cacheSize)
    protected var audioFramesSent: Long = 0
    protected var videoFramesSent: Long = 0
    var droppedAudioFrames: Long = 0
        protected set
    var droppedVideoFrames: Long = 0
        protected set
    private val bitrateManager: BitrateManager = BitrateManager(connectChecker)
    protected var isEnableLogs = true
    private var job: Job? = null
    protected val scope = CoroutineScope(Dispatchers.IO)
    @Volatile
    protected var bytesSend = 0L

    abstract fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?)
    abstract fun setAudioInfo(sampleRate: Int, isStereo: Boolean)
    protected abstract suspend fun onRun()
    protected abstract suspend fun stopImp(clear: Boolean = true)

    fun sendMediaFrame(mediaFrame: MediaFrame) {
        if (running && !queue.trySend(mediaFrame)) {
            when (mediaFrame.type) {
                MediaFrame.Type.VIDEO -> {
                    Log.i(TAG, "Video frame discarded")
                    droppedVideoFrames++
                }
                MediaFrame.Type.AUDIO -> {
                    Log.i(TAG, "Audio frame discarded")
                    droppedAudioFrames++
                }
            }
        }
    }

    fun start() {
        bitrateManager.reset()
        queue.clear()
        running = true
        job = scope.launch {
            val bitrateTask = async {
                while (scope.isActive && running) {
                    //bytes to bits
                    bitrateManager.calculateBitrate(bytesSend * 8)
                    bytesSend = 0
                    delay(timeMillis = 1000)
                }
            }
            onRun()
        }
    }

    suspend fun stop(clear: Boolean = true) {
        running = false
        stopImp(clear)
        resetSentAudioFrames()
        resetSentVideoFrames()
        resetDroppedAudioFrames()
        resetDroppedVideoFrames()
        job?.cancelAndJoin()
        job = null
        queue.clear()
    }

    @Throws(IllegalArgumentException::class)
    fun hasCongestion(percentUsed: Float = 20f): Boolean {
        if (percentUsed < 0 || percentUsed > 100) throw IllegalArgumentException("the value must be in range 0 to 100")
        val size = queue.getSize().toFloat()
        val remaining = queue.remainingCapacity().toFloat()
        val capacity = size + remaining
        return size >= capacity * (percentUsed / 100f)
    }

    fun resizeCache(newSize: Int) {
        if (newSize < queue.getSize() - queue.remainingCapacity()) {
            throw RuntimeException("Can't fit current cache inside new cache size")
        }
        val tempQueue = StreamBlockingQueue(newSize)
        queue.drainTo(tempQueue)
        queue = tempQueue
    }

    fun getCacheSize(): Int = cacheSize

    fun getItemsInCache(): Int = queue.getSize()

    fun clearCache() {
        queue.clear()
    }

    fun getSentAudioFrames(): Long = audioFramesSent

    fun getSentVideoFrames(): Long = videoFramesSent

    fun resetSentAudioFrames() {
        audioFramesSent = 0
    }

    fun resetSentVideoFrames() {
        videoFramesSent = 0
    }

    fun resetDroppedAudioFrames() {
        droppedAudioFrames = 0
    }

    fun resetDroppedVideoFrames() {
        droppedVideoFrames = 0
    }

    fun setLogs(enable: Boolean) {
        isEnableLogs = enable
    }

    fun setBitrateExponentialFactor(factor: Float) {
        bitrateManager.exponentialFactor = factor
    }

    fun getBitrateExponentialFactor() = bitrateManager.exponentialFactor

    fun setDelay(delay: Long) {
        queue.setCacheTime(delay)
    }
}