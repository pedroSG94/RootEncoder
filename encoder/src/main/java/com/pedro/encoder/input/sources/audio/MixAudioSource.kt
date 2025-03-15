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

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioPlaybackCaptureConfiguration
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresApi
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.CustomAudioEffect
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.audio.MicrophoneManager
import com.pedro.encoder.input.sources.MediaProjectionHandler

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
    mediaProjectionCallback: MediaProjection.Callback? = null,
    private val microphoneAudioSource: Int = MediaRecorder.AudioSource.DEFAULT
): AudioSource(), GetMicrophoneData {

    private val TAG = "MixAudioSource"
    private var handlerThread = HandlerThread(TAG)
    private val microphone = MicrophoneManager(this)
    private var preferredDevice: AudioDeviceInfo? = null
    private val mediaProjectionCallback = mediaProjectionCallback ?: object : MediaProjection.Callback() {}

    init {
        MediaProjectionHandler.mediaProjection = mediaProjection
    }

    override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
        //create microphone to confirm valid parameters
        val result = microphone.createMicrophone(microphoneAudioSource, sampleRate, isStereo, echoCanceler, noiseSuppressor)
        if (!result) {
            throw IllegalArgumentException("Some parameters specified are not valid");
        }
        return true
    }

    fun setPreferredDevice(deviceInfo: AudioDeviceInfo?): Boolean {
        preferredDevice = deviceInfo
        return microphone.setPreferredDevice(deviceInfo)
    }

    override fun start(getMicrophoneData: GetMicrophoneData) {
        this.getMicrophoneData = getMicrophoneData
        if (!isRunning()) {
            handlerThread = HandlerThread(TAG)
            handlerThread.start()
            MediaProjectionHandler.mediaProjection?.registerCallback(mediaProjectionCallback, Handler(handlerThread.looper))
            val config = AudioPlaybackCaptureConfiguration.Builder(MediaProjectionHandler.mediaProjection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
            val result = microphone.createMixMicrophone(microphoneAudioSource, config, sampleRate, isStereo, echoCanceler, noiseSuppressor)
            if (!result) {
                throw IllegalArgumentException("Failed to create microphone audio source")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                microphone.setPreferredDevice(preferredDevice)
            }
            microphone.start()
        }
    }

    override fun stop() {
        if (isRunning()) {
            getMicrophoneData = null
            microphone.stop()
            handlerThread.quitSafely()
        }
    }

    override fun isRunning(): Boolean = microphone.isRunning

    override fun release() {}

    override fun inputPCMData(frame: Frame) {
        getMicrophoneData?.inputPCMData(frame)
    }

    fun mute() {
        microphone.mute()
    }

    fun unMute() {
        microphone.unMute()
    }

    fun isMuted(): Boolean = microphone.isMuted

    fun setAudioEffect(effect: CustomAudioEffect) {
        microphone.setCustomAudioEffect(effect)
    }

    var mixVolume: Float
        set(value) { microphone.setVolume(value) }
        get() = (microphone.microphoneVolume + microphone.internalVolume) / 2f

    var microphoneVolume: Float
        set(value) { microphone.microphoneVolume = value }
        get() = microphone.microphoneVolume

    var internalVolume: Float
        set(value) { microphone.internalVolume = value }
        get() = microphone.internalVolume
}