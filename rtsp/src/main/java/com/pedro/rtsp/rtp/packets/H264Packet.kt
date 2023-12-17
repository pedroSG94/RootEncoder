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

package com.pedro.rtsp.rtp.packets

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.isKeyframe
import com.pedro.common.removeInfo
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.getVideoStartCodeSize
import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * Created by pedro on 27/11/18.
 *
 * RFC 3984
 */
class H264Packet(
  sps: ByteArray,
  pps: ByteArray
): BasePacket(RtpConstants.clockVideoFrequency,
  RtpConstants.payloadType + RtpConstants.trackVideo
) {

  private var stapA: ByteArray? = null
  private var sendKeyFrame = false
  private var sps: ByteArray? = null
  private var pps: ByteArray? = null

  init {
    channelIdentifier = RtpConstants.trackVideo
    setSpsPps(sps, pps)
  }

  override fun createAndSendPacket(
    byteBuffer: ByteBuffer,
    bufferInfo: MediaCodec.BufferInfo,
    callback: (RtpFrame) -> Unit
  ) {
    val fixedBuffer = byteBuffer.removeInfo(bufferInfo)
    // We read a NAL units from ByteBuffer and we send them
    // NAL units are preceded with 0x00000001
    val header = ByteArray(getHeaderSize(fixedBuffer) + 1)
    if (header.size == 1) return //invalid buffer or waiting for sps/pps
    fixedBuffer.rewind()
    fixedBuffer.get(header, 0, header.size)
    val ts = bufferInfo.presentationTimeUs * 1000L
    val naluLength = fixedBuffer.remaining()
    val type: Int = (header[header.size - 1] and 0x1F).toInt()
    if (type == RtpConstants.IDR || bufferInfo.isKeyframe()) {
      stapA?.let {
        val buffer = getBuffer(it.size + RtpConstants.RTP_HEADER_LENGTH)
        val rtpTs = updateTimeStamp(buffer, ts)
        markPacket(buffer) //mark end frame
        System.arraycopy(it, 0, buffer, RtpConstants.RTP_HEADER_LENGTH, it.size)
        updateSeq(buffer)
        val rtpFrame = RtpFrame(buffer, rtpTs, it.size + RtpConstants.RTP_HEADER_LENGTH, rtpPort, rtcpPort, channelIdentifier)
        callback(rtpFrame)
        sendKeyFrame = true
      } ?: run {
        Log.i(TAG, "can't create key frame because setSpsPps was not called")
      }
    }
    if (sendKeyFrame) {
      // Small NAL unit => Single NAL unit
      if (naluLength <= maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 1) {
        val buffer = getBuffer(naluLength + RtpConstants.RTP_HEADER_LENGTH + 1)
        buffer[RtpConstants.RTP_HEADER_LENGTH] = header[header.size - 1]
        fixedBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 1, naluLength)
        val rtpTs = updateTimeStamp(buffer, ts)
        markPacket(buffer) //mark end frame
        updateSeq(buffer)
        val rtpFrame = RtpFrame(buffer, rtpTs, buffer.size, rtpPort, rtcpPort, channelIdentifier)
        callback(rtpFrame)
      } else {
        // Set FU-A header
        header[1] = header[header.size - 1] and 0x1F // FU header type
        header[1] = header[1].plus(0x80).toByte()  // set start bit to 1
        // Set FU-A indicator
        header[0] = header[header.size - 1] and 0x60 and 0xFF.toByte() // FU indicator NRI
        header[0] = header[0].plus(28).toByte()
        var sum = 0
        while (sum < naluLength) {
          val length = if (naluLength - sum > maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2) {
            maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2
          } else {
            fixedBuffer.remaining()
          }
          val buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 2)
          buffer[RtpConstants.RTP_HEADER_LENGTH] = header[0]
          buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = header[1]
          val rtpTs = updateTimeStamp(buffer, ts)
          fixedBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 2, length)
          sum += length
          // Last packet before next NAL
          if (sum >= naluLength) {
            // End bit on
            buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = buffer[RtpConstants.RTP_HEADER_LENGTH + 1].plus(0x40).toByte()
            markPacket(buffer) //mark end frame
          }
          updateSeq(buffer)
          val rtpFrame = RtpFrame(buffer, rtpTs, buffer.size, rtpPort, rtcpPort, channelIdentifier)
          callback(rtpFrame)
          // Switch start bit
          header[1] = header[1] and 0x7F
        }
      }
    } else {
      Log.i(TAG, "waiting for keyframe")
    }
  }

  private fun setSpsPps(sps: ByteArray, pps: ByteArray) {
    this.sps = sps
    this.pps = pps
    stapA = ByteArray(sps.size + pps.size + 5)
    stapA?.let {
      // STAP-A NAL header is 24
      it[0] = 24

      // Write NALU 1 size into the array (NALU 1 is the SPS).
      it[1] = (sps.size shr 8).toByte()
      it[2] = (sps.size and 0xFF).toByte()

      // Write NALU 2 size into the array (NALU 2 is the PPS).
      it[sps.size + 3] = (pps.size shr 8).toByte()
      it[sps.size + 4] = (pps.size and 0xFF).toByte()

      // Write NALU 1 into the array, then write NALU 2 into the array.
      System.arraycopy(sps, 0, it, 3, sps.size)
      System.arraycopy(pps, 0, it, 5 + sps.size, pps.size)
    }
  }

  private fun getHeaderSize(byteBuffer: ByteBuffer): Int {
    if (byteBuffer.remaining() < 4) return 0

    val sps = this.sps
    val pps = this.pps
    if (sps != null && pps != null) {
      val startCodeSize = byteBuffer.getVideoStartCodeSize()
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

  override fun reset() {
    super.reset()
    sendKeyFrame = false
  }
}