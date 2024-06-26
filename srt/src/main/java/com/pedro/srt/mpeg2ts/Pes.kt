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

package com.pedro.srt.mpeg2ts

import com.pedro.srt.utils.Constants
import com.pedro.srt.utils.toInt
import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.math.pow

/**
 * Created by pedro on 28/8/23.
 */
class Pes(
  pid: Int,
  isKeyFrame: Boolean,
  private val streamId: PesType,
  private val pts: Long,
  val bufferData: ByteBuffer
): MpegTsPayload(pid, isKeyFrame) {

  private val headerLength = 14

  private val length = headerLength + bufferData.remaining()
  private val markerBits = 2
  private val scramblingControl = 0
  private val priority = false
  private val dataAlignmentIndicator = false
  private val copyright = false
  private val originalOrCopy = true
  private val ptsdtsIndicator = 2 //(only pts)
  private val otherFlags = 0 // ESCR flag, ES rate flag, DSM trick mode flag, Additional copy info flag, CRC flag, extension flag
  private val pesHeaderLength = 5 //pts size

  fun writeHeader(buffer: ByteBuffer) {
    buffer.putShort(0)
    buffer.put(1)
    buffer.put(streamId.value)
    // - 6 because the length count after insert length in the header
    val l = if (length > 0xFFFF) 0 else length - 6
    buffer.putShort(l.toShort())
    val info = ((markerBits shl 6) or (scramblingControl shl 4) or (priority.toInt() shl 3) or (dataAlignmentIndicator.toInt() shl 3) or (copyright.toInt() shl 3) or originalOrCopy.toInt()).toByte()
    buffer.put(info)
    val flags = ((ptsdtsIndicator shl 6) or otherFlags).toByte()
    buffer.put(flags)
    buffer.put(pesHeaderLength.toByte())
    addTimestamp(buffer, pts, 0b0010) //indicate no dts
  }

  private fun addTimestamp(buffer: ByteBuffer, timestamp: Long, fourBits: Byte) {
    val pts =
      (Constants.SYSTEM_CLOCK_FREQ * timestamp / 1000000 /* µs -> s */ / 300) % 2.toDouble()
        .pow(33)
        .toLong()

    buffer.put((((fourBits and 0xF).toInt() shl 4) or ((pts shr 29) and 0xE).toInt() or 1).toByte())
    buffer.putShort((((pts shr 14) and 0xFFFE) or 1).toShort())
    buffer.putShort((((pts shl 1) and 0xFFFE) or 1).toShort())
  }
}