/*
 *
 *  * Copyright (C) 2024 pedroSG94.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.pedro.library.util.sources.audio

import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.common.trySend
import com.pedro.encoder.Frame
import com.pedro.encoder.audio.AudioEncoder
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.audio.VolumeEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.Q)
class AudioMixSource(
    mediaProjection: MediaProjection
): AudioSource() {

    private val microphoneVolumeEffect = VolumeEffect()
    private val internalVolumeEffect = VolumeEffect()
    private val microphone = MicrophoneSource().apply { setAudioEffect(microphoneVolumeEffect) }
    private val internal = InternalAudioSource(mediaProjection).apply { setAudioEffect(internalVolumeEffect) }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val queue1: BlockingQueue<Frame> = LinkedBlockingQueue(500)
    private val queue2: BlockingQueue<Frame> = LinkedBlockingQueue(500)
    private var running = false
    //We need read with a higher buffer to get enough time to mix it
    private val inputSize = AudioEncoder.inputSize

    override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
        return microphone.init(sampleRate, isStereo, echoCanceler, noiseSuppressor) && internal.init(sampleRate, isStereo, echoCanceler, noiseSuppressor)
    }

    override fun start(getMicrophoneData: GetMicrophoneData) {
        this.getMicrophoneData = getMicrophoneData
        if (!isRunning()) {
            microphone.start(callback1)
            internal.start(callback2)
            running = true
            scope.launch {
                val min = Byte.MIN_VALUE.toInt()
                val max = Byte.MAX_VALUE.toInt()

                while (running) {
                    runCatching {
                        val frame1 = async { runInterruptible { queue1.poll(1, TimeUnit.SECONDS) } }
                        val frame2 = async { runInterruptible { queue2.poll(1, TimeUnit.SECONDS) } }
                        val r = awaitAll(frame1, frame2)
                        async {
                            val b1 = r[0].buffer
                            val b2 = r[1].buffer
                            val buffer = ByteArray(inputSize)
                            for (i in buffer.indices) {
                                buffer[i] = (b1[i] + b2[i]).coerceIn(min, max).toByte()
                            }
                            getMicrophoneData.inputPCMData(Frame(buffer, 0, buffer.size, r[0].timeStamp))
                        }
                    }.exceptionOrNull()
                }
            }
        }
    }

    override fun stop() {
        if (isRunning()) {
            getMicrophoneData = null
            microphone.stop()
            internal.stop()
            running = false
        }
    }

    override fun release() {
        microphone.release()
        internal.release()
    }

    override fun isRunning(): Boolean = running

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

    var microphoneVolume: Float
        set(value) { microphoneVolumeEffect.volume = value }
        get() = microphoneVolumeEffect.volume

    var internalVolume: Float
        set(value) { internalVolumeEffect.volume = value }
        get() = internalVolumeEffect.volume
}