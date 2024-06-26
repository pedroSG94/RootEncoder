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

package com.pedro.srt.mpeg2ts.psi

import com.pedro.srt.mpeg2ts.MpegTsPayload
import com.pedro.srt.utils.CRC32
import com.pedro.srt.utils.toInt
import java.nio.ByteBuffer

/**
 * Created by pedro on 28/8/23.
 */
abstract class Psi(
  pid: Int,
  private val id: Byte,
  private val idExtension: Short,
  var version: Byte,
  private val sectionSyntaxIndicator: Boolean = true,
  private val privateBit: Boolean = false,
  private val indicator: Boolean = true, //true current, false next
  private val sectionNumber: Byte = 0,
  private val lastSectionNumber: Byte = 0,
): MpegTsPayload(pid, false) {

  private val reserved = 3
  private val sectionLengthUnusedBits = 0
  private var sectionLength = 0

  fun write(byteBuffer: ByteBuffer) {
    byteBuffer.put(0x00)
    val crc32InitialPosition = byteBuffer.position()
    byteBuffer.put(id)
    sectionLength = 9 + getTableDataSize()
    val combined = (sectionSyntaxIndicator.toInt() shl 15) or (privateBit.toInt() shl 14) or
        (reserved shl 12) or (sectionLengthUnusedBits shl 10) or (sectionLength and 0x03FF)
    byteBuffer.putShort(combined.toShort())
    byteBuffer.putShort(idExtension)
    val combined2 = (reserved shl 6) or (version.toInt() shl 1) or indicator.toInt()
    byteBuffer.put(combined2.toByte())
    byteBuffer.put(sectionNumber)
    byteBuffer.put(lastSectionNumber)
    writeData(byteBuffer)
    val crc32 = writeCRC(byteBuffer, crc32InitialPosition, byteBuffer.position())
    byteBuffer.putInt(crc32)
  }

  /**
   * https://en.wikipedia.org/wiki/Computation_of_cyclic_redundancy_checks#CRC-32_algorithm
   */
  private fun writeCRC(byteBuffer: ByteBuffer, offset: Int, size: Int): Int {
    return CRC32.getCRC32(byteBuffer.array(), offset, size)
  }

  abstract fun writeData(byteBuffer: ByteBuffer)

  abstract fun getTableDataSize(): Int

  fun getSize(): Int = 13 + getTableDataSize()
}