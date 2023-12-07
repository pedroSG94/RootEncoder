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
import com.pedro.common.removeInfo
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.getVideoStartCodeSize
import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * Created by pedro on 28/11/18.
 *
 * RFC 7798.
 */
class H265Packet: BasePacket(
  RtpConstants.clockVideoFrequency,
  RtpConstants.payloadType + RtpConstants.trackVideo
) {

  init {
    channelIdentifier = RtpConstants.trackVideo
  }

  override fun createAndSendPacket(
    byteBuffer: ByteBuffer,
    bufferInfo: MediaCodec.BufferInfo,
    callback: (RtpFrame) -> Unit
  ) {
    val fixedBuffer = byteBuffer.removeInfo(bufferInfo)
    // We read a NAL units from ByteBuffer and we send them
    // NAL units are preceded with 0x00000001
    val header = ByteArray(fixedBuffer.getVideoStartCodeSize() + 2)
    if (header.size == 2) return //invalid buffer or waiting for sps/pps/vps
    fixedBuffer.get(header, 0, header.size)
    val ts = bufferInfo.presentationTimeUs * 1000L
    val naluLength = fixedBuffer.remaining()
    val type: Int = header[header.size - 2].toInt().shr(1 and 0x3f)
    // Small NAL unit => Single NAL unit
    if (naluLength <= maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2) {
      val buffer = getBuffer(naluLength + RtpConstants.RTP_HEADER_LENGTH + 2)
      //Set PayloadHdr (exact copy of nal unit header)
      buffer[RtpConstants.RTP_HEADER_LENGTH] = header[header.size - 2]
      buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = header[header.size - 1]
      fixedBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 2, naluLength)
      val rtpTs = updateTimeStamp(buffer, ts)
      markPacket(buffer) //mark end frame
      updateSeq(buffer)
      val rtpFrame = RtpFrame(buffer, rtpTs, buffer.size, rtpPort, rtcpPort, channelIdentifier)
      callback(rtpFrame)
    } else {
      //Set PayloadHdr (16bit type=49)
      header[0] = (49 shl 1).toByte()
      header[1] = 1
      // Set FU header
      //   +---------------+
      //   |0|1|2|3|4|5|6|7|
      //   +-+-+-+-+-+-+-+-+
      //   |S|E|  FuType   |
      //   +---------------+
      header[2] = type.toByte() // FU header type
      header[2] = header[2].plus(0x80).toByte() // Start bit
      var sum = 0
      while (sum < naluLength) {
        val length = if (naluLength - sum > maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 3) {
          maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 3
        } else {
          fixedBuffer.remaining()
        }
        val buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 3)
        buffer[RtpConstants.RTP_HEADER_LENGTH] = header[0]
        buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = header[1]
        buffer[RtpConstants.RTP_HEADER_LENGTH + 2] = header[2]
        val rtpTs = updateTimeStamp(buffer, ts)
        fixedBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 3, length)
        sum += length
        // Last packet before next NAL
        if (sum >= naluLength) {
          // End bit on
          buffer[RtpConstants.RTP_HEADER_LENGTH + 2] = buffer[RtpConstants.RTP_HEADER_LENGTH + 2].plus(0x40).toByte()
          markPacket(buffer) //mark end frame
        }
        updateSeq(buffer)
        val rtpFrame = RtpFrame(buffer, rtpTs, buffer.size, rtpPort, rtcpPort, channelIdentifier)
        callback(rtpFrame)
        // Switch start bit
        header[2] = header[2] and 0x7F
      }
    }
  }

  override fun reset() {
    super.reset()
  }
}