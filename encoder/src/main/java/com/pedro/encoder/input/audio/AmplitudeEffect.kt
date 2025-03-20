package com.pedro.encoder.input.audio

import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

/**
 * Calculate the amplitude in each buffer and return the result
 */
class AmplitudeEffect(private val listener: Listener): CustomAudioEffect() {

    interface Listener {
        fun onAmplitude(value: Float)
    }

    private val queue = LinkedBlockingQueue<ByteArray>(200)
    private val audioUtils = AudioUtils()
    private var executor = Executors.newSingleThreadExecutor()
    private var running = false

    override fun process(pcmBuffer: ByteArray): ByteArray {
        if (running) {
            val buffer = pcmBuffer.clone()
            queue.offer(buffer)
        }
        return pcmBuffer
    }

    fun start() {
        queue.clear()
        running = true
        executor = Executors.newSingleThreadExecutor()
        executor.execute {
            while (running) {
                val buffer = queue.take()
                val amplitude = audioUtils.calculateAmplitude(buffer)
                listener.onAmplitude(amplitude)
            }
        }
    }

    fun stop() {
        running = false
        executor.shutdownNow()
        queue.clear()
    }

    fun isRunning() = running
}