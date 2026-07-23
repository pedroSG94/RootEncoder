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

package com.pedro.rtsp.rtp.packets

import com.pedro.common.frame.MediaFrame
import com.pedro.common.removeInfo
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import kotlin.random.Random

/**
 * Created by pedro on 23/07/26.
 *
 * RFC 7741
 *
 * Descriptor Header
 *
 *  0 1 2 3 4 5 6 7
 * +-+-+-+-+-+-+-+-+
 * |X|R|N|S|R| PID |
 * +-+-+-+-+-+-+-+-+
 */
class Vp8Packet(track: Int): BasePacket(
  RtpConstants.clockVideoFrequency,
  RtpConstants.payloadType + track
) {

  private val headerSize = 4
  private var pictureId = Random.nextInt(0x7FFF)

  init {
    channelIdentifier = track
  }

  override suspend fun createAndSendPacket(
    mediaFrame: MediaFrame,
    callback: suspend (List<RtpFrame>) -> Unit
  ) {
    val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
    val ts = mediaFrame.info.timestamp * 1000
    pictureId = (pictureId + 1) and 0x7FFF

    val size = fixedBuffer.remaining()
    var sum = 0
    val frames = mutableListOf<RtpFrame>()
    while (sum < size) {
      val isFirstPacket = sum == 0
      val length = if (size - sum > maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - headerSize - encryptSize()) {
        maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - headerSize - encryptSize()
      } else {
        fixedBuffer.remaining()
      }
      val buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + headerSize + encryptSize())
      val rtpTs = updateTimeStamp(buffer, ts)
      fixedBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + headerSize, length)
      sum += length
      if (sum >= size) markPacket(buffer) //mark end frame
      buffer[RtpConstants.RTP_HEADER_LENGTH] = generateDescriptorHeader(isFirstPacket)
      buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = 0x80.toByte() //PID enabled and the rest disabled
      buffer[RtpConstants.RTP_HEADER_LENGTH + 2] = (0x80 or (pictureId shr 8)).toByte()
      buffer[RtpConstants.RTP_HEADER_LENGTH + 3] = pictureId.toByte()
      updateSeq(buffer)
      encryptPacket(buffer)
      val rtpFrame = RtpFrame(buffer, rtpTs, buffer.size, channelIdentifier)
      frames.add(rtpFrame)
    }
    if (frames.isNotEmpty()) callback(frames)
  }

  override fun reset() {
    super.reset()
    pictureId = Random.nextInt(0x7FFF)
  }

  private fun generateDescriptorHeader(isFirstPacket: Boolean): Byte {
    return ((0x01 shl 7) or //X
        (0x00 shl 6) or //R
        (0x00 shl 5)  or //N
        ((if (isFirstPacket) 0x01 else 0x00) shl 4) or //S
        (0x00 shl 3) or //R
        0x00).toByte() //PID
  }
}