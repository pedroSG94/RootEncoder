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
import com.pedro.rtmp.flv.video.FourCCPacketType
import com.pedro.rtmp.flv.video.VideoDataType
import com.pedro.rtmp.flv.video.VideoFormat
import com.pedro.rtmp.flv.video.VideoNalType
import com.pedro.rtmp.flv.video.config.VideoSpecificConfigHEVC
import java.nio.ByteBuffer

/**
 * Created by pedro on 14/08/23.
 *
 */
class H265Packet: BasePacket() {

  private val TAG = "H265Packet"

  private val header = ByteArray(8)
  private val naluSize = 4
  //first time we need send video config
  private var configSend = false

  private var sps: ByteArray? = null
  private var pps: ByteArray? = null
  private var vps: ByteArray? = null

  fun sendVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer) {
    val mSps = removeHeader(sps)
    val mPps = removeHeader(pps)
    val mVps = removeHeader(vps)

    val spsBytes = ByteArray(mSps.remaining())
    val ppsBytes = ByteArray(mPps.remaining())
    val vpsBytes = ByteArray(mVps.remaining())
    mSps.get(spsBytes, 0, spsBytes.size)
    mPps.get(ppsBytes, 0, ppsBytes.size)
    mVps.get(vpsBytes, 0, vpsBytes.size)

    this.sps = spsBytes
    this.pps = ppsBytes
    this.vps = vpsBytes
  }

  override fun createFlvPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (FlvPacket) -> Unit
  ) {
    val fixedBuffer = byteBuffer.removeInfo(info)
    val ts = info.presentationTimeUs / 1000
    //header is 8 bytes length:
    //mark first byte as extended header (0b10000000)
    //4 bits data type, 4 bits packet type
    //4 bytes extended codec type (in this case hevc)
    //3 bytes CompositionTime, the cts.
    val codec = VideoFormat.HEVC.value // { "h", "v", "c", "1" }
    header[1] = (codec shr 24).toByte()
    header[2] = (codec shr 16).toByte()
    header[3] = (codec shr 8).toByte()
    header[4] = codec.toByte()
    val cts = 0
    val ctsLength = 3
    header[5] = (cts shr 16).toByte()
    header[6] = (cts shr 8).toByte()
    header[7] = cts.toByte()

    var buffer: ByteArray
    if (!configSend) {
      //avoid send cts on sequence start
      header[0] = (0b10000000 or (VideoDataType.KEYFRAME.value shl 4) or FourCCPacketType.SEQUENCE_START.value).toByte()
      val sps = this.sps
      val pps = this.pps
      val vps = this.vps
      if (sps != null && pps != null && vps != null) {
        val config = VideoSpecificConfigHEVC(sps, pps, vps)
        buffer = ByteArray(config.size + header.size - ctsLength)
        config.write(buffer, header.size - ctsLength)
      } else {
        Log.e(TAG, "waiting for a valid sps and pps")
        return
      }

      System.arraycopy(header, 0, buffer, 0, header.size - ctsLength)
      callback(FlvPacket(buffer, ts, buffer.size, FlvType.VIDEO))
      configSend = true
    }
    val headerSize = getHeaderSize(fixedBuffer)
    if (headerSize == 0) return //invalid buffer or waiting for sps/pps
    fixedBuffer.rewind()
    val validBuffer = removeHeader(fixedBuffer, headerSize)
    val size = validBuffer.remaining()
    buffer = ByteArray(header.size + size + naluSize)

    val type: Int = validBuffer.get(0).toInt().shr(1 and 0x3f)
    var nalType = VideoDataType.INTER_FRAME.value
    if (type == VideoNalType.IDR_N_LP.value || type == VideoNalType.IDR_W_DLP.value || info.isKeyframe()) {
      nalType = VideoDataType.KEYFRAME.value
    } else if (type == VideoNalType.HEVC_VPS.value || type == VideoNalType.HEVC_SPS.value || type == VideoNalType.HEVC_PPS.value) {
      // we don't need send it because we already do it in video config
      return
    }
    header[0] = (0b10000000 or (nalType shl 4) or FourCCPacketType.CODED_FRAMES.value).toByte()
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
    val vps = this.vps
    if (sps != null && pps != null && vps != null) {
      val startCodeSize = getStartCodeSize(byteBuffer)
      if (startCodeSize == 0) return 0
      val startCode = ByteArray(startCodeSize) { 0x00 }
      startCode[startCodeSize - 1] = 0x01
      val avcHeader = startCode.plus(vps).plus(startCode).plus(sps).plus(startCode).plus(pps).plus(startCode)
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
      vps = null
    }
    configSend = false
  }
}