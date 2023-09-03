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
import android.os.Build
import android.util.Log
import com.pedro.srt.mpeg2ts.Codec
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegType
import com.pedro.srt.mpeg2ts.Pes
import com.pedro.srt.mpeg2ts.PesType
import com.pedro.srt.mpeg2ts.psi.PsiManager
import com.pedro.srt.srt.packets.data.PacketPosition
import com.pedro.srt.utils.toByteArray
import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * Created by pedro on 20/8/23.
 *
 * Used for H264/H265
 */
class H26XPacket(
  limitSize: Int,
  psiManager: PsiManager,
): BasePacket(psiManager, limitSize) {

  private val TAG = "H26XPacket"

  private var sps: ByteBuffer? = null
  private var pps: ByteBuffer? = null
  private var vps: ByteBuffer? = null
  private var codec = Codec.AVC

  override fun createAndSendPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (MpegTsPacket) -> Unit
  ) {
    val length = info.size
    if (length < 0) return
    val isKeyFrame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME
    } else {
      info.flags == MediaCodec.BUFFER_FLAG_SYNC_FRAME
    }

    if (codec == Codec.HEVC) {
      val sps = this.sps
      val pps = this.pps
      val vps = this.vps
      if (sps == null || pps == null || vps == null) {
        Log.e(TAG, "waiting for a valid sps, pps and vps")
        return
      }
    } else {
      val sps = this.sps
      val pps = this.pps
      if (sps == null || pps == null) {
        Log.e(TAG, "waiting for a valid sps and pps")
        return
      }
    }

    byteBuffer.rewind()
    val validBuffer = fixHeader(byteBuffer, isKeyFrame)
    val payload = ByteArray(validBuffer.remaining())
    validBuffer.get(payload, 0, validBuffer.remaining())

    val pes = Pes(psiManager.getVideoPid().toInt(), isKeyFrame, PesType.VIDEO, info.presentationTimeUs, ByteBuffer.wrap(payload))
    val mpeg2tsPackets = mpegTsPacketizer.write(listOf(pes))
    val chunked = mpeg2tsPackets.chunked(chunkSize)
    chunked.forEachIndexed { index, chunks ->
      val size = chunks.sumOf { it.size }
      val buffer = ByteBuffer.allocate(size)
      chunks.forEach {
        buffer.put(it)
      }
      val packetPosition = if (index == 0 && chunked.size == 1) {
        PacketPosition.SINGLE
      } else if (index == 0) {
        PacketPosition.FIRST
      } else if (index == chunked.size - 1) {
        PacketPosition.LAST
      } else {
        PacketPosition.MIDDLE
      }
      callback(MpegTsPacket(buffer.array(), MpegType.VIDEO, packetPosition))
    }
  }

  override fun resetPacket() {
    vps = null
    sps = null
    pps = null
  }

  fun setVideoCodec(codec: Codec) {
    this.codec = codec
  }

  fun sendVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
    this.sps = sps
    this.pps = pps
    this.vps = vps
  }

  /**
   * check if header contain AUD and sps/pps/vps if keyframe, if not fix it
   */
  private fun fixHeader(byteBuffer: ByteBuffer, isKeyFrame: Boolean): ByteBuffer {
    val startCodeSize = getStartCodeSize(byteBuffer)
    if (startCodeSize == 0) { //AUD not found fixing buffer
      if (!isKeyFrame) {
        val validBuffer = ByteBuffer.allocate(byteBuffer.remaining() + 4)
        validBuffer.putInt(0x00000001)
        validBuffer.put(byteBuffer.array())
        validBuffer.rewind()
        return validBuffer
      } else {
        val vps = this.sps?.array() ?: byteArrayOf()
        val sps = this.sps?.array() ?: byteArrayOf()
        val pps = this.sps?.array() ?: byteArrayOf()
        val keyExtraSize = (if (codec == Codec.HEVC) 12 else 8) + vps.size + sps.size + pps.size
        val validBuffer = ByteBuffer.allocate(byteBuffer.remaining() + keyExtraSize)
        val startCode = byteArrayOf(0x00, 0x00, 0x00, 0x01)
        val videoHeader = if (codec == Codec.HEVC) {
          startCode.plus(vps).plus(startCode).plus(sps).plus(startCode).plus(pps).plus(startCode)
        } else {
          startCode.plus(sps).plus(startCode).plus(pps).plus(startCode)
        }
        validBuffer.put(videoHeader)
        validBuffer.put(byteBuffer.array())
        validBuffer.rewind()
        return validBuffer
      }
    } else {
      return byteBuffer
    }
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

  private fun getType(byteBuffer: ByteBuffer): Int {
    val startCodeSize = getStartCodeSize(byteBuffer)
    return if (codec == Codec.HEVC) {
      byteBuffer.get(startCodeSize).toInt().shr(1 and 0x3f)
    } else {
      (byteBuffer.get(startCodeSize) and 0x1F).toInt()
    }
  }
}