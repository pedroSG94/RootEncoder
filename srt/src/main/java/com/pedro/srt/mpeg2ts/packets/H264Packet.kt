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

package com.pedro.srt.mpeg2ts.packets

import android.media.MediaCodec
import android.util.Log
import com.pedro.srt.mpeg2ts.AdaptationField
import com.pedro.srt.mpeg2ts.AdaptationFieldControl
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.PES
import com.pedro.srt.mpeg2ts.PesType
import com.pedro.srt.mpeg2ts.psi.PSIManager
import java.nio.ByteBuffer

/**
 * Created by pedro on 20/8/23.
 */
class H264Packet(
  sizeLimit: Int,
  psiManager: PSIManager
): BasePacket(psiManager) {

  private val TAG = "H264Packet"
  private var sps: ByteArray? = null
  private var pps: ByteArray? = null
  private val realSize = getRealSize(sizeLimit)

  override fun createAndSendPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (MpegTsPacket) -> Unit
  ) {
    if (!configSend) {
      sendConfig(realSize, callback)
      configSend = true
    }
    val sps = this.sps
    val pps = this.pps
    if (sps == null || pps == null) {
      Log.e(TAG, "waiting for a valid sps and pps")
      return
    }
    checkSendTable(realSize, callback)
    byteBuffer.rewind()
    val avcData = removeHeader(byteBuffer)
    val length = avcData.remaining()
    if (length < 0) return
    val buffer = ByteBuffer.allocate(realSize)

    val headerSize = if (info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
      6 + sps.size + pps.size
    } else 0

    val payloadBuffer = ByteBuffer.allocate(length + headerSize)
    if (headerSize > 0) {
      writeAUD(payloadBuffer)
      payloadBuffer.put(sps)
      payloadBuffer.put(pps)
    }
    val payload = payloadBuffer.array()
    avcData.get(payload, headerSize, length)
    var sum = 0
    var counter = 0
    val pid = psiManager.getVideoPid()
    while (sum < length) {
      val adaptationFieldControl = AdaptationFieldControl.ADAPTATION_PAYLOAD
      //num of pes/psi in payload
      val adaptationField = AdaptationField(
        discontinuityIndicator = false,
        randomAccessIndicator = headerSize > 0,
        pcr = System.nanoTime() / 1000
      )
      writeTsHeader(buffer, sum == 0, pid, adaptationFieldControl, counter.toByte(), adaptationField)
      val pesSize = getTSPacketSize() - getTSPacketHeaderSize() - adaptationField.getSize()
      val payloadSize = pesSize - PES.HeaderLength
      val data = payload.sliceArray(sum until minOf(sum + payloadSize, payload.size))
      val pes = PES(PesType.VIDEO, payload.size, data)
      pes.write(buffer, info.presentationTimeUs, pesSize, sum == 0)
      sum += data.size
      counter++
      if (!buffer.hasRemaining()) {
        val array = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(array)
        buffer.rewind()
        callback(MpegTsPacket(array, true))
      }
    }

    val array = ByteArray(buffer.position())
    buffer.flip()
    buffer.get(array)
    callback(MpegTsPacket(array, true))
  }

  fun sendVideoInfo(sps: ByteBuffer, pps: ByteBuffer) {
    this.sps = sps.array()
    this.pps = pps.array()
  }

  private fun writeAUD(buffer: ByteBuffer) {
    buffer.putInt(0x00000001)
    buffer.put(0x09)
    buffer.put(0xF0.toByte())
  }

  private fun removeHeader(byteBuffer: ByteBuffer, size: Int = -1): ByteBuffer {
    val position = if (size == -1) getStartCodeSize(byteBuffer) else size
    byteBuffer.position(position)
    return byteBuffer.slice()
  }

  private fun getHeaderSize(byteBuffer: ByteBuffer): Int {
    if (byteBuffer.remaining() < 4) return 0

    val sps = this.sps
    val pps = this.pps
    if (sps != null && pps != null) {
      val startCodeSize = getStartCodeSize(byteBuffer)
      if (startCodeSize == 0) return 0
      val startCode = ByteArray(startCodeSize) { 0x00 }
      startCode[startCodeSize - 1] = 0x01
      val avcHeader = startCode.plus(sps).plus(startCode).plus(pps).plus(startCode)
      if (byteBuffer.remaining() < avcHeader.size) return startCodeSize

      val possibleAvcHeader = ByteArray(avcHeader.size)
      byteBuffer.get(possibleAvcHeader, 0, possibleAvcHeader.size)
      return if (avcHeader.contentEquals(possibleAvcHeader)) {
        avcHeader.size
      } else {
        startCodeSize
      }
    }
    return 0
  }

  private fun getStartCodeSize(byteBuffer: ByteBuffer): Int {
    var startCodeSize = 0
    if (byteBuffer.get(0).toInt() == 0x00 && byteBuffer.get(1).toInt() == 0x00
      && byteBuffer.get(2).toInt() == 0x00 && byteBuffer.get(3).toInt() == 0x01) {
      //match 00 00 00 01
      startCodeSize = 4
    } else if (byteBuffer.get(0).toInt() == 0x00 && byteBuffer.get(1).toInt() == 0x00
      && byteBuffer.get(2).toInt() == 0x01) {
      //match 00 00 01
      startCodeSize = 3
    }
    return startCodeSize
  }
}