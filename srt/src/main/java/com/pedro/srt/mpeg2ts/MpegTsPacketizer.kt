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

import com.pedro.srt.mpeg2ts.psi.Psi
import com.pedro.srt.utils.toByteArray
import com.pedro.srt.utils.toInt
import java.nio.ByteBuffer

/**
 * Created by pedro on 28/8/23.
 *
 */
class MpegTsPacketizer() {

  private val packetSize = 188

  //4 bytes header
  private fun writeHeader(buffer: ByteBuffer, startIndicator: Boolean, pid: Int, adaptationFieldControl: AdaptationFieldControl, continuity: Int) {
    val transportErrorIndicator = false
    val transportPriority = false
    val transportScramblingControl = 0

    buffer.put(0x47) //sync byte
    val combined: Short = ((transportErrorIndicator.toInt() shl 15)
        or (startIndicator.toInt() shl 14)
        or (transportPriority.toInt() shl 13) or pid).toShort()
    buffer.putShort(combined)
    val combined2: Byte = ((transportScramblingControl and 0x3 shl 6)
        or (adaptationFieldControl.value.toInt() and 0x3 shl 4) or (continuity and 0xF)).toByte()
    buffer.put(combined2)
  }

  /**
   * return a list of mpeg2ts packets
   */
  fun write(payload: List<MpegTsPayload>): List<ByteArray> {
    val packets = mutableListOf<ByteArray>()
    payload.forEachIndexed { index, mpegTsPayload ->
      var buffer = ByteBuffer.allocate(packetSize)
      var isFirstPacket = index == 0
      var continuity = index and 0xF

      when (mpegTsPayload) {
        is Psi -> {
          writeHeader(buffer, true, mpegTsPayload.pid, AdaptationFieldControl.PAYLOAD, continuity)

          val psi = mpegTsPayload
          psi.write(buffer)
          val stuffingSize = buffer.remaining()
          writeStuffingBytes(buffer, stuffingSize)

          packets.add(buffer.toByteArray())
        }

        is Pes -> {
          val pes = mpegTsPayload
          var adaptationFieldControl = AdaptationFieldControl.ADAPTATION_PAYLOAD
          writeHeader(buffer, true, mpegTsPayload.pid, adaptationFieldControl, continuity)
          val adaptationField = AdaptationField(
            discontinuityIndicator = false,
            randomAccessIndicator = mpegTsPayload.isKeyFrame, //only video can be true
            pcr = System.nanoTime() / 1000
          )
          buffer.put(adaptationField.getData())
          pes.writeHeader(buffer)

          val data = pes.bufferData
          while (data.hasRemaining()) {
            if (isFirstPacket) {
              isFirstPacket = false
              adaptationFieldControl = AdaptationFieldControl.PAYLOAD
            } else {
              writeHeader(buffer, false, mpegTsPayload.pid, adaptationFieldControl, continuity)
            }
            val size = minOf(data.remaining(), buffer.remaining())
            if (size < buffer.remaining()) { //last packet
              val stuffingSize = buffer.remaining() - data.remaining()
              writeStuffingBytes(buffer, stuffingSize)
            }
            buffer.put(data.array(), data.position(), size)
            data.position(data.position() + size)
            packets.add(buffer.toByteArray())
            continuity = (continuity + 1) and 0xF
            buffer = ByteBuffer.allocate(packetSize)
          }
        }
      }
    }
    return packets
  }

  private fun writeStuffingBytes(byteBuffer: ByteBuffer, size: Int) {
    val bytes = ByteArray(size) { 0xFF.toByte() }
    byteBuffer.put(bytes)
  }
}