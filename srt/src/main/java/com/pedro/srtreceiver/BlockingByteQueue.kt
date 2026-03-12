package com.pedro.srtreceiver

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class BlockingByteQueue(private val maxSize: Int = 100) {
    
    private val queue = LinkedBlockingQueue<ByteArray>(maxSize)
    
    fun offer(data: ByteArray): Boolean {
        return queue.offer(data)
    }
    
    fun poll(timeout: Long, unit: TimeUnit): ByteArray? {
        return queue.poll(timeout, unit)
    }
    
    fun poll(): ByteArray? {
        return queue.poll()
    }
    
    fun take(): ByteArray {
        return queue.take()
    }
    
    fun clear() {
        queue.clear()
    }
    
    fun size(): Int {
        return queue.size
    }
    
    fun isEmpty(): Boolean {
        return queue.isEmpty()
    }
}
