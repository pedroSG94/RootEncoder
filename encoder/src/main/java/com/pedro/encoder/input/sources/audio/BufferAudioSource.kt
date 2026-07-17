/*
 *
 *  * Copyright (C) 2026 pedroSG94.
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

package com.pedro.encoder.input.sources.audio

import com.pedro.common.TimeUtils
import com.pedro.encoder.Frame
import com.pedro.encoder.audio.AudioEncoder
import com.pedro.encoder.input.audio.GetMicrophoneData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

/**
 * Created by pedro on 17/7/26.
 *
 * Audio source backed by a synthetic PCM buffer (silence) consumed at fixed intervals.
 * Use [setBuffer] to replace parts of the synthetic buffer with real PCM data.
 * Consumed regions are restored to silence, so if no data is provided silence is streamed.
 *
 * @param bufferCapacityMs capacity of the synthetic buffer in milliseconds.
 */
class BufferAudioSource(
  private val bufferCapacityMs: Int = 1000
): AudioSource(), GetMicrophoneData {

  private var running = false
  private var job: Job? = null
  private var sleepTime = 0L
  private val chunkSize = AudioEncoder.inputSize / 4
  private var buffer = ByteArray(0)
  private var readIndex = 0
  private var writeIndex = 0
  private var queuedBytes = 0
  private var bytesPerSecond = 0
  private var frameSize = 2
  private var consumedBytes = 0L
  private var producerBaseTs = -1L
  private var consumedBytesAtBase = 0L
  private val lock = Any()

  override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
    val channels = if (isStereo) 2 else 1
    bytesPerSecond = sampleRate * channels * 2
    frameSize = channels * 2
    sleepTime = chunkSize * 1_000_000L / bytesPerSecond //in us
    val chunks = maxOf(1, (bytesPerSecond * bufferCapacityMs / 1000) / chunkSize)
    synchronized(lock) {
      buffer = ByteArray(chunks * chunkSize)
      readIndex = 0
      writeIndex = 0
      queuedBytes = 0
      consumedBytes = 0
      producerBaseTs = -1
    }
    return true
  }

  override fun start(getMicrophoneData: GetMicrophoneData) {
    this.getMicrophoneData = getMicrophoneData
    synchronized(lock) {
      buffer.fill(0)
      readIndex = 0
      writeIndex = 0
      queuedBytes = 0
      consumedBytes = 0
      producerBaseTs = -1
    }
    running = true
    job = CoroutineScope(Dispatchers.IO).launch {
      val chunk = ByteArray(chunkSize)
      val startTime = TimeUtils.getCurrentTimeMicro()
      var count = 0L
      while (running) {
        synchronized(lock) {
          buffer.copyInto(chunk, 0, readIndex, readIndex + chunkSize)
          buffer.fill(0, readIndex, readIndex + chunkSize)
          readIndex = (readIndex + chunkSize) % buffer.size
          consumedBytes += chunkSize
          if (queuedBytes > chunkSize) {
            queuedBytes -= chunkSize
          } else {
            //partially written chunk consumed, keep writing after the read position
            queuedBytes = 0
            writeIndex = readIndex
          }
        }
        getMicrophoneData.inputPCMData(Frame(chunk, 0, chunk.size, TimeUtils.getCurrentTimeMicro()))
        count++
        val nextFrameTime = startTime + count * sleepTime
        delay((nextFrameTime - TimeUtils.getCurrentTimeMicro()).microseconds)
      }
    }
  }

  /**
   * Replace the corresponding part of the synthetic buffer with PCM data.
   * Data exceeding the buffer capacity is discarded.
   *
   * @return the number of bytes written.
   */
  fun setBuffer(pcmBuffer: ByteArray, offset: Int = 0, size: Int = pcmBuffer.size): Int {
    synchronized(lock) {
      if (buffer.isEmpty()) return 0
      val toWrite = minOf(size, buffer.size - queuedBytes)
      var written = 0
      while (written < toWrite) {
        val length = minOf(toWrite - written, buffer.size - writeIndex)
        pcmBuffer.copyInto(buffer, writeIndex, offset + written, offset + written + length)
        writeIndex = (writeIndex + length) % buffer.size
        written += length
      }
      queuedBytes += written
      return written
    }
  }

  /**
   * Replace the part of the synthetic buffer matching the provided timestamp.
   *
   * The first call latches the base timestamp, mapped to the current read position (played as
   * soon as possible). Following calls are placed relative to that base using the timestamp
   * delta, so gaps between non contiguous buffers are kept as silence. Data whose time was
   * already consumed or is further in the future than the buffer capacity is discarded.
   *
   * @param timestampMicro buffer timestamp in microseconds, in the producer timebase.
   * @return the number of bytes written.
   */
  fun setBuffer(pcmBuffer: ByteArray, timestampMicro: Long, offset: Int = 0, size: Int = pcmBuffer.size): Int {
    synchronized(lock) {
      if (buffer.isEmpty()) return 0
      if (producerBaseTs < 0) {
        producerBaseTs = timestampMicro
        consumedBytesAtBase = consumedBytes
      }
      var position = usToBytes(timestampMicro - producerBaseTs) - (consumedBytes - consumedBytesAtBase)
      var srcOffset = offset
      var toWrite = size
      if (position < 0) { //data partially or fully in the past, discard the late part
        val dropped = minOf(-position, toWrite.toLong()).toInt()
        srcOffset += dropped
        toWrite -= dropped
        position = 0
      }
      if (position >= buffer.size || toWrite <= 0) return 0
      toWrite = minOf(toWrite.toLong(), buffer.size - position).toInt()
      var written = 0
      var index = (readIndex + position.toInt()) % buffer.size
      while (written < toWrite) {
        val length = minOf(toWrite - written, buffer.size - index)
        pcmBuffer.copyInto(buffer, index, srcOffset + written, srcOffset + written + length)
        index = (index + length) % buffer.size
        written += length
      }
      queuedBytes = maxOf(queuedBytes, position.toInt() + toWrite)
      writeIndex = (readIndex + queuedBytes) % buffer.size
      return written
    }
  }

  private fun usToBytes(timeMicro: Long): Long {
    val bytes = timeMicro * bytesPerSecond / 1_000_000L
    return bytes - bytes % frameSize
  }

  override fun stop() {
    running = false
    runBlocking { job?.cancelAndJoin() }
  }

  override fun isRunning(): Boolean = running

  override fun release() {}

  override fun inputPCMData(frame: Frame) {
    getMicrophoneData?.inputPCMData(frame)
  }
}
