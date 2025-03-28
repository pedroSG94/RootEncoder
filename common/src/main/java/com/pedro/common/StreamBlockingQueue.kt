package com.pedro.common

import com.pedro.common.frame.MediaFrame
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class StreamBlockingQueue(size: Int) {

    private val queue = PriorityBlockingQueue<MediaFrame>(size) { p0, p1 ->
        p0.info.timestamp.compare(p1.info.timestamp)
    }
    private var cacheQueue = PriorityBlockingQueue<MediaFrame>(200) { p0, p1 ->
        p0.info.timestamp.compare(p1.info.timestamp)
    }
    private var cacheTimeFilled = AtomicBoolean(false)
    private var cacheTime = 0L
    private var startTs = 0L

    fun trySend(item: MediaFrame): Boolean {
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
        } catch (e: IllegalStateException) {
            false
        }
    }

    fun take(): MediaFrame {
        return queue.take()
    }

    fun remainingCapacity(): Int = queue.remainingCapacity()

    fun drainTo(destiny: StreamBlockingQueue) {
        queue.drainTo(destiny.queue)
        cacheQueue.drainTo(destiny.cacheQueue)
    }

    fun clear() {
        queue.clear()
        cacheQueue.clear()
        startTs = 0L
        cacheTimeFilled.set(false)
    }

    fun setCacheTime(cache: Long) {
        cacheTime = cache
        cacheQueue = PriorityBlockingQueue<MediaFrame>((cache / 5).toInt()) { p0, p1 ->
            p0.info.timestamp.compare(p1.info.timestamp)
        }
    }

    fun getSize() = queue.size
}