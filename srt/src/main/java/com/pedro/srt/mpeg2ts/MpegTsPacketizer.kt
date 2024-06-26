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

import com.pedro.srt.mpeg2ts.psi.Psi
import com.pedro.srt.mpeg2ts.psi.PsiManager
import com.pedro.common.TimeUtils
import com.pedro.common.toByteArray
import com.pedro.srt.utils.toInt
import java.nio.ByteBuffer

/**
 * Created by pedro on 28/8/23.
 *
 */
class MpegTsPacketizer(private val psiManager: PsiManager) {

  companion object {
    const val packetSize = 188
  }

  private var pesContinuity = 0
  private var psiContinuity = 0

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
  fun write(payload: List<MpegTsPayload>, increasePsiContinuity: Boolean = false): List<ByteArray> {
    val packets = mutableListOf<ByteArray>()
    if (increasePsiContinuity) psiContinuity = (psiContinuity + 1) and 0xF

    payload.forEachIndexed { index, mpegTsPayload ->
      var buffer = ByteBuffer.allocate(packetSize)
      var isFirstPacket = index == 0

      when (mpegTsPayload) {
        is Psi -> {
          writeHeader(buffer, true, mpegTsPayload.pid, AdaptationFieldControl.PAYLOAD, psiContinuity)
          mpegTsPayload.write(buffer)
          val stuffingSize = buffer.remaining()
          writeStuffingBytes(buffer, stuffingSize, false)
          packets.add(buffer.toByteArray())
        }
        is Pes -> {
          var adaptationFieldControl = AdaptationFieldControl.ADAPTATION_PAYLOAD
          writeHeader(buffer, true, mpegTsPayload.pid, adaptationFieldControl, pesContinuity)
          val isAudio = psiManager.getAudioPid().toInt() == mpegTsPayload.pid
          val pcr = if (isAudio && !mpegTsPayload.isKeyFrame) null else TimeUtils.getCurrentTimeMicro()
          val adaptationField = AdaptationField(
            discontinuityIndicator = false,
            randomAccessIndicator = mpegTsPayload.isKeyFrame, //only video can be true
            pcr = pcr
          )
          buffer.put(adaptationField.getData())
          mpegTsPayload.writeHeader(buffer)

          val data = mpegTsPayload.bufferData
          while (data.hasRemaining()) {
            if (isFirstPacket) {
              isFirstPacket = false
              adaptationFieldControl = AdaptationFieldControl.PAYLOAD
            } else {
              writeHeader(buffer, false, mpegTsPayload.pid, adaptationFieldControl, pesContinuity)
            }
            val size = minOf(data.remaining(), buffer.remaining())
            if (size < buffer.remaining()) { //last packet
              val stuffingSize = buffer.remaining() - data.remaining()
              //override AdaptationFieldControl.PAYLOAD to AdaptationFieldControl.ADAPTATION_PAYLOAD
              val byte = buffer.get(buffer.position() - 1)
              buffer.position(buffer.position() - 1)
              buffer.put((byte.toInt() or (1 shl 5)).toByte())
              writeStuffingBytes(buffer, stuffingSize, true)
            }
            buffer.put(data.array(), data.position(), size)
            data.position(data.position() + size)
            packets.add(buffer.toByteArray())
            pesContinuity = (pesContinuity + 1) and 0xF
            buffer = ByteBuffer.allocate(packetSize)
          }
        }
      }
    }
    return packets
  }

  private fun writeStuffingBytes(byteBuffer: ByteBuffer, size: Int, addHeader: Boolean) {
    when (val fillSize = if (addHeader) size - 2 else size) {
      -1 -> {
        byteBuffer.put((size - 1).toByte()) //this byte is not included in the size
      }
      else -> {
        val bytes = ByteArray(fillSize) { 0xFF.toByte() }
        if (addHeader) {
          byteBuffer.put((size - 1).toByte()) //this byte is not included in the size
          byteBuffer.put(0x00)
        }
        byteBuffer.put(bytes)
      }
    }
  }

  fun reset() {
    pesContinuity = 0
    psiContinuity = 0
  }
}