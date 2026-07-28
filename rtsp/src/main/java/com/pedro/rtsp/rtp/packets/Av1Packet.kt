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

import com.pedro.common.av1.Av1Parser
import com.pedro.common.av1.Obu
import com.pedro.common.av1.ObuType
import com.pedro.common.frame.MediaFrame
import com.pedro.common.removeInfo
import com.pedro.common.toByteArray
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import java.nio.ByteBuffer

/**
 * Created by pedro on 28/11/18.
 *
 * AV1 has no RFC specification so we are using the official implementation from aomediacodec:
 * https://aomediacodec.github.io/av1-rtp-spec/
 *
 * AV1 Aggregation Header
 *  0 1 2 3 4 5 6 7
 * +-+-+-+-+-+-+-+-+
 * |Z|Y| W |N|-|-|-|
 * +-+-+-+-+-+-+-+-+
 */
class Av1Packet(track: Int): BasePacket(
  RtpConstants.clockVideoFrequency,
  RtpConstants.payloadType + track
) {

  private val parser = Av1Parser()
  private var sequenceHeader: Obu? = null

  init {
    channelIdentifier = track
  }

  fun sendVideoInfo(sequenceHeader: ByteBuffer) {
    this.sequenceHeader = parser.getObus(sequenceHeader.toByteArray()).firstOrNull {
      parser.getObuType(it.header[0]) == ObuType.SEQUENCE_HEADER
    }
  }

  override suspend fun createAndSendPacket(
    mediaFrame: MediaFrame,
    callback: suspend (List<RtpFrame>) -> Unit
  ) {
    val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
    val ts = mediaFrame.info.timestamp * 1000L
    val obuList = parser.getObus(fixedBuffer.toByteArray()).filterNot {
      val type = parser.getObuType(it.header[0])
      type == ObuType.TEMPORAL_DELIMITER || type == ObuType.TILE_LIST
    }.toMutableList()
    if (obuList.isEmpty()) return
    if (mediaFrame.info.isKeyFrame) {
      sequenceHeader?.let { sequenceHeader ->
        if (obuList.none { parser.getObuType(it.header[0]) == ObuType.SEQUENCE_HEADER }) {
          obuList.add(0, sequenceHeader)
        }
      }
    }
    val frames = mutableListOf<RtpFrame>()
    val maxPayload = maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 1 - encryptSize()
    obuList.forEachIndexed { index, obuData ->
      val obu = ByteBuffer.wrap(obuData.getFullDataWithoutSize())
      val size = obu.remaining()
      var sum = 0
      while (sum < size) {
        val firstObuPacket = sum == 0
        val isFirstPacket = firstObuPacket && index == 0
        var lastObuPacket = false

        var length = minOf(obu.remaining(), maxPayload - 1)
        while (parser.leb128Size(length) + length > maxPayload) length--
        val prefixSize = parser.leb128Size(length)

        val buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 1 + prefixSize + encryptSize())
        val rtpTs = updateTimeStamp(buffer, ts)
        obu.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 1 + prefixSize, length)
        sum += length
        // Last packet before next Obu
        if (sum >= size) {
          lastObuPacket = true
          if (index == obuList.size - 1) markPacket(buffer) //mark end frame
        }
        buffer[RtpConstants.RTP_HEADER_LENGTH] = generateAv1AggregationHeader(mediaFrame.info.isKeyFrame, firstObuPacket, lastObuPacket, isFirstPacket)
        parser.writeLeb128(length.toLong()).copyInto(buffer, RtpConstants.RTP_HEADER_LENGTH + 1)
        updateSeq(buffer)
        encryptPacket(buffer)
        val rtpFrame = RtpFrame(buffer, rtpTs, buffer.size, channelIdentifier)
        frames.add(rtpFrame)
      }
    }
    if (frames.isNotEmpty()) callback(frames)
  }

  override fun reset() {
    super.reset()
  }

  private fun generateAv1AggregationHeader(isKeyFrame: Boolean, firstObuPacket: Boolean, lastObuPacket: Boolean, isFirstPacket: Boolean): Byte {
    val z = if (firstObuPacket) 0 else 1
    val y = if (lastObuPacket) 0 else 1
    val w = 0
    val n = if (isKeyFrame && isFirstPacket) 1 else 0
    return ((z shl 7) or (y shl 6) or (w shl 4) or (n shl 3) or 0).toByte()
  }
}