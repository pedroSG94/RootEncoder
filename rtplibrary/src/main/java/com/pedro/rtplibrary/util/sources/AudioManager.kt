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
class AudioManager(getMicrophoneData: GetMicrophoneData) {

  enum class Source {
    MICROPHONE, INTERNAL
  }

  private var source = Source.MICROPHONE
  private val microphone = MicrophoneManager(getMicrophoneData)
  private var mediaProjection: MediaProjection? = null
  private var sampleRate = 0
  private var isStereo = true
  private var echoCanceler = false
  private var noiseSuppressor = false

  fun createAudioManager(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean,
    noiseSuppressor: Boolean): Boolean {
    this.sampleRate = sampleRate
    this.isStereo = isStereo
    this.echoCanceler = echoCanceler
    this.noiseSuppressor = noiseSuppressor
    //create microphone to confirm valid parameters
    return microphone.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor)
  }

  fun start() {
    if (!isRunning()) {
      when (source) {
        Source.MICROPHONE -> {
          val result = microphone.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor)
          if (!result) {
            throw IllegalArgumentException("Failed to create microphone audio source")
          }
        }
        Source.INTERNAL -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaProjection?.let {
              val config = AudioPlaybackCaptureConfiguration.Builder(it)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
              val result = microphone.createInternalMicrophone(config, sampleRate, isStereo,
                echoCanceler, noiseSuppressor)
              if (!result) {
                throw IllegalArgumentException("Failed to create internal audio source")
              }
            }
          } else {
            throw IllegalStateException("Using internal audio in a invalid Android version. Android 10+ is necessary")
          }
        }
      }
      microphone.start()
    }
  }

  fun stop() {
    if (isRunning()) {
      microphone.stop()
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  fun changeSourceInternal(mediaProjection: MediaProjection) {
    if (this.source != source) {
      this.mediaProjection = mediaProjection
      val wasRunning = isRunning()
      stop()
      this.source = Source.INTERNAL
      if (wasRunning) start()
    }
  }

  fun changeSourceMicrophone() {
    if (this.source != source) {
      val wasRunning = isRunning()
      stop()
      this.source = Source.MICROPHONE
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        mediaProjection?.stop()
        mediaProjection = null
      }
      if (wasRunning) start()
    }
  }

  fun isRunning(): Boolean = microphone.isRunning

  fun getMaxInputSize(): Int = microphone.maxInputSize
}