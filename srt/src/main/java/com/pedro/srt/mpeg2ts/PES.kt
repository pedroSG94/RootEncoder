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

package com.pedro.srt.mpeg2ts

import com.pedro.srt.utils.toInt
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.math.pow

/**
 * Created by pedro on 20/8/23.
 *
 * PES (Packetized Elementary Stream)
 *
 * Header (6 bytes):
 *
 * Packet start code prefix -> 3 bytes
 * Stream id -> 1 byte Examples: Audio streams (0xC0), Video streams (0xE0)
 * PES Packet length -> 2 bytes
 *
 * Optional
 * PES header -> variable length (length >= 3) 	not present in case of Padding stream & Private stream 2 (navigation data)
 *
 * Marker bits -> 2 	10 binary or 0x2 hex
 * Scrambling control -> 2 	00 implies not scrambled
 * Priority -> 1
 * Data alignment indicator -> 1 	1 indicates that the PES packet header is immediately followed by the video start code or audio syncword
 * Copyright -> 1 	1 implies copyrighted
 * Original or Copy -> 1 	1 implies original
 * PTS DTS indicator -> 2 	11 = both present, 01 is forbidden, 10 = only PTS, 00 = no PTS or DTS
 * ESCR flag -> 1
 * ES rate flag -> 1
 * DSM trick mode flag -> 1
 * Additional copy info flag -> 1
 * CRC flag -> 1
 * extension flag -> 1
 * PES header length -> 8 	gives the length of the remainder of the PES header in bytes
 * Optional fields -> variable length
 * Stuffing Bytes -> variable length -> fill remaining ts packet with 0xff
 *
 * Data -> Variable
 */
data class PES(
  private val streamId: PesType,
  private val payloadSize: Int,
  private val data: ByteArray = byteArrayOf()
) {

  init {
    if (data.size > UShort.MAX_VALUE.toInt()) {
      throw IllegalArgumentException("data size is too large")
    }
  }

  companion object {
    const val HeaderLength = 14
    const val SYSTEM_CLOCK_FREQ = 27000000
  }

  private val startCodePrefix: Int = 0x000001
  private val length = HeaderLength - 6 + payloadSize
  private val markerBits = 2
  private val scramblingControl = 0
  private val priority = false
  private val dataAlignmentIndicator = false
  private val copyright = false
  private val originalOrCopy = true
  private val ptsdtsIndicator = 2 //(only pts)
  private val otherFlags = 0 // ESCR flag, ES rate flag, DSM trick mode flag, Additional copy info flag, CRC flag, extension flag
  private val pesHeaderLength = 5

  fun write(buffer: ByteBuffer, pts: Long, maxSize: Int) {
    writeHeader(buffer, pts, maxSize)
    buffer.put(data)
  }

  private fun writeHeader(buffer: ByteBuffer, pts: Long, maxSize: Int) {
    buffer.putInt(startCodePrefix shl 8 or streamId.value.toInt())
    val l = if (length > 0xFFFF) 0 else length
    buffer.putShort(l.toShort())
    val info = ((markerBits shl 6) or (scramblingControl shl 4) or (priority.toInt() shl 3) or (dataAlignmentIndicator.toInt() shl 3) or (copyright.toInt() shl 3) or originalOrCopy.toInt()).toByte()
    buffer.put(info)
    val flags = ((ptsdtsIndicator shl 6) or otherFlags).toByte()
    buffer.put(flags)
    buffer.put(pesHeaderLength.toByte())
    addTimestamp(buffer, pts, 0b0010) //four bits indicate no dts
    val stuffingSize = maxSize - HeaderLength - data.size
    val stuffingBytes = ByteArray(stuffingSize) { 0xFF.toByte() }
    buffer.put(stuffingBytes)
  }

  private fun addTimestamp(buffer: ByteBuffer, timestamp: Long, fourBits: Byte) {
    val pts =
      (SYSTEM_CLOCK_FREQ * timestamp / 1000000 /* µs -> s */ / 300) % 2.toDouble()
        .pow(33)
        .toLong()

    buffer.put((((fourBits and 0xF).toInt() shl 4) or ((pts shr 29) and 0xE).toInt() or 1).toByte())
    buffer.putShort((((pts shr 14) and 0xFFFE) or 1).toShort())
    buffer.putShort((((pts shl 1) and 0xFFFE) or 1).toShort())
  }
}