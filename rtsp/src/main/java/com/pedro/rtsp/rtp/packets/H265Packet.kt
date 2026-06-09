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

import android.util.Log
import com.pedro.common.VideoCodec
import com.pedro.common.frame.MediaFrame
import com.pedro.common.nal.NalReader
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

  private var videoInfo: Set<ByteBuffer>? = null
  private val header = ByteArray(3)

  init {
    channelIdentifier = RtpConstants.trackVideo
  }

  fun sendVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer) {
    videoInfo = setOf(sps, pps, vps)
  }

  override suspend fun createAndSendPacket(
    mediaFrame: MediaFrame,
    callback: suspend (List<RtpFrame>) -> Unit
  ) {
    val videoInfo = this.videoInfo
    if (videoInfo == null) {
      Log.e(TAG, "waiting for a valid sps, pps and vps")
      return
    }
    val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
    // We read a NAL units from ByteBuffer and we send them
    // NAL units are preceded with 0x00000001
    val nals = NalReader.extractNals(fixedBuffer, VideoCodec.H265, true)
    if (nals.isEmpty()) return

    val ts = mediaFrame.info.timestamp * 1000L
    val nalType = nals[0].get()
    val nalType2 = nals[0].get()
    val naluLength = nals.sumOf { it.remaining() }
    val type: Int = nalType.toInt().shr(1 and 0x3f)
    val frames = mutableListOf<RtpFrame>()
    // Small NAL unit => Single NAL unit
    if (nals.size == 1 && naluLength <= maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2) {
      val buffer = getBuffer(naluLength + RtpConstants.RTP_HEADER_LENGTH + 2)
      //Set PayloadHdr (exact copy of nal unit header)
      buffer[RtpConstants.RTP_HEADER_LENGTH] = nalType
      buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = nalType2
      nals[0].get(buffer, RtpConstants.RTP_HEADER_LENGTH + 2, naluLength)
      val rtpTs = updateTimeStamp(buffer, ts)
      markPacket(buffer) //mark end frame
      updateSeq(buffer)
      val rtpFrame = RtpFrame(buffer, rtpTs, buffer.size, channelIdentifier)
      frames.add(rtpFrame)
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
      nals.forEachIndexed { index, data ->
        var sum = 0
        val nalSize = data.remaining()
        while (sum < nalSize) {
          val length = if (nalSize - sum > maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 3) {
            maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 3
          } else {
            data.remaining()
          }
          val buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 3)
          buffer[RtpConstants.RTP_HEADER_LENGTH] = header[0]
          buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = header[1]
          // Switch start bit
          buffer[RtpConstants.RTP_HEADER_LENGTH + 2] = if (sum > 0) header[2] and 0x7F else header[2]
          val rtpTs = updateTimeStamp(buffer, ts)
          data.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 3, length)
          sum += length
          // Last packet before next NAL
          if (sum >= nalSize) {
            // End bit on
            buffer[RtpConstants.RTP_HEADER_LENGTH + 2] = buffer[RtpConstants.RTP_HEADER_LENGTH + 2].plus(0x40).toByte()
            if (index == nals.size - 1) markPacket(buffer) //mark end frame
          }
          updateSeq(buffer)
          val rtpFrame = RtpFrame(buffer, rtpTs, buffer.size, channelIdentifier)
          frames.add(rtpFrame)
        }
      }
    }
    if (frames.isNotEmpty()) callback(frames)
  }

  override fun reset() {
    super.reset()
  }
}