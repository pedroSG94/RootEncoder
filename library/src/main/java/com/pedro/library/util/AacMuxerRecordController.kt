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
import com.pedro.common.frame.MediaFrame
import com.pedro.common.toMediaCodecBufferInfo
import com.pedro.library.base.recording.AsyncBaseRecordController
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
class AacMuxerRecordController : AsyncBaseRecordController() {
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
    require(tracks == RecordTracks.AUDIO) { "This record controller only support record audio" }
    outputStream = FileOutputStream(path)
    start()
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Throws(IOException::class)
  override fun startRecordImp(
    fd: FileDescriptor,
    listener: RecordController.Listener?,
    tracks: RecordTracks
  ) {
    require(tracks == RecordTracks.AUDIO) { "This record controller only support record audio" }
    outputStream = FileOutputStream(fd)
    start()
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  @Throws(IOException::class)
  private fun start() {
    if (getAudioCodec() != AudioCodec.AAC) throw IOException("Unsupported AudioCodec: " + getAudioCodec().name)
    if (sampleRate != -1 && channels != -1) init()
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  override fun stopRecordImp() {
    sampleRate = -1
    channels = -1
    try {
      outputStream?.close()
    } catch (_: Exception) {
    } finally {
      outputStream = null
    }
  }

  override suspend fun onWriteFrame(frame: MediaFrame) {
    when (frame.type) {
      MediaFrame.Type.VIDEO -> {}
      MediaFrame.Type.AUDIO -> {
        if (recordStatus == RecordController.Status.RECORDING) {
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
    if (recordStatus == RecordController.Status.STARTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      init()
    }
  }

  override fun resetFormats() {
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  private fun init() {
    recordStatus = RecordController.Status.RECORDING
    listener?.onStatusChange(recordStatus)
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