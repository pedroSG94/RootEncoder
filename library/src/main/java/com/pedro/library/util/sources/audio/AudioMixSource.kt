package com.pedro.library.util.sources.audio

import com.pedro.common.trySend
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.utils.PCMUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.min

class AudioMixSource(
    private val source1: AudioSource,
    private val source2: AudioSource
): AudioSource() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val queue1: BlockingQueue<Frame> = LinkedBlockingQueue(500)
    private val queue2: BlockingQueue<Frame> = LinkedBlockingQueue(500)
    private var running = false

    override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
        return source1.init(sampleRate, isStereo, echoCanceler, noiseSuppressor) && source2.init(sampleRate, isStereo, echoCanceler, noiseSuppressor)
    }

    override fun start(getMicrophoneData: GetMicrophoneData) {
        this.getMicrophoneData = getMicrophoneData
        if (!isRunning()) {
            source1.start(callback1)
            source2.start(callback2)
            running = true
            scope.launch {
                while (running) {
                    runCatching {
                        val frame1 = runInterruptible { queue1.poll(1, TimeUnit.SECONDS) }
                        val frame2 = runInterruptible { queue2.poll(1, TimeUnit.SECONDS) }
                        val ts = min(frame1.timeStamp, frame2.timeStamp)
                        val buffer = PCMUtil.mixPCM(frame1.buffer, frame2.buffer)
                        getMicrophoneData.inputPCMData(Frame(buffer, 0, buffer.size, ts))
                    }.exceptionOrNull()
                }
            }
        }
    }

    override fun stop() {
        if (isRunning()) {
            getMicrophoneData = null
            source1.stop()
            source2.stop()
            running = false
        }
    }

    override fun release() {
        source1.release()
        source2.release()
    }

    override fun isRunning(): Boolean = running

    override fun getMaxInputSize(): Int = source1.getMaxInputSize() + source2.getMaxInputSize() / 2

    override fun setMaxInputSize(size: Int) {
        source1.setMaxInputSize(size)
        source2.setMaxInputSize(size)
    }

    private val callback1 = object: GetMicrophoneData {
        override fun inputPCMData(frame: Frame) {
            queue1.trySend(frame)
        }
    }

    private val callback2 = object: GetMicrophoneData {
        override fun inputPCMData(frame: Frame) {
            queue2.trySend(frame)
        }
    }
}