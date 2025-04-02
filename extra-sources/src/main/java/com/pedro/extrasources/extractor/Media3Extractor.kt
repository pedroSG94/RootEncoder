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

package com.pedro.extrasources.extractor

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.media3.exoplayer.MediaExtractorCompat
import com.pedro.common.frame.MediaFrame
import com.pedro.common.getIntegerSafe
import com.pedro.common.validMessage
import com.pedro.encoder.input.decoder.AudioInfo
import com.pedro.encoder.input.decoder.Extractor
import com.pedro.encoder.input.decoder.VideoInfo
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Created by pedro on 30/10/24.
 *
 * Extractor implementation using media3 library.
 *
 * Note:
 * Using this implementation the library can't extract the duration so we are using MediaMetadataRetriever to do it.
 */
@SuppressLint("UnsafeOptInUsageError")
class Media3Extractor(private val context: Context): Extractor {

  private var mediaExtractor = MediaExtractorCompat(context)
  private var sleepTime: Long = 0
  private var accumulativeTs: Long = 0
  @Volatile
  private var lastExtractorTs: Long = 0
  private var format: MediaFormat? = null
  private var duration = -1L

  override fun selectTrack(type: MediaFrame.Type): String {
    return when (type) {
      MediaFrame.Type.VIDEO -> selectTrack("video/")
      MediaFrame.Type.AUDIO -> selectTrack("audio/")
    }
  }

  override fun initialize(path: String) {
    initialize(context, path.toUri())
  }

  override fun initialize(context: Context, uri: Uri) {
    try {
      reset()
      val metadata = MediaMetadataRetriever()
      metadata.setDataSource(context, uri)
      val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
      this.duration = duration?.toLongOrNull()?.times(1000) ?: 0
      mediaExtractor = MediaExtractorCompat(context)
      mediaExtractor.setDataSource(uri, 0)
    } catch (e: Exception) {
      throw IOException(e.validMessage())
    }
  }

  override fun initialize(fileDescriptor: FileDescriptor) {
    try {
      val file = File(fileDescriptor.toString())
      val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
      initialize(context, uri)
    } catch (e: Exception) {
      throw IOException(e.validMessage())
    }
  }

  override fun readFrame(buffer: ByteBuffer) = mediaExtractor.readSampleData(buffer, 0)

  override fun advance() = mediaExtractor.advance()

  override fun getTimeStamp() = try { mediaExtractor.sampleTime } catch (e: Exception) { 0 }

  override fun getSleepTime(ts: Long): Long {
    val extractorTs = max(0, getTimeStamp())
    if (extractorTs == 0L) lastExtractorTs = 0
    accumulativeTs += extractorTs - lastExtractorTs
    lastExtractorTs = extractorTs
    sleepTime = if (accumulativeTs > ts) (accumulativeTs - ts) / 1000 else 0
    return sleepTime
  }

  override fun seekTo(time: Long) {
    mediaExtractor.seekTo(time, MediaExtractorCompat.SEEK_TO_PREVIOUS_SYNC)
    lastExtractorTs = getTimeStamp()
  }

  override fun release() {
    mediaExtractor.release()
  }

  override fun getVideoInfo(): VideoInfo {
    val format = this.format ?: throw IOException("Extractor track not selected")
    val width = format.getIntegerSafe(MediaFormat.KEY_WIDTH) ?: throw IOException("Width info is required")
    val height = format.getIntegerSafe(MediaFormat.KEY_HEIGHT) ?: throw IOException("Height info is required")
    val fps = format.getIntegerSafe(MediaFormat.KEY_FRAME_RATE) ?: 30
    return VideoInfo(width, height, fps, duration)
  }

  override fun getAudioInfo(): AudioInfo {
    val format = this.format ?: throw IOException("Extractor track not selected")
    val sampleRate = format.getIntegerSafe(MediaFormat.KEY_SAMPLE_RATE) ?: throw IOException("Channels info is required")
    val channels = format.getIntegerSafe(MediaFormat.KEY_CHANNEL_COUNT) ?: throw IOException("SampleRate info is required")
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
    duration = -1
    sleepTime = 0
    accumulativeTs = 0
    lastExtractorTs = 0
    format = null
  }
}