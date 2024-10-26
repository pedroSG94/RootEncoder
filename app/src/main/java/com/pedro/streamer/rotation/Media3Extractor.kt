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

package com.pedro.streamer.rotation

import android.content.Context
import android.media.MediaFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.PositionHolder
import com.pedro.common.frame.MediaFrame
import com.pedro.encoder.input.decoder.AudioInfo
import com.pedro.encoder.input.decoder.Extractor
import com.pedro.encoder.input.decoder.VideoInfo
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Created by pedro on 22/10/24.
 */
@UnstableApi
class Media3Extractor(private val context: Context): Extractor {

  private var position = PositionHolder()
  private var extractor: androidx.media3.extractor.Extractor? = null
  private var input: DefaultExtractorInput? = null
  private val output = M3Output()
  private var source: DefaultDataSource? = null
  private var type: MediaFrame.Type? = null
  private var sleepTime: Long = 0
  private var accumulativeTs: Long = 0
  @Volatile
  private var lastExtractorTs: Long = 0
  private var finished = false
  private var currentMedia: M3Output.M3Data? = null

  override fun selectTrack(type: MediaFrame.Type): String {
    this.type = type
    extractor!!.read(input!!, position)
    val mime = when (type) {
      MediaFrame.Type.VIDEO -> output.videoFormat!!.sampleMimeType!!
      MediaFrame.Type.AUDIO -> output.audioFormat!!.sampleMimeType!!
    }
    return mime
  }

  override fun initialize(path: String) {
    val uri =FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(path))
    initialize(context, uri)
  }

  private var d = -1
  override fun initialize(context: Context, uri: Uri) {
    this.source = null
    this.extractor = null
    this.input = null
    finished = false

    position = PositionHolder()
    var extractor: androidx.media3.extractor.Extractor? = null
    val dataSource = DefaultDataSource.Factory(context)
    val source = dataSource.createDataSource()
    val extractors = DefaultExtractorsFactory().createExtractors(uri, source.responseHeaders)

    val spec = DataSpec(uri)
    val length = source.open(spec)
    val input = DefaultExtractorInput(source,0, length)
    for (e in extractors) {
      try {
        if (e.sniff(input)) {
          extractor = e
          break
        }
      } finally {
        Assertions.checkState(extractor != null || input.position == 0L)
        input.resetPeekPosition()
      }
    }
    if (extractor == null) throw IOException("Valid extractor not found")
    extractor.init(output)
    this.extractor = extractor
    this.input = input
    this.source = source
  }

  override fun initialize(fileDescriptor: FileDescriptor) {
    val file = File(fileDescriptor.toString())
    val uri =FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    initialize(context, uri)
  }

  override fun readFrame(buffer: ByteBuffer): Int {
    while (!output.dataQueue.isEmpty()) {
      val media = output.dataQueue.poll(1, TimeUnit.SECONDS)
      if (media.type == type) {
        val size = media.bytes.size
        buffer.put(media.bytes)
        this.currentMedia = media
        return size
      }
    }
    while (!finished) {
      finished = extractor?.read(input!!, position) == androidx.media3.extractor.Extractor.RESULT_END_OF_INPUT
      val media = output.dataQueue.poll(1, TimeUnit.SECONDS) ?: continue
      if (media.type != null && media.type == type) {
        val size = media.bytes.size
        buffer.put(media.bytes)
        this.currentMedia = media
        return size
      }
    }
    return -1
  }

  override fun advance(): Boolean {
    return !finished
  }

  override fun getTimeStamp(): Long {
    return currentMedia?.ts ?: 0
  }

  override fun getSleepTime(ts: Long): Long {
    val extractorTs = getTimeStamp()
    accumulativeTs += extractorTs - lastExtractorTs
    lastExtractorTs = getTimeStamp()
    sleepTime = if (accumulativeTs > ts) (accumulativeTs - ts) / 1000 else 0
    return sleepTime
  }

  override fun seekTo(time: Long) {
    extractor?.seek(0, time)
    lastExtractorTs = time
  }

  override fun release() {
    finished = false
    sleepTime = 0
    accumulativeTs = 0
    lastExtractorTs = 0
    source?.close()
    source = null
    input = null
    extractor = null
    this.type = null
    output.reset()
  }

  override fun getVideoInfo(): VideoInfo {
    val format = output.videoFormat!!
    var fps = format.frameRate.toInt()
    if (fps <= 0) fps = 30
    return VideoInfo(
      format.width,
      format.height,
      fps,
      -1
    )
  }

  override fun getAudioInfo(): AudioInfo {
    val format = output.audioFormat!!
    return AudioInfo(
      format.sampleRate,
      format.channelCount,
      -1
    )
  }

  override fun getFormat(): MediaFormat {
    val mediaFormat = MediaFormat()
    when (type) {
      MediaFrame.Type.VIDEO -> {
        val format = output.videoFormat!!
        mediaFormat.setString(MediaFormat.KEY_MIME, format.sampleMimeType)
        mediaFormat.setInteger(MediaFormat.KEY_WIDTH, format.width)
        mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, format.height)
        MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData)
        MediaFormatUtil.maybeSetFloat(mediaFormat, MediaFormat.KEY_FRAME_RATE, format.frameRate)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          MediaFormatUtil.maybeSetInteger(
            mediaFormat,
            MediaFormat.KEY_ROTATION,
            format.rotationDegrees
          )
        }
        MediaFormatUtil.maybeSetColorInfo(mediaFormat, format.colorInfo)
      }
      MediaFrame.Type.AUDIO -> {
        val format = output.audioFormat!!
        mediaFormat.setString(MediaFormat.KEY_MIME, format.sampleMimeType)
        mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, format.sampleRate)
        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, format.channelCount)
        MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData)

        // Set codec max values.
        MediaFormatUtil.maybeSetInteger(
          mediaFormat,
          MediaFormat.KEY_MAX_INPUT_SIZE,
          format.maxInputSize
        )

      }
      null -> throw IOException("Track not selected")
    }
    return mediaFormat
  }
}