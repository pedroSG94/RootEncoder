package com.pedro.rtplibrary.util.sources

import android.media.AudioAttributes
import android.media.AudioPlaybackCaptureConfiguration
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.audio.MicrophoneManager

/**
 * Created by pedro on 29/3/22.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class AudioManager(getMicrophoneData: GetMicrophoneData, private var source: Source) {

  enum class Source {
    MICROPHONE, DISABLED,
    @RequiresApi(Build.VERSION_CODES.Q)
    INTERNAL,
  }

  private val microphone = MicrophoneManager(getMicrophoneData)
  private val noSource = NoSource()
  private var mediaProjection: MediaProjection? = null
  private var sampleRate = 0
  private var isStereo = true
  private var echoCanceler = false
  private var noiseSuppressor = false
  private var maxInputSize = 4096

  fun createAudioManager(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean,
    noiseSuppressor: Boolean): Boolean {
    this.sampleRate = sampleRate
    this.isStereo = isStereo
    this.echoCanceler = echoCanceler
    this.noiseSuppressor = noiseSuppressor
    //create microphone to confirm valid parameters
    val result = microphone.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor)
    maxInputSize = microphone.maxInputSize //get default input size to keep value constant in all sources
    return result
  }

  fun start() {
    if (!isRunning()) {
      when (source) {
        Source.MICROPHONE -> {
          microphone.maxInputSize = maxInputSize
          val result = microphone.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor)
          if (!result) {
            throw IllegalArgumentException("Failed to create microphone audio source")
          }
          microphone.start()
        }
        Source.INTERNAL -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaProjection?.let {
              val config = AudioPlaybackCaptureConfiguration.Builder(it)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
              microphone.maxInputSize = maxInputSize
              val result = microphone.createInternalMicrophone(config, sampleRate, isStereo,
                echoCanceler, noiseSuppressor)
              if (!result) {
                throw IllegalArgumentException("Failed to create internal audio source")
              }
            }
          } else {
            throw IllegalStateException("Using internal audio in a invalid Android version. Android 10+ is necessary")
          }
          microphone.start()
        }
        Source.DISABLED -> noSource.start()
      }
    }
  }

  fun stop() {
    if (isRunning()) {
      when (source) {
        Source.MICROPHONE, Source.INTERNAL -> microphone.stop()
        Source.DISABLED -> noSource.stop()
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  fun changeSourceInternal(mediaProjection: MediaProjection) {
    if (this.source != Source.INTERNAL || this.mediaProjection == null) {
      this.mediaProjection = mediaProjection
      val wasRunning = isRunning()
      stop()
      this.source = Source.INTERNAL
      if (wasRunning) start()
    }
  }

  fun changeSourceMicrophone() {
    if (this.source != Source.MICROPHONE) {
      val wasRunning = isRunning()
      stop()
      this.source = Source.MICROPHONE
      mediaProjection?.stop()
      mediaProjection = null
      if (wasRunning) start()
    }
  }

  fun changeAudioSourceDisabled() {
    if (this.source != Source.DISABLED) {
      val wasRunning = isRunning()
      stop()
      this.source = Source.DISABLED
      mediaProjection?.stop()
      mediaProjection = null
      if (wasRunning) start()
    }
  }

  fun mute() {
    if (source == Source.DISABLED) return
    microphone.mute()
  }

  fun unMute() {
    if (source == Source.DISABLED) return
    microphone.unMute()
  }

  fun isMuted(): Boolean {
    return if (source == Source.DISABLED) false
    else microphone.isMuted
  }

  fun isRunning(): Boolean {
    return when (source) {
      Source.MICROPHONE, Source.INTERNAL -> microphone.isRunning
      Source.DISABLED -> noSource.isRunning()
    }
  }

  fun getMaxInputSize(): Int = microphone.maxInputSize

  fun setMaxInputSize(size: Int) {
    microphone.maxInputSize = size
  }
}