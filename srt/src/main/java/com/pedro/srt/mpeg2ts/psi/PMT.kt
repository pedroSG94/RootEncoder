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

import com.pedro.srt.mpeg2ts.Codec
import com.pedro.srt.mpeg2ts.service.Mpeg2TsService
import java.nio.ByteBuffer

/**
 * Created by pedro on 20/8/23.
 *
 * PMT (Program Map Table)
 *
 * A type of PSI packet
 *
 * Reserved bits -> 3 bits
 * PCR PID -> 13 	The packet identifier that contains the program clock reference used to improve the random access accuracy of the stream's timing that is derived from the program timestamp. If this is unused. then it is set to 0x1FFF (all bits on).
 * Reserved bits -> 4
 * Program info length unused bits -> 2 	Set to 0 (all bits off)
 * Program info length -> 10 	The number of bytes that follow for the program descriptors.
 * Program descriptors -> N*8 	When the program info length is non-zero, this is the program info length number of program descriptor bytes.
 * Elementary stream info data -> N*8
 */
class PMT(
  pmtPid: Short,
  idExtension: Short,
  version: Byte,
  private val service: Mpeg2TsService,
) : PSI(
  pid = pmtPid,
  id = 0x02,
  idExtension = idExtension,
  version = version,
) {

  private val reserved: Byte = 7
  private val reserved2: Byte = 15
  private val programInfoLengthUnused: Byte = 0

  override fun writeData(byteBuffer: ByteBuffer) {
    byteBuffer.putShort(((reserved.toInt() shl 13) or (service.pcrPID?.toInt() ?: 0)).toShort())
    byteBuffer.putShort(((reserved2.toInt() shl 12) or (programInfoLengthUnused.toInt() shl 10) or 0).toShort())

    service.tracks.forEach { track ->
      byteBuffer.put(track.codec.value)
      val programDescriptor = generateProgramDescriptor(track.codec)
      byteBuffer.putShort(((reserved.toInt() shl 13) or pid.toInt()).toShort())
      byteBuffer.putShort(((reserved2.toInt() shl 12) or (programInfoLengthUnused.toInt() shl 10) or programDescriptor.size).toShort())
      byteBuffer.put(programDescriptor)
    }
  }

  private fun generateProgramDescriptor(codec: Codec): ByteArray {
    return if (codec == Codec.HEVC) {
      val bytes = ByteArray(6)
      bytes[0] = 0x05
      bytes[1] = 0x04

      bytes[2] = "H".toByte()
      bytes[3] = "E".toByte()
      bytes[4] = "V".toByte()
      bytes[5] = "C".toByte()
      bytes
    } else {
      byteArrayOf()
    }
  }

  override fun getTableDataSize(): Int {
    var size = 4
    service.tracks.forEach { track ->
      size += 5
      if (track.codec == Codec.HEVC) size += 6
    }
    return size
  }
}