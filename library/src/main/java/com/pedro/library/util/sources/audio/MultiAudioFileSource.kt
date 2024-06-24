/*
 * Copyright (C) 2023 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.library.util.sources.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.decoder.AudioDecoder
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.DecoderInterface


/**
 * Created by pedro on 19/6/24.
 */
class MultiAudioFileSource(
  private val context: Context,
  private val path: List<Uri>,
  private var loopMode: Boolean = true
): AudioSource(), GetMicrophoneData {

  private var running = false
  private var currentDecoder = 0
  private val decoders = mutableListOf<AudioDecoder>()
  private var muted = false
  private var audioTrackPlayer: AudioTrack? = null

  private val audioDecoderInterface = AudioDecoderInterface {
    decoders[currentDecoder].stop()
    if (!loopMode && currentDecoder == decoders.size - 1) {
      return@AudioDecoderInterface
    }
    currentDecoder = if (currentDecoder == decoders.size - 1) 0 else currentDecoder + 1
    decoders[currentDecoder].initExtractor(context, path[currentDecoder], null)
    decoders[currentDecoder].prepareAudio()
    decoders[currentDecoder].start()
  }
  private val decoderInterface: DecoderInterface = object: DecoderInterface {
    override fun onLoop() {

    }
  }

  override fun create(
    sampleRate: Int, isStereo: Boolean,
    echoCanceler: Boolean, noiseSuppressor: Boolean
  ): Boolean {
    if (path.isEmpty()) throw IllegalArgumentException("empty list of files is not allowed")
    path.forEach {
      val decoder = AudioDecoder(this, audioDecoderInterface, decoderInterface)
      val result = decoder.initExtractor(context, it, null)
      if (!result) {
        throw IllegalArgumentException("Audio file track not found")
      }
      decoders.add(decoder)
    }
    val mySampleRate = decoders[0].sampleRate
    val myIsStereo = decoders[0].isStereo
    decoders.forEach {
      if (mySampleRate != it.sampleRate || myIsStereo != it.isStereo) {
        throw IllegalArgumentException("All audio files must contain the same sampleRate and channels")
      }
    }
    return true
  }

  override fun start(getMicrophoneData: GetMicrophoneData) {
    this.getMicrophoneData = getMicrophoneData
    decoders[currentDecoder].initExtractor(context, path[currentDecoder], null)
    decoders[currentDecoder].prepareAudio()
    decoders[currentDecoder].start()
    running = true
  }

  override fun stop() {
    decoders[currentDecoder].stop()
    running = false
    currentDecoder = 0
  }

  override fun isRunning(): Boolean = running

  override fun release() {
    if (running) stop()
  }

  override fun getMaxInputSize(): Int = decoders[0].size
  override fun setMaxInputSize(size: Int) { }
  override fun inputPCMData(frame: Frame) {
    audioTrackPlayer?.write(frame.buffer, frame.offset, frame.size)
    getMicrophoneData?.inputPCMData(frame)
  }

  fun moveTo(time: Double, fileIndex: Int = currentDecoder) {
    decoders[fileIndex].moveTo(time)
  }

  fun getCurrentUsedFile() = currentDecoder

  fun getDuration(fileIndex: Int = currentDecoder) = decoders[fileIndex].duration

  fun getTime(fileIndex: Int = currentDecoder) = decoders[fileIndex].time

  fun setLoopMode(enabled: Boolean) {
    this.loopMode = enabled
  }

  fun isMuted(): Boolean = muted

  fun mute() {
    decoders.forEach { it.mute() }
    muted = true
  }

  fun unMute() {
    decoders.forEach { it.unMute() }
    muted = false
  }

  fun playAudioDevice() {
    if (isAudioDeviceEnabled()) {
      audioTrackPlayer?.stop()
      audioTrackPlayer = null
    }
    val channel = if (isStereo) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
    val buffSize = AudioTrack.getMinBufferSize(sampleRate, channel, AudioFormat.ENCODING_PCM_16BIT)
    audioTrackPlayer = AudioTrack(
        AudioManager.STREAM_MUSIC, sampleRate, channel,
        AudioFormat.ENCODING_PCM_16BIT, buffSize, AudioTrack.MODE_STREAM
    )
    audioTrackPlayer?.play()
  }

  fun stopAudioDevice() {
    if (isAudioDeviceEnabled()) {
      audioTrackPlayer?.stop()
      audioTrackPlayer = null
    }
  }

  fun isAudioDeviceEnabled(): Boolean {
    return (audioTrackPlayer != null && audioTrackPlayer?.playState == AudioTrack.PLAYSTATE_PLAYING)
  }
}