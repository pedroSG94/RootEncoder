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
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.common.AudioCodec
import com.pedro.common.AudioUtils.createAdtsHeader
import com.pedro.common.BitrateManager
import com.pedro.common.frame.MediaFrame
import com.pedro.common.toMediaCodecBufferInfo
import com.pedro.library.base.recording.BaseRecordController
import com.pedro.library.base.recording.RecordController
import com.pedro.library.base.recording.RecordController.RecordTracks
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Muxer to record AAC files (used in only audio by default).
 */
class AacMuxerRecordController : BaseRecordController() {
  private var outputStream: OutputStream? = null
  private var sampleRate = -1
  private var channels = -1

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  @Throws(IOException::class)
  override fun startRecordImp(
    path: String,
    listener: RecordController.Listener?,
    tracks: RecordTracks
  ) {
    this.tracks = RecordTracks.AUDIO
    require(tracks == RecordTracks.AUDIO) { "This record controller only support record audio" }
    outputStream = FileOutputStream(path)
    start(listener)
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
    outputStream = FileOutputStream(fd)
    start(listener)
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  @Throws(IOException::class)
  private fun start(listener: RecordController.Listener?) {
    if (audioCodec != AudioCodec.AAC) throw IOException("Unsupported AudioCodec: " + audioCodec.name)
    this.listener = listener
    status = RecordController.Status.STARTED
    if (listener != null) {
      bitrateManager = BitrateManager(listener)
      listener.onStatusChange(status)
    } else {
      bitrateManager = null
    }
    if (sampleRate != -1 && channels != -1) init()
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  override fun stopRecordImp() {
    status = RecordController.Status.STOPPED
    pauseMoment = 0
    pauseTime = 0
    sampleRate = -1
    channels = -1
    startTs = 0
    try {
      outputStream?.close()
    } catch (_: Exception) {
    } finally {
      outputStream = null
    }
    myRequestKeyFrame = null
    listener?.onStatusChange(status)
  }

  override suspend fun onWriteFrame(frame: MediaFrame) {
    when (frame.type) {
      MediaFrame.Type.VIDEO -> {}
      MediaFrame.Type.AUDIO -> {
        if (status == RecordController.Status.RECORDING) {
          //we need duplicate buffer to avoid problems with the buffer
          write(frame.data, frame.info.toMediaCodecBufferInfo())
        }
      }
    }
  }

  override fun setVideoFormat(videoFormat: MediaFormat) {
  }

  override fun setAudioFormat(audioFormat: MediaFormat) {
    sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    if (status == RecordController.Status.STARTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      init()
    }
  }

  override fun resetFormats() {
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  private fun init() {
    status = RecordController.Status.RECORDING
    listener?.onStatusChange(status)
  }

  private suspend fun write(byteBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    try {
      if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
        val header = createAdtsHeader(2, info.size - info.offset, sampleRate, channels).array()
        outputStream?.write(header)
        val data = ByteArray(byteBuffer.remaining())
        byteBuffer.get(data)
        outputStream?.write(data)
        bitrateManager?.calculateBitrate(info.size * 8L)
      }
    } catch (e: Exception) {
      listener?.onError(e)
    }
  }
}