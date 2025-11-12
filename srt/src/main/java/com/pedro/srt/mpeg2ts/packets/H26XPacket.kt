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

package com.pedro.srt.mpeg2ts.packets

import android.util.Log
import com.pedro.common.frame.MediaFrame
import com.pedro.common.removeInfo
import com.pedro.common.toByteArray
import com.pedro.srt.mpeg2ts.Codec
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.MpegType
import com.pedro.srt.mpeg2ts.Pes
import com.pedro.srt.mpeg2ts.PesType
import com.pedro.srt.mpeg2ts.psi.PsiManager
import com.pedro.srt.srt.packets.data.PacketPosition
import com.pedro.srt.utils.chunkPackets
import com.pedro.srt.utils.startWith
import java.nio.ByteBuffer

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

  private var sps: ByteArray? = null
  private var pps: ByteArray? = null
  private var vps: ByteArray? = null
  private var codec = Codec.AVC
  private var configSend = false

  override suspend fun createAndSendPacket(
    mediaFrame: MediaFrame,
    callback: suspend (List<MpegTsPacket>) -> Unit
  ) {
    val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
    val length = fixedBuffer.remaining()
    if (length < 0) return
    val isKeyFrame = mediaFrame.info.isKeyFrame

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
    val validBuffer = fixHeader(fixedBuffer, isKeyFrame)
    val payload = ByteArray(validBuffer.remaining())
    validBuffer.get(payload, 0, validBuffer.remaining())

    val pes = Pes(psiManager.getVideoPid().toInt(), isKeyFrame, PesType.VIDEO, mediaFrame.info.timestamp, ByteBuffer.wrap(payload))
    val mpeg2tsPackets = mpegTsPacketizer.write(listOf(pes)).chunkPackets(chunkSize).map { buffer ->
      MpegTsPacket(buffer, MpegType.VIDEO, PacketPosition.SINGLE, isKeyFrame)
    }
    if (mpeg2tsPackets.isNotEmpty()) callback(mpeg2tsPackets)
  }

  override fun resetPacket(resetInfo: Boolean) {
    if (resetInfo) {
      vps = null
      sps = null
      pps = null
    }
    configSend = false
  }

  fun setVideoCodec(codec: Codec) {
    this.codec = codec
  }

  fun sendVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    this.sps = getVideoInfoData(sps)
    this.pps = if (pps != null) getVideoInfoData(pps) else null
    this.vps = if (vps != null) getVideoInfoData(vps) else null
  }

  /**
   * Doing video header check sanity.
   *
   * Remove all header video info if necessary, make sure buffer start with prefix and add video info to first keyframe
   */
  private fun fixHeader(byteBuffer: ByteBuffer, isKeyFrame: Boolean): ByteBuffer {
    var noHeaderBuffer = removeHeader(byteBuffer, isKeyFrame) //remove video info header
    val startCodeSize = getStartCodeSize(noHeaderBuffer)
    if (startCodeSize == 0) { //make sure buffer start with prefix
      val bufferWithPrefix = ByteBuffer.allocate(noHeaderBuffer.remaining() + 4)
      bufferWithPrefix.putInt(0x00000001)
      bufferWithPrefix.put(noHeaderBuffer)
      noHeaderBuffer = bufferWithPrefix
    }
    return if (isKeyFrame) { //add video info to first keyframe
      val vps = this.vps ?: byteArrayOf()
      val sps = this.sps ?: byteArrayOf()
      val pps = this.pps ?: byteArrayOf()
      val audSize = if (codec == Codec.AVC) 6 else 7
      val videoHeader = vps.plus(sps).plus(pps)
      val validBuffer = ByteBuffer.allocate(audSize + videoHeader.size + noHeaderBuffer.remaining())
      validBuffer.putInt(0x00000001)
      if (codec == Codec.AVC) {
        validBuffer.put(0x09.toByte())
        validBuffer.put(0xf0.toByte())
      } else {
        validBuffer.put(0x46.toByte())
        validBuffer.put(0x01.toByte())
        validBuffer.put(0x50.toByte())
      }
      validBuffer.put(videoHeader)
      validBuffer.put(noHeaderBuffer.toByteArray())
      validBuffer.rewind()
      configSend = true
      validBuffer
    } else {
      noHeaderBuffer.rewind()
      noHeaderBuffer
    }
  }

  private fun getVideoInfoData(byteBuffer: ByteBuffer): ByteArray {
    byteBuffer.rewind()
    val startCodeSize = getStartCodeSize(byteBuffer)
    return if (startCodeSize == 0) { //make sure video info start with prefix
      val validBuffer = ByteBuffer.allocate(byteBuffer.remaining() + 4)
      validBuffer.putInt(0x00000001)
      validBuffer.put(byteBuffer)
      validBuffer.toByteArray()
    } else {
      byteBuffer.toByteArray()
    }
  }

  private fun removeHeader(byteBuffer: ByteBuffer, isKeyFrame: Boolean): ByteBuffer {
      if (isKeyFrame) {
        var validBuffer = byteBuffer
        val vps = this.vps ?: byteArrayOf()
        val sps = this.sps ?: byteArrayOf()
        val pps = this.pps ?: byteArrayOf()
        if (vps.isNotEmpty()) {
          if (validBuffer.startWith(vps)) {
            validBuffer.position(vps.size)
            validBuffer = validBuffer.slice()
          }
        }
        if (sps.isNotEmpty()) {
          if (validBuffer.startWith(sps)) {
            validBuffer.position(sps.size)
            validBuffer = validBuffer.slice()
          }
        }
        if (pps.isNotEmpty()) {
          if (validBuffer.startWith(pps)) {
            validBuffer.position(pps.size)
            validBuffer = validBuffer.slice()
          }
        }
        validBuffer.rewind()
        return validBuffer
      } else {
        byteBuffer.rewind()
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
}