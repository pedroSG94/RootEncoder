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

package com.pedro.srt.mpeg2ts.psi

import com.pedro.srt.utils.CRC32
import com.pedro.srt.utils.toInt
import java.nio.ByteBuffer

/**
 * Created by pedro on 20/8/23.
 *
 * PSI (Program Specific Information)
 *
 * Header (3 bytes):
 *
 * Table ID -> 8 bits
 * Section syntax indicator -> 1 bit
 * Private bit -> 1 bit
 * Reserved bits -> 2 bits
 * Section length unused bits -> 2 bits
 * Section length -> 10 bits
 *
 * Syntax section/Table data -> N*8 bits
 *
 * Table ID extension -> 16 bits
 * Reserved bits -> 2 bits
 * Version number -> 5 bits
 * Current/next indicator -> 1 bit
 * Section number -> 8 bits
 * Last section number -> 8 bits
 * Table data -> N*8 bits
 * CRC32 -> 32 bits
 */
abstract class PSI(
  val pid: Short,
  private val id: Byte,
  private val idExtension: Short,
  var version: Byte,
  private val sectionSyntaxIndicator: Boolean = true,
  private val privateBit: Boolean = false,
  private val indicator: Boolean = true, //true current, false next
  private val sectionNumber: Byte = 0,
  private val lastSectionNumber: Byte = 0,
) {

  private val reserved = 3
  private val sectionLengthUnusedBits = 0
  private var sectionLength: Short = 0

  fun write(byteBuffer: ByteBuffer, psiSize: Int) {
    byteBuffer.put(0x00)
    val crc32InitialPosition = byteBuffer.position()
    byteBuffer.put(id)
    sectionLength = (9 + getTableDataSize()).toShort()
    val combined = (sectionSyntaxIndicator.toInt() shl 15) or (privateBit.toInt() shl 14) or
        (reserved shl 12) or (sectionLengthUnusedBits shl 10) or (sectionLength.toInt() and 0x03FF)
    byteBuffer.putShort(combined.toShort())
    byteBuffer.putShort(idExtension)
    val combined2 = (reserved shl 6) or (version.toInt() shl 1) or indicator.toInt()
    byteBuffer.put(combined2.toByte())
    byteBuffer.put(sectionNumber)
    byteBuffer.put(lastSectionNumber)
    writeData(byteBuffer)
    val crc32 = writeCRC(byteBuffer, crc32InitialPosition, byteBuffer.position())
    byteBuffer.putInt(crc32)
    fillPacket(byteBuffer, psiSize - getSize())
  }

  private fun fillPacket(buffer: ByteBuffer, size: Int) {
    var count = 0
    while (buffer.hasRemaining() && count < size) {
      buffer.put(0xFF.toByte())
      count++
    }
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