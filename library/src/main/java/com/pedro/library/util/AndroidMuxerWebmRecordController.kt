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
import com.pedro.common.frame.MediaFrame
import com.pedro.common.toMediaCodecBufferInfo
import com.pedro.library.base.recording.AsyncBaseRecordController
import com.pedro.library.base.recording.RecordController
import com.pedro.library.base.recording.RecordController.RecordTracks
import java.io.FileDescriptor
import java.io.IOException

/**
 * Created by pedro on 08/03/19.
 * Class to control audio recording with MediaMuxer.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class AndroidMuxerWebmRecordController : AsyncBaseRecordController() {
  private var mediaMuxer: MediaMuxer? = null
  private var audioFormat: MediaFormat? = null
  private val outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
  private var videoTrack: Int = -1
  private var audioTrack: Int = -1

  @Throws(IOException::class)
  override fun startRecordImp(
    path: String,
    listener: RecordController.Listener?,
    tracks: RecordTracks
  ) {
    require(tracks == RecordTracks.AUDIO) { "This record controller only support record audio" }
    if (getAudioCodec() != AudioCodec.OPUS) {
      throw IOException("Unsupported AudioCodec: " + getAudioCodec().name)
    }
    mediaMuxer = MediaMuxer(path, outputFormat)
    if (audioFormat != null) init()
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Throws(IOException::class)
  override fun startRecordImp(
    fd: FileDescriptor,
    listener: RecordController.Listener?,
    tracks: RecordTracks
  ) {
    require(tracks == RecordTracks.AUDIO) { "This record controller only support record audio" }
    if (getAudioCodec() != AudioCodec.OPUS) {
      throw IOException("Unsupported AudioCodec: " + getAudioCodec().name)
    }
    mediaMuxer = MediaMuxer(fd, outputFormat)
    if (audioFormat != null) init()
  }

  override fun stopRecordImp() {
    videoTrack = -1
    audioTrack = -1
    try {
      mediaMuxer?.stop()
      mediaMuxer?.release()
    } catch (_: Exception) { }
    mediaMuxer = null
  }

  override fun setVideoFormat(videoFormat: MediaFormat) {
  }

  override fun setAudioFormat(audioFormat: MediaFormat) {
    this.audioFormat = audioFormat
    if (recordStatus == RecordController.Status.STARTED) {
      init()
    }
  }

  override fun resetFormats() {
    audioFormat = null
  }

  private fun init() {
    audioTrack = mediaMuxer?.addTrack(audioFormat!!) ?: -1
    mediaMuxer?.start()
    recordStatus = RecordController.Status.RECORDING
    listener?.onStatusChange(recordStatus)
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
        if (recordStatus == RecordController.Status.RECORDING) {
          write(audioTrack, frame)
        }
      }
    }
  }
}