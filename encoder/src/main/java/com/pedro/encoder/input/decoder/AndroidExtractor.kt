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

package com.pedro.encoder.input.decoder

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.pedro.common.frame.MediaFrame
import com.pedro.common.getIntegerSafe
import com.pedro.common.getLongSafe
import com.pedro.common.validMessage
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Created by pedro on 18/10/24.
 */
class AndroidExtractor: Extractor {

  private var mediaExtractor = MediaExtractor()
  private var sleepTime: Long = 0
  private var accumulativeTs: Long = 0
  @Volatile
  private var lastExtractorTs: Long = 0
  private var format: MediaFormat? = null

  override fun selectTrack(type: MediaFrame.Type): String {
    return when (type) {
      MediaFrame.Type.VIDEO -> selectTrack("video/")
      MediaFrame.Type.AUDIO -> selectTrack("audio/")
    }
  }

  override fun initialize(path: String) {
    try {
      reset()
      mediaExtractor = MediaExtractor()
      mediaExtractor.setDataSource(path)
    } catch (e: Exception) {
      throw IOException(e.validMessage())
    }
  }

  override fun initialize(context: Context, uri: Uri) {
    try {
      reset()
      mediaExtractor = MediaExtractor()
      mediaExtractor.setDataSource(context, uri, null)
    } catch (e: Exception) {
      throw IOException(e.validMessage())
    }
  }

  override fun initialize(fileDescriptor: FileDescriptor) {
    try {
      reset()
      mediaExtractor = MediaExtractor()
      mediaExtractor.setDataSource(fileDescriptor)
    } catch (e: Exception) {
      throw IOException(e.validMessage())
    }
  }

  override fun readFrame(buffer: ByteBuffer) = mediaExtractor.readSampleData(buffer, 0)

  override fun advance() = mediaExtractor.advance()

  override fun getTimeStamp() = mediaExtractor.sampleTime

  override fun getSleepTime(ts: Long): Long {
    val extractorTs = max(0, getTimeStamp())
    if (extractorTs == 0L) lastExtractorTs = 0
    accumulativeTs += extractorTs - lastExtractorTs
    lastExtractorTs = extractorTs
    sleepTime = if (accumulativeTs > ts) (accumulativeTs - ts) / 1000 else 0
    return sleepTime
  }

  override fun seekTo(time: Long) {
    mediaExtractor.seekTo(time, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    lastExtractorTs = getTimeStamp()
  }

  override fun release() {
    mediaExtractor.release()
  }

  override fun getVideoInfo(): VideoInfo {
    val format = this.format ?: throw IOException("Extractor track not selected")
    val width = format.getIntegerSafe(MediaFormat.KEY_WIDTH) ?: throw IOException("Width info is required")
    val height = format.getIntegerSafe(MediaFormat.KEY_HEIGHT) ?: throw IOException("Height info is required")
    val duration = format.getLongSafe(MediaFormat.KEY_DURATION) ?: 0
    val fps = format.getIntegerSafe(MediaFormat.KEY_FRAME_RATE) ?: 30
    return VideoInfo(width, height, fps, duration)
  }

  override fun getAudioInfo(): AudioInfo {
    val format = this.format ?: throw IOException("Extractor track not selected")
    val sampleRate = format.getIntegerSafe(MediaFormat.KEY_SAMPLE_RATE) ?: throw IOException("Channels info is required")
    val channels = format.getIntegerSafe(MediaFormat.KEY_CHANNEL_COUNT) ?: throw IOException("SampleRate info is required")
    val duration = format.getLongSafe(MediaFormat.KEY_DURATION) ?: 0
    return AudioInfo(sampleRate, channels, duration)
  }

  override fun getFormat(): MediaFormat {
    return format ?: throw IOException("Extractor track not selected")
  }

  private fun selectTrack(type: String): String {
    for (i in 0 until mediaExtractor.trackCount) {
      val format = mediaExtractor.getTrackFormat(i)
      val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
      if (mime.startsWith(type, ignoreCase = true)) {
        mediaExtractor.selectTrack(i)
        this.format = format
        return mime
      }
    }
    throw IOException("track not found")
  }

  private fun reset() {
    sleepTime = 0
    accumulativeTs = 0
    lastExtractorTs = 0
    format = null
  }
}