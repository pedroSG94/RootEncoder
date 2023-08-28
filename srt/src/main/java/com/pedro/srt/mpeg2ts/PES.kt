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

import android.util.Log
import com.pedro.srt.utils.Constants
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
 *
 * 47, 40, 20, 38, 07, 10, C7, 28, 20, 6C, 7E, F9,
 * 00, 00, 01, C0, 01, 15, 81, 80, 05, 21, 00, 4B, 08, EB, FF, F1, 54, 80, 21, BF, FC, 21, 1B, 8F, FF, FF, FF, E4, 10, 71, 14, 88, 42, 15, 82, 82, 22, 08, B4, 42, 1D, 28, 87, 4A, 22, 57, 55, 2A, B5, 59, 77, 52, A5, 5A, 65, 95, 04, 79, C8, 55, D2, C4, F2, 1E, DF, C7, 41, 39, F5, 94, BF, 53, 6A, A6, BF, 35, 8C, B6, 6D, C6, 27, 9C, 40, E0, 27, B7, 61, 96, DD, 82, B4, 26, 6E, 18, 8C, B8, 12, 12, AA, 80, C0, 89, 9C, E1, 5C, B4, 48, AE, 59, FB, 1D, 75, 64, F4, FA, E2, 9B, DD, 8C, F6, A1, 1D, 60, BA, 70, CC, 3E, F3, 25, 48, 5A, DE, 33, 2C, 57, D9, ED, D8, 7C, 8C, 0D, 3F, 26, 67, 86, C8, CC, 85, E6, 00, AA, 37, 9A, 0A, 96, 8D, C8, 42, 60, 04, 5C, 3D, 10, 44, 31, E8, 84, 3A, 51, 0E, 94, 44, D5, A9, 25, EE, 47, 00, 20, 39, 4C, 00,
 * FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, E2, AF, 2E, 25, 25, 40, 8E, EA, 49, 7B, 8C, D5, 9E, 43, BC, B4, 08, BC, 3A, E9, E6, 78, 4F, 3C, 61, 4A, C1, 36, 15, 96, 1C, 16, 59, 64, C0, 02, 9A, 31, 8E, E2, D6, C2, 06, AA, 80, CE, B8, DC, C7, 6B, AB, 38, BB, 5E, 56, C6, 58, 14, 66, 43, 3F, 66, 07, E4, 21, D7, 45, 25, D6, 46, 80, 07, 7C, 7E, C0, 4A, 73, EA, 94, 49, 5C, 8E, CA, 8B, 3B, 1E, 2F, 6D, D9, 27, 87, 1D, 63, C9, 98, 8A, A2, 91, 5B, 50, 0A, FB, D5, 28, 80, 90, 07]
 *
 * [47, 40, 20, 30, 08, 10, C7, 28, 21, 59, 7E, E7, 00, 00, 01, C0, 01, 15, 81, 80, 05, 21, 00, 4B, 08, EB, FF, F1, 54, 80, 21, BF, FC, 21, 1B, 8F, FF, FF, FF, E4, 10, 71, 14, 88, 42, 15, 82, 82, 22, 08, B4, 42, 1D, 28, 87, 4A, 22, 57, 55, 2A, B5, 59, 77, 52, A5, 5A, 65, 95, 04, 79, C8, 55, D2, C4, F2, 1E, DF, C7, 41, 39, F5, 94, BF, 53, 6A, A6, BF, 35, 8C, B6, 6D, C6, 27, 9C, 40, E0, 27, B7, 61, 96, DD, 82, B4, 26, 6E, 18, 8C, B8, 12, 12, AA, 80, C0, 89, 9C, E1, 5C, B4, 48, AE, 59, FB, 1D, 75, 64, F4, FA, E2, 9B, DD, 8C, F6, A1, 1D, 60, BA, 70, CC, 3E, F3, 25, 48, 5A, DE, 33, 2C, 57, D9, ED, D8, 7C, 8C, 0D, 3F, 26, 67, 86, C8, CC, 85, E6, 00, AA, 37, 9A, 0A, 96, 8D, C8, 42, 60, 04, 5C, 3D, 10, 44, 31, E8, 84, 3A, 51, 0E, 94, 44, D5, A9, 25, EE, 47, 00, 20, 31, 00, 00, 01, C0, 01, 15, 81, 80, 05, 21, 00, 4B, 08, EB,
 * FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, E2, AF, 2E, 25, 25, 40, 8E, EA, 49, 7B, 8C, D5, 9E, 43, BC, B4, 08, BC, 3A, E9, E6, 78, 4F, 3C, 61, 4A, C1, 36, 15, 96, 1C, 16, 59, 64, C0, 02, 9A, 31, 8E, E2, D6, C2, 06, AA, 80, CE, B8, DC, C7, 6B, AB, 38, BB, 5E, 56, C6, 58, 14, 66, 43, 3F, 66, 07, E4, 21, D7, 45, 25, D6, 46, 80, 07, 7C, 7E, C0, 4A, 73, EA, 94, 49, 5C, 8E, CA, 8B, 3B, 1E, 2F, 6D, D9, 27, 87, 1D, 63, C9, 98, 8A, A2, 91, 5B, 50, 0A, FB, D5, 28, 80, 90, 07]
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
  }

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

  fun write(buffer: ByteBuffer, pts: Long, maxSize: Int, shouldWriteHeader: Boolean) {
    if (shouldWriteHeader) writeHeader(buffer, pts)
    val stuffingSize = maxSize - (if (shouldWriteHeader) HeaderLength else 0) - data.size
    val stuffingBytes = ByteArray(stuffingSize) { 0xFF.toByte() }
    buffer.put(stuffingBytes)
    buffer.put(data)
  }

  private fun writeHeader(buffer: ByteBuffer, pts: Long) {
    buffer.putShort(0)
    buffer.put(1)
    buffer.put(streamId.value)
    val l = if (length > 0xFFFF) 0 else length
    buffer.putShort(l.toShort())
    val info = ((markerBits shl 6) or (scramblingControl shl 4) or (priority.toInt() shl 3) or (dataAlignmentIndicator.toInt() shl 3) or (copyright.toInt() shl 3) or originalOrCopy.toInt()).toByte()
    buffer.put(info)
    val flags = ((ptsdtsIndicator shl 6) or otherFlags).toByte()
    buffer.put(flags)
    buffer.put(pesHeaderLength.toByte())
    addTimestamp(buffer, pts, 0b0010) //four bits indicate no dts
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