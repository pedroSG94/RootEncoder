/*
 * Copyright (C) 2024 pedroSG94.
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
package com.pedro.library.util

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.pedro.common.AudioCodec
import com.pedro.common.BitrateManager
import com.pedro.common.clone
import com.pedro.common.frame.MediaFrame
import com.pedro.common.toMediaCodecBufferInfo
import com.pedro.common.toMediaFrameInfo
import com.pedro.library.base.recording.BaseRecordController
import com.pedro.library.base.recording.RecordController
import com.pedro.library.base.recording.RecordController.RecordTracks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Created by pedro on 08/03/19.
 * Class to control video recording with MediaMuxer.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class AndroidMuxerRecordController : BaseRecordController() {
  private var mediaMuxer: MediaMuxer? = null
  private var videoFormat: MediaFormat? = null
  private var audioFormat: MediaFormat? = null
  private val outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
  private val scope = CoroutineScope(Dispatchers.IO)
  private var muxerChannel: Channel<MediaFrame>? = null
  private var muxerJob: Job? = null

  @Throws(IOException::class)
  override fun startRecord(
    path: String,
    listener: RecordController.Listener?,
    tracks: RecordTracks
  ) {
    this.tracks = tracks
    if (audioCodec != AudioCodec.AAC) {
      throw IOException("Unsupported AudioCodec: " + audioCodec.name)
    }
    mediaMuxer = MediaMuxer(path, outputFormat)
    this.listener = listener
    status = RecordController.Status.STARTED
    if (listener != null) {
      bitrateManager = BitrateManager(listener)
      listener.onStatusChange(status)
    } else {
      bitrateManager = null
    }
    startChannel()
    if (tracks == RecordTracks.AUDIO && audioFormat != null) init()
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Throws(IOException::class)
  override fun startRecord(
    fd: FileDescriptor,
    listener: RecordController.Listener?,
    tracks: RecordTracks
  ) {
    this.tracks = tracks
    if (audioCodec != AudioCodec.AAC) {
      throw IOException("Unsupported AudioCodec: " + audioCodec.name)
    }
    mediaMuxer = MediaMuxer(fd, outputFormat)
    this.listener = listener
    status = RecordController.Status.STARTED
    if (listener != null) {
      bitrateManager = BitrateManager(listener)
      listener.onStatusChange(status)
    } else {
      bitrateManager = null
    }
    startChannel()
    if (tracks == RecordTracks.AUDIO && audioFormat != null) init()
  }

  override fun stopRecord() {
    videoTrack = -1
    audioTrack = -1
    status = RecordController.Status.STOPPED
    muxerChannel?.close()
    muxerChannel = null
    runBlocking { muxerJob?.join() }
    try {
      mediaMuxer?.stop()
      mediaMuxer?.release()
    } catch (_: Exception) { }
    mediaMuxer = null
    pauseMoment = 0
    pauseTime = 0
    startTs = 0
    requestKeyFrame = null
    if (listener != null) listener.onStatusChange(status)
  }

  private fun startChannel() {
    muxerChannel = Channel(Channel.UNLIMITED)
    muxerJob = scope.launch {
        val channel = muxerChannel ?: return@launch
        for (frame in channel) {
          when (frame.type) {
            MediaFrame.Type.VIDEO -> {
              if (status == RecordController.Status.STARTED && videoFormat != null && (audioFormat != null || tracks == RecordTracks.VIDEO)) {
                if (frame.info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME || isKeyFrame(frame.data)) {
                  requestKeyFrame = null
                  videoTrack = mediaMuxer?.addTrack(videoFormat!!) ?: -1
                  init()
                } else if (requestKeyFrame != null) {
                  requestKeyFrame.onRequestKeyFrame()
                  requestKeyFrame = null
                }
              } else if (status == RecordController.Status.RESUMED && (frame.info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME
                    || isKeyFrame(frame.data))
              ) {
                status = RecordController.Status.RECORDING
                if (listener != null) listener.onStatusChange(status)
              }
              if (status == RecordController.Status.RECORDING && tracks != RecordTracks.AUDIO) {
                write(videoTrack, frame)
              }
            }
            MediaFrame.Type.AUDIO -> {
              if (status == RecordController.Status.RECORDING && tracks != RecordTracks.VIDEO) {
                write(audioTrack, frame)
              }
            }
          }
        }

    }
  }

  override fun recordVideo(videoBuffer: ByteBuffer, videoInfo: MediaCodec.BufferInfo) {

    val info = videoInfo.toMediaFrameInfo()
    val i = updateFormat(info)
    muxerChannel?.trySend(MediaFrame(videoBuffer.clone(), i, MediaFrame.Type.VIDEO))
  }

  var job: Job? = null

  fun start() {
    job = scope.launch {
      while (isActive) {

      }
    }
  }

  override fun recordAudio(audioBuffer: ByteBuffer, audioInfo: MediaCodec.BufferInfo) {
    val info = audioInfo.toMediaFrameInfo()
    val i = updateFormat(info)
    muxerChannel?.trySend(MediaFrame(audioBuffer.clone(), i, MediaFrame.Type.AUDIO))
  }

  override fun setVideoFormat(videoFormat: MediaFormat?) {
    this.videoFormat = videoFormat
  }

  override fun setAudioFormat(audioFormat: MediaFormat?) {
    this.audioFormat = audioFormat
    if (tracks == RecordTracks.AUDIO && status == RecordController.Status.STARTED) {
      init()
    }
  }

  override fun resetFormats() {
    videoFormat = null
    audioFormat = null
  }

  private fun init() {
    if (tracks != RecordTracks.VIDEO) audioTrack = mediaMuxer?.addTrack(audioFormat!!) ?: -1
    mediaMuxer?.start()
    status = RecordController.Status.RECORDING
    if (listener != null) listener.onStatusChange(status)
  }

  private suspend fun write(track: Int, frame: MediaFrame) {
    if (track == -1) return
    val trackString = if (track == audioTrack) "Audio" else "Video"
    try {
      Log.i(TAG, trackString + ", ts: " + frame.info.timestamp + ", flag: " + frame.info.flags)
      mediaMuxer?.writeSampleData(track, frame.data, frame.info.toMediaCodecBufferInfo())
      if (bitrateManager != null) bitrateManager.calculateBitrate(frame.info.size * 8L)
    } catch (e: Exception) {
      if (listener != null) listener.onError(e)
    }
  }

  private fun updateFormat(oldInfo: MediaFrame.Info): MediaFrame.Info {
    if (startTs <= 0) startTs = oldInfo.timestamp
    val ts = max(0, oldInfo.timestamp - startTs - pauseTime)
    return oldInfo.copy(timestamp = ts)
  }
}