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

package com.pedro.encoder.input.decoder.extractor

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Created by pedro on 6/6/24.
 *
 * Allow to extract multiple media files at the same time.
 */
class MultiMediaExtractor {

  private val mediaExtractors = mutableListOf<MediaExtractor>()
  private var currentExtractor = 0
  var mediaFormat: MediaFormat? = null
    private set
  private var totalDuration = 0L
  private var accumulativeSampleTime = 0L
  private var sampleTime = 0L

  @Throws(IOException::class)
  fun setDataSource(path: List<String>) {
    path.forEach {
      val mediaExtractor = MediaExtractor()
      mediaExtractor.setDataSource(it)
      mediaExtractors.add(mediaExtractor)
    }
  }

  @Throws(IOException::class)
  fun setDataSource1(fileDescriptors: List<FileDescriptor>) {
    fileDescriptors.forEach {
      val mediaExtractor = MediaExtractor()
      mediaExtractor.setDataSource(it)
      mediaExtractors.add(mediaExtractor)
    }
  }

  @RequiresApi(Build.VERSION_CODES.N)
  @Throws(IOException::class)
  fun setDataSource2(assetFileDescriptors: List<AssetFileDescriptor>) {
    assetFileDescriptors.forEach {
      val mediaExtractor = MediaExtractor()
      mediaExtractor.setDataSource(it)
      mediaExtractors.add(mediaExtractor)
    }
  }

  @RequiresApi(Build.VERSION_CODES.M)
  @Throws(IOException::class)
  fun setDataSource3(mediaDataSources: List<MediaDataSource>) {
    mediaDataSources.forEach {
      val mediaExtractor = MediaExtractor()
      mediaExtractor.setDataSource(it)
      mediaExtractors.add(mediaExtractor)
    }
  }

  @Throws(IOException::class)
  fun setDataSource4(path: List<String>, headers: List<Map<String, String>?>) {
    path.forEachIndexed { index, s ->
      val mediaExtractor = MediaExtractor()
      mediaExtractor.setDataSource(s, headers[index])
      mediaExtractors.add(mediaExtractor)
    }
  }

  @Throws(IOException::class)
  fun setDataSource5(fileDescriptors: List<FileDescriptor>, offset: List<Long>, length: List<Long>) {
    fileDescriptors.forEachIndexed { index, fileDescriptor ->
      val mediaExtractor = MediaExtractor()
      mediaExtractor.setDataSource(fileDescriptor, offset[index], length[index])
      mediaExtractors.add(mediaExtractor)
    }
  }

  @Throws(IOException::class)
  fun setDataSource6(context: Context, uris: List<Uri>, headers: List<Map<String, String>?>?) {
    uris.forEachIndexed { index, uri ->
      val mediaExtractor = MediaExtractor()
      mediaExtractor.setDataSource(context, uri, headers?.get(index))
      mediaExtractors.add(mediaExtractor)
    }
  }

  fun readSampleData(buffer: ByteBuffer, offset: Int): Int {
    val result = mediaExtractors[currentExtractor].readSampleData(buffer, offset)
    val time = mediaExtractors[currentExtractor].sampleTime
    if (currentExtractor < mediaExtractors.size - 1 && (result < 0 || time < 0)) {
      currentExtractor++
      mediaExtractors[currentExtractor].seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
      return readSampleData(buffer, offset)
    }
    sampleTime = time + accumulativeSampleTime
    mediaExtractors[currentExtractor].advance()
    return result
  }

  fun seekTo(time: Long, mode: Int = MediaExtractor.SEEK_TO_CLOSEST_SYNC) {
    if (time == 0L) {
      currentExtractor = 0
      accumulativeSampleTime = 0L
      sampleTime = 0L
      mediaExtractors.forEach { it.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC) }
    } else {
      mediaExtractors[currentExtractor].seekTo(time, mode)
    }
  }

  fun getSampleTime(): Long {
    return sampleTime
  }

  fun isLastFrame(): Boolean {
    return currentExtractor == mediaExtractors.size - 1 && mediaExtractors[currentExtractor].sampleTime < 0
  }

  fun shouldReset(lastTs: Long): Boolean {
    val result = mediaExtractors[currentExtractor].sampleTime < 0
    if (result) accumulativeSampleTime += lastTs
    return result
  }

  fun release() {
    mediaExtractors.forEach { it.release() }
    mediaExtractors.clear()
    mediaFormat = null
    totalDuration = 0L
    accumulativeSampleTime = 0L
    sampleTime = 0L
  }

  fun selectTrack(type: String) {
    val videoInfo = mutableListOf<VideoInfo>()
    mediaExtractors.forEach { extractor ->
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = (format.getString(MediaFormat.KEY_MIME) ?: "")
        if (mime.startsWith(type)) {
          extractor.selectTrack(i)
          this.mediaFormat = format
          if (type.startsWith("video")) {
            videoInfo.add(VideoInfo(getMime(), getWidth(), getHeight(), getFps(), 0, 0, getDuration()))
          } else if (type.startsWith("audio")) {
            videoInfo.add(VideoInfo(getMime(), 0, 0, 0, getSampleRate(), getChannelCount(), getDuration()))
          }
        }
      }
    }
    if (videoInfo.isEmpty()) {
      throw IllegalArgumentException("Track not found ($type)")
    } else if (type.startsWith("video")) {
      val info = videoInfo[0]
      videoInfo.forEach {
        if (it.width != info.width || it.height != info.height) {
          throw IllegalArgumentException("Video tracks must have the same resolution")
        }
      }
    } else if (type.startsWith("audio")) {
      val info = videoInfo[0]
      videoInfo.forEach {
        if (it.sampleRate != info.sampleRate || it.channels != info.channels) {
          throw IllegalArgumentException("Audio tracks must have the same sample rate and channels")
        }
      }
    }
    totalDuration = videoInfo.sumOf { it.duration }
  }

  fun getMime(): String = mediaFormat?.getString(MediaFormat.KEY_MIME) ?: ""
  fun getWidth(): Int = mediaFormat?.getInteger(MediaFormat.KEY_WIDTH) ?: 0
  fun getHeight(): Int = mediaFormat?.getInteger(MediaFormat.KEY_HEIGHT) ?: 0
  fun getFps(): Int = mediaFormat?.getInteger(MediaFormat.KEY_FRAME_RATE) ?: 0
  fun getSampleRate(): Int = mediaFormat?.getInteger(MediaFormat.KEY_SAMPLE_RATE) ?: 0
  fun getChannelCount(): Int = mediaFormat?.getInteger(MediaFormat.KEY_CHANNEL_COUNT) ?: 0
  fun getTotalDuration(): Long = totalDuration
  private fun getDuration(): Long = mediaFormat?.getLong(MediaFormat.KEY_DURATION) ?: 0L
}