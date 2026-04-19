package com.pedro.common.base

import android.util.Log
import com.pedro.common.BitrateManager
import com.pedro.common.ConnectChecker
import com.pedro.common.FrameLifecycleListener
import com.pedro.common.QueueSnapshot
import com.pedro.common.StreamBlockingQueue
import com.pedro.common.TransportEvent
import com.pedro.common.frame.MediaFrame
import com.pedro.common.onMainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

abstract class BaseSender(
    protected val connectChecker: ConnectChecker,
    protected val TAG: String
) {

    @Volatile
    protected var running = false
    // Tracks actual queue capacity; updated when resizeCache() replaces the queue.
    // getCacheSize() reads this field — it must stay in sync with the live queue object.
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
    var bytesSend = 0L
        protected set
    @Volatile
    protected var bytesSendPerSecond = 0L

    /**
     * Lifecycle callback invoked after the sender's dispatch thread has fully consumed a
     * [MediaFrame]. Register a [FrameLifecycleListener] to receive notifications when the
     * frame's [MediaFrame.data] buffer may safely be returned to a pool.
     *
     * Called from the sender's IO coroutine; implementations must not block.
     */
    var frameLifecycleListener: FrameLifecycleListener? = null

    abstract fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?)
    abstract fun setAudioInfo(sampleRate: Int, isStereo: Boolean)
    protected abstract suspend fun onRun()
    protected abstract suspend fun stopImp(clear: Boolean = true)

    fun sendMediaFrame(mediaFrame: MediaFrame) {
        if (running && !queue.trySend(mediaFrame)) {
            when (mediaFrame.type) {
                MediaFrame.Type.VIDEO -> {
                    Log.i(TAG, "Video frame discarded (queue full)")
                    droppedVideoFrames++
                }
                MediaFrame.Type.AUDIO -> {
                    Log.i(TAG, "Audio frame discarded (queue full)")
                    droppedAudioFrames++
                }
            }
        }
    }

    /**
     * Called by subclass [onRun] implementations after a [MediaFrame] has been fully
     * dispatched to the network socket. Notifies [frameLifecycleListener] so callers can
     * recycle the frame's buffer.
     */
    protected fun notifyFrameConsumed(frame: MediaFrame) {
        frameLifecycleListener?.onFrameConsumed(frame)
    }

    fun start() {
        bitrateManager.reset()
        queue.clear()
        running = true
        job = scope.launch {
            val bitrateTask = async {
                while (scope.isActive && running) {
                    //bytes to bits
                    bitrateManager.calculateBitrate(bytesSendPerSecond * 8)
                    bytesSendPerSecond = 0
                    delay(timeMillis = 1000)
                }
            }
            val frameEventTask = async {
                while (scope.isActive && running) {
                    val event = TransportEvent.QueueOverflow(
                        droppedVideo = droppedVideoFrames,
                        droppedAudio = droppedAudioFrames,
                        queueCapacity = cacheSize,
                        queueSize = queue.getSize(),
                    )
                    onMainThread { connectChecker.onTransportEvent(event) }
                    delay(timeMillis = 1500)
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
        if (percentUsed !in 0.0..100.0) throw IllegalArgumentException("the value must be in range 0 to 100")
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
        cacheSize = newSize  // keep getCacheSize() in sync with the new queue capacity
    }

    /**
     * Returns the current maximum capacity of the sender frame queue.
     * Reflects the value passed to the most recent [resizeCache] call.
     */
    fun getCacheSize(): Int = cacheSize

    fun getItemsInCache(): Int = queue.getSize()

    /**
     * Returns a point-in-time snapshot of the sender queue state.
     * Thread-safe; values are read atomically from the backing [StreamBlockingQueue].
     */
    fun getQueueSnapshot(): QueueSnapshot = QueueSnapshot(
        capacity = cacheSize,
        items = queue.getSize(),
    )

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

    fun resetBytesSend() {
        bytesSend = 0
    }
}