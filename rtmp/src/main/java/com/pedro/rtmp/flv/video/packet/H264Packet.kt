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

package com.pedro.rtmp.flv.video.packet

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.isKeyframe
import com.pedro.common.removeInfo
import com.pedro.rtmp.flv.BasePacket
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import com.pedro.rtmp.flv.video.VideoDataType
import com.pedro.rtmp.flv.video.VideoFormat
import com.pedro.rtmp.flv.video.VideoNalType
import com.pedro.rtmp.flv.video.config.VideoSpecificConfigAVC
import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * Created by pedro on 8/04/21.
 *
 * ISO 14496-15
 */
class H264Packet: BasePacket() {

  private val TAG = "H264Packet"

  private val header = ByteArray(5)
  private val naluSize = 4
  //first time we need send video config
  private var configSend = false

  private var sps: ByteArray? = null
  private var pps: ByteArray? = null

  enum class Type(val value: Byte) {
    SEQUENCE(0x00), NALU(0x01), EO_SEQ(0x02)
  }

  fun sendVideoInfo(sps: ByteBuffer, pps: ByteBuffer) {
    val mSps = removeHeader(sps)
    val mPps = removeHeader(pps)

    val spsBytes = ByteArray(mSps.remaining())
    val ppsBytes = ByteArray(mPps.remaining())
    mSps.get(spsBytes, 0, spsBytes.size)
    mPps.get(ppsBytes, 0, ppsBytes.size)

    this.sps = spsBytes
    this.pps = ppsBytes
  }

  override fun createFlvPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (FlvPacket) -> Unit
  ) {
    val fixedBuffer = byteBuffer.removeInfo(info)
    val ts = info.presentationTimeUs / 1000
    //header is 5 bytes length:
    //4 bits FrameType, 4 bits CodecID
    //1 byte AVCPacketType
    //3 bytes CompositionTime, the cts.
    val cts = 0
    header[2] = (cts shr 16).toByte()
    header[3] = (cts shr 8).toByte()
    header[4] = cts.toByte()

    var buffer: ByteArray
    if (!configSend) {
      header[0] = ((VideoDataType.KEYFRAME.value shl 4) or VideoFormat.AVC.value).toByte()
      header[1] = Type.SEQUENCE.value

      val sps = this.sps
      val pps = this.pps
      if (sps != null && pps != null) {
        val config = VideoSpecificConfigAVC(sps, pps)
        buffer = ByteArray(config.size + header.size)
        config.write(buffer, header.size)
      } else {
        Log.e(TAG, "waiting for a valid sps and pps")
        return
      }

      System.arraycopy(header, 0, buffer, 0, header.size)
      callback(FlvPacket(buffer, ts, buffer.size, FlvType.VIDEO))
      configSend = true
    }
    val headerSize = getHeaderSize(fixedBuffer)
    if (headerSize == 0) return //invalid buffer or waiting for sps/pps
    fixedBuffer.rewind()
    val validBuffer = removeHeader(fixedBuffer, headerSize)
    val size = validBuffer.remaining()
    buffer = ByteArray(header.size + size + naluSize)

    val type: Int = (validBuffer.get(0) and 0x1F).toInt()
    var nalType = VideoDataType.INTER_FRAME.value
    if (type == VideoNalType.IDR.value || info.isKeyframe()) {
      nalType = VideoDataType.KEYFRAME.value
    } else if (type == VideoNalType.SPS.value || type == VideoNalType.PPS.value) {
      // we don't need send it because we already do it in video config
      return
    }
    header[0] = ((nalType shl 4) or VideoFormat.AVC.value).toByte()
    header[1] = Type.NALU.value
    writeNaluSize(buffer, header.size, size)
    validBuffer.get(buffer, header.size + naluSize, size)

    System.arraycopy(header, 0, buffer, 0, header.size)
    callback(FlvPacket(buffer, ts, buffer.size, FlvType.VIDEO))
  }

  //naluSize = UInt32
  private fun writeNaluSize(buffer: ByteArray, offset: Int, size: Int) {
    buffer[offset] = (size ushr 24).toByte()
    buffer[offset + 1] = (size ushr 16).toByte()
    buffer[offset + 2] = (size ushr 8).toByte()
    buffer[offset + 3] = size.toByte()
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

  override fun reset(resetInfo: Boolean) {
    if (resetInfo) {
      sps = null
      pps = null
    }
    configSend = false
  }
}