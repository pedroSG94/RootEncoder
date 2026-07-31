package com.pedro.common

import com.pedro.common.frame.MediaFrame
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class StreamBlockingQueue(var capacity: Int) {

    private val queue = PriorityBlockingQueue<MediaFrame>(capacity) { p0, p1 ->
        p0.info.timestamp.compare(p1.info.timestamp)
    }
    private var cacheQueue = PriorityBlockingQueue<MediaFrame>(200) { p0, p1 ->
        p0.info.timestamp.compare(p1.info.timestamp)
    }
    private var cacheTimeFilled = AtomicBoolean(false)
    private var cacheTime = 0L
    private var startTs = 0L

    fun trySend(item: MediaFrame): Boolean {
        if (queue.size >= capacity) return false
        if (cacheTime > 0 && !cacheTimeFilled.get()) {
            if (startTs == 0L) startTs = TimeUtils.getCurrentTimeMillis()
            val t = TimeUtils.getCurrentTimeMillis() - startTs
            if (t >= cacheTime) cacheTimeFilled.set(true)
        }
        return try {
            if (cacheTime > 0) {
                cacheQueue.add(item)
                if (cacheTimeFilled.get()) queue.add(cacheQueue.take())
            } else queue.add(item)
            return true
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun take(): MediaFrame {
        return queue.take()
    }

    fun remainingCapacity(): Int = max(0, capacity - queue.size)

    fun drainTo(destiny: StreamBlockingQueue) {
        queue.drainTo(destiny.queue)
        cacheQueue.drainTo(destiny.cacheQueue)
    }

    /**
     * @param onRemove called for every discarded frame, used to recycle pooled buffers.
     */
    fun clear(onRemove: ((MediaFrame) -> Unit)? = null) {
        if (onRemove != null) {
            val removed = mutableListOf<MediaFrame>()
            queue.drainTo(removed)
            cacheQueue.drainTo(removed)
            removed.forEach(onRemove)
        } else {
            queue.clear()
            cacheQueue.clear()
        }
        startTs = 0L
        cacheTimeFilled.set(false)
    }

    fun setCacheTime(cache: Long) {
        cacheTime = cache
        if (cacheTime == 0L) return
        cacheQueue = PriorityBlockingQueue<MediaFrame>(maxOf(1, (cache / 5).toInt())) { p0, p1 ->
            p0.info.timestamp.compare(p1.info.timestamp)
        }
    }

    fun getSize() = queue.size

    /**
     * Sum of [MediaFrame.info.size] for all frames in the main send queue.
     * Delay/cache queue bytes are excluded.
     */
    fun getTotalSize(): Long = queue.sumOf { it.info.size.toLong() }
}