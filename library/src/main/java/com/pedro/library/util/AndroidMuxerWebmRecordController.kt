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

import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.common.AudioCodec
import com.pedro.common.BitrateManager
import com.pedro.common.frame.MediaFrame
import com.pedro.common.toMediaCodecBufferInfo
import com.pedro.library.base.recording.BaseRecordController
import com.pedro.library.base.recording.RecordController
import com.pedro.library.base.recording.RecordController.RecordTracks
import java.io.FileDescriptor
import java.io.IOException

/**
 * Created by pedro on 08/03/19.
 * Class to control audio recording with MediaMuxer.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class AndroidMuxerWebmRecordController : BaseRecordController() {
  private var mediaMuxer: MediaMuxer? = null
  private var audioFormat: MediaFormat? = null
  private val outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM

  @Throws(IOException::class)
  override fun startRecordImp(
    path: String,
    listener: RecordController.Listener?,
    tracks: RecordTracks
  ) {
    this.tracks = RecordTracks.AUDIO
    require(tracks == RecordTracks.AUDIO) { "This record controller only support record audio" }
    if (audioCodec != AudioCodec.OPUS) {
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
    if (audioFormat != null) init()
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Throws(IOException::class)
  override fun startRecordImp(
    fd: FileDescriptor,
    listener: RecordController.Listener?,
    tracks: RecordTracks
  ) {
    this.tracks = RecordTracks.AUDIO
    require(tracks == RecordTracks.AUDIO) { "This record controller only support record audio" }
    if (audioCodec != AudioCodec.OPUS) {
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
    if (audioFormat != null) init()
  }

  override fun stopRecordImp() {
    videoTrack = -1
    audioTrack = -1
    status = RecordController.Status.STOPPED
    try {
      mediaMuxer?.stop()
      mediaMuxer?.release()
    } catch (_: Exception) { }
    mediaMuxer = null
    pauseMoment = 0
    pauseTime = 0
    startTs = 0
    myRequestKeyFrame = null
    listener?.onStatusChange(status)
  }

  override fun setVideoFormat(videoFormat: MediaFormat) {
  }

  override fun setAudioFormat(audioFormat: MediaFormat) {
    this.audioFormat = audioFormat
    if (status == RecordController.Status.STARTED) {
      init()
    }
  }

  override fun resetFormats() {
    audioFormat = null
  }

  private fun init() {
    audioTrack = mediaMuxer?.addTrack(audioFormat!!) ?: -1
    mediaMuxer?.start()
    status = RecordController.Status.RECORDING
    listener?.onStatusChange(status)
  }

  private suspend fun write(track: Int, frame: MediaFrame) {
    if (track == -1) return
    try {
      mediaMuxer?.writeSampleData(track, frame.data, frame.info.toMediaCodecBufferInfo())
      bitrateManager?.calculateBitrate(frame.info.size * 8L)
    } catch (e: Exception) {
      listener?.onError(e)
    }
  }

  override suspend fun onWriteFrame(frame: MediaFrame) {
    when (frame.type) {
      MediaFrame.Type.VIDEO -> {}
      MediaFrame.Type.AUDIO -> {
        if (status == RecordController.Status.RECORDING) {
          write(audioTrack, frame)
        }
      }
    }
  }
}