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

import androidx.media3.common.C.TRACK_TYPE_AUDIO
import androidx.media3.common.C.TRACK_TYPE_VIDEO
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import com.pedro.common.frame.MediaFrame
import com.pedro.common.trySend
import java.util.concurrent.LinkedBlockingQueue

/**
 * Created by pedro on 25/10/24.
 */
@UnstableApi
class M3Output: ExtractorOutput {

  val dataQueue = LinkedBlockingQueue<M3Data>()
  var videoFormat: Format? = null
    private set
  var audioFormat: Format? = null
    private set

  data class M3Data(val bytes: ByteArray, val ts: Long, val type: MediaFrame.Type?)

  fun reset() {
    dataQueue.clear()
    videoFormat = null
    audioFormat = null
  }

  override fun track(id: Int, type: Int): TrackOutput {
    return when (type) {
      TRACK_TYPE_VIDEO -> videoCallback
      TRACK_TYPE_AUDIO -> audioCallback
      else -> callback
    }
  }

  override fun endTracks() {

  }

  override fun seekMap(seekMap: SeekMap) {

  }

  private val videoCallback = object: TrackOutput {

    private val bytes = mutableListOf<Byte>()

    override fun format(format: Format) {
      videoFormat = format
    }

    override fun sampleData(
      input: DataReader,
      length: Int,
      allowEndOfInput: Boolean,
      sampleDataPart: Int
    ): Int {
      val b = ByteArray(length)
      input.read(b, 0, length)
      bytes.addAll(b.toList())
      return length
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
      bytes.addAll(data.data.toList())
    }

    override fun sampleMetadata(
      timeUs: Long,
      flags: Int,
      size: Int,
      offset: Int,
      cryptoData: TrackOutput.CryptoData?
    ) {
      val data = bytes.toByteArray()
      dataQueue.trySend(M3Data(data, timeUs, MediaFrame.Type.VIDEO))
      bytes.clear()
    }
  }

  private val audioCallback = object: TrackOutput {

    private val bytes = mutableListOf<Byte>()

    override fun format(format: Format) {
      audioFormat = format
    }

    override fun sampleData(
      input: DataReader,
      length: Int,
      allowEndOfInput: Boolean,
      sampleDataPart: Int
    ): Int {
      val b = ByteArray(length)
      input.read(b, 0, length)
      bytes.addAll(b.toList())
      return length
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
      bytes.addAll(data.data.toList())
    }

    override fun sampleMetadata(
      timeUs: Long,
      flags: Int,
      size: Int,
      offset: Int,
      cryptoData: TrackOutput.CryptoData?
    ) {
      val data = bytes.toByteArray()
      dataQueue.trySend(M3Data(data, timeUs, MediaFrame.Type.AUDIO))
      bytes.clear()
    }
  }

  private val callback = object: TrackOutput {

    override fun format(format: Format) {
    }

    override fun sampleData(
      input: DataReader,
      length: Int,
      allowEndOfInput: Boolean,
      sampleDataPart: Int
    ): Int {
      return length
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
    }

    override fun sampleMetadata(
      timeUs: Long,
      flags: Int,
      size: Int,
      offset: Int,
      cryptoData: TrackOutput.CryptoData?
    ) {
      dataQueue.trySend(M3Data(byteArrayOf(), timeUs, null))
    }
  }
}