package com.pedro.common.base

import android.util.Log
import com.pedro.common.BitrateManager
import com.pedro.common.ConnectChecker
import com.pedro.common.StreamBlockingQueue
import com.pedro.common.frame.MediaFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

abstract class BaseSender(
    protected val connectChecker: ConnectChecker,
    protected val TAG: String
) {

    @Volatile
    protected var running = false
    private var cacheSize = 400

    protected val queue = StreamBlockingQueue(cacheSize)

    protected val audioFramesSent = AtomicLong(0)
    protected val videoFramesSent = AtomicLong(0)
    private val droppedAudioFrames = AtomicLong(0)
    private val droppedVideoFrames = AtomicLong(0)

    private val bitrateManager: BitrateManager = BitrateManager(connectChecker)
    protected var isEnableLogs = true
    private var job: Job? = null
    protected val scope = CoroutineScope(Dispatchers.IO)

    protected val bytesSend = AtomicLong(0)
    protected val bytesSendPerSecond = AtomicLong(0)

    abstract fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?)
    abstract fun setAudioInfo(sampleRate: Int, isStereo: Boolean)
    protected abstract suspend fun onRun()
    protected abstract suspend fun stopImp(clear: Boolean = true)

    fun sendMediaFrame(mediaFrame: MediaFrame) {
        if (running && !queue.trySend(mediaFrame)) {
            when (mediaFrame.type) {
                MediaFrame.Type.VIDEO -> {
                    Log.i(TAG, "Video frame discarded")
                    droppedVideoFrames.incrementAndGet()
                }
                MediaFrame.Type.AUDIO -> {
                    Log.i(TAG, "Audio frame discarded")
                    droppedAudioFrames.incrementAndGet()
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
                    bitrateManager.calculateBitrate(bytesSendPerSecond.get() * 8)
                    bytesSendPerSecond.set(0)
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
        resetBytesSend()
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
        queue.capacity = newSize
    }

    fun getCacheSize(): Int = cacheSize

    fun getItemsInCache(): Int = queue.getSize()

    fun clearCache() {
        queue.clear()
    }

    fun getSentAudioFrames(): Long = audioFramesSent.get()

    fun getSentVideoFrames(): Long = videoFramesSent.get()

    fun getDroppedAudioFrames(): Long = droppedAudioFrames.get()

    fun getDroppedVideoFrames(): Long = droppedVideoFrames.get()

    fun getBytesSend(): Long = bytesSend.get()

    fun resetSentAudioFrames() {
        audioFramesSent.set(0)
    }

    fun resetSentVideoFrames() {
        videoFramesSent.set(0)
    }

    fun resetDroppedAudioFrames() {
        droppedAudioFrames.set(0)
    }

    fun resetDroppedVideoFrames() {
        droppedVideoFrames.set(0)
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

    fun resetBytesSend() {
        bytesSend.set(0)
    }
}