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

package com.pedro.encoder.input.sources.audio

import android.media.AudioDeviceInfo
import android.media.MediaRecorder
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

/**
 * Mix microphone and internal audio sources in one source to allow send both at the same time.
 * NOTES:
 * Recommended configure prepareAudio with:
 *             echoCanceler = true,
 *             noiseSuppressor = true
 * This is to avoid echo in microphone track.
 *
 * Recommended increase microphone volume to 2f,
 * because the internal audio normally is higher and you can't hear audio track properly.
 *
 * Tested in 2 devices (Android 12 and Android 14). This could change depend of the model or not, I'm not sure:
 * MediaRecorder.AudioSource.DEFAULT, MediaRecorder.AudioSource.MIC -> If other app open the microphone you receive buffers with silence from the microphone until the other app release the microphone (maybe you need close the app).
 * MediaRecorder.AudioSource.CAMCORDER -> Block the access to microphone to others apps. Others apps can't instantiate the microphone.
 * MediaRecorder.AudioSource.VOICE_COMMUNICATION -> Block the access to microphone to others apps. Others apps can instantiate the microphone but receive buffers with silence from the microphone.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class MixAudioSource(
    mediaProjection: MediaProjection,
    microphoneAudioSource: Int = MediaRecorder.AudioSource.DEFAULT
): AudioSource() {

    private val microphoneVolumeEffect = VolumeEffect()
    private val internalVolumeEffect = VolumeEffect()
    private val microphone = MicrophoneSource(audioSource = microphoneAudioSource).apply {
        setAudioEffect(microphoneVolumeEffect)
    }
    private val internal = InternalAudioSource(mediaProjection).apply {
        setAudioEffect(internalVolumeEffect)
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val microphoneQueue: BlockingQueue<Frame> = LinkedBlockingQueue(2)
    private val internalQueue: BlockingQueue<Frame> = LinkedBlockingQueue(2)
    private var running = false
    //We need read with a higher buffer to get enough time to mix it
    private val inputSize = AudioEncoder.inputSize

    override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
        return microphone.init(sampleRate, isStereo, echoCanceler, noiseSuppressor) && internal.init(sampleRate, isStereo, echoCanceler, noiseSuppressor)
    }

    override fun start(getMicrophoneData: GetMicrophoneData) {
        this.getMicrophoneData = getMicrophoneData
        if (!isRunning()) {
            microphoneQueue.clear()
            internalQueue.clear()
            microphone.start(microphoneCallback)
            internal.start(internalCallback)
            running = true
            scope.launch {
                val min = Byte.MIN_VALUE.toInt()
                val max = Byte.MAX_VALUE.toInt()

                while (running) {
                    runCatching {
                        val microphoneFrame = async { runInterruptible { microphoneQueue.poll(1, TimeUnit.SECONDS) } }
                        val internalFrame = async { runInterruptible { internalQueue.poll(1, TimeUnit.SECONDS) } }
                        val result = awaitAll(microphoneFrame, internalFrame)
                        async {
                            val microphoneBuffer = result[0]?.buffer ?: return@async
                            val internalBuffer = result[1]?.buffer ?: return@async
                            val mixBuffer = ByteArray(inputSize)
                            for (i in mixBuffer.indices) { //mix buffers with same config
                                mixBuffer[i] = (microphoneBuffer[i] + internalBuffer[i]).coerceIn(min, max).toByte()
                            }
                            getMicrophoneData.inputPCMData(Frame(mixBuffer, 0, mixBuffer.size, result[0].timeStamp))
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

    private val microphoneCallback = object: GetMicrophoneData {
        override fun inputPCMData(frame: Frame) {
            microphoneQueue.trySend(frame)
        }
    }

    private val internalCallback = object: GetMicrophoneData {
        override fun inputPCMData(frame: Frame) {
            internalQueue.trySend(frame)
        }
    }

    var microphoneVolume: Float
        set(value) { microphoneVolumeEffect.volume = value }
        get() = microphoneVolumeEffect.volume

    var internalVolume: Float
        set(value) { internalVolumeEffect.volume = value }
        get() = internalVolumeEffect.volume

    fun muteMicrophone() {
        microphone.mute()
    }

    fun unMuteMicrophone() {
        microphone.unMute()
    }

    fun isMicrophoneMuted(): Boolean = microphone.isMuted()

    fun muteInternalAudio() {
        internal.mute()
    }

    fun unMuteInternalAudio() {
        internal.unMute()
    }

    fun isInternalAudioMuted(): Boolean = internal.isMuted()

    fun setMicrophonePreferredDevice(deviceInfo: AudioDeviceInfo?) {
        microphone.setPreferredDevice(deviceInfo)
    }
}