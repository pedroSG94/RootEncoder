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
import com.pedro.common.av1.Av1Parser
import com.pedro.common.av1.ObuType
import com.pedro.common.isKeyframe
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
class Av1Packet: BasePacket(
  RtpConstants.clockVideoFrequency,
  RtpConstants.payloadType + RtpConstants.trackVideo
) {

  private val parser = Av1Parser()

  init {
    channelIdentifier = RtpConstants.trackVideo
  }

  override fun createAndSendPacket(
    byteBuffer: ByteBuffer,
    bufferInfo: MediaCodec.BufferInfo,
    callback: (RtpFrame) -> Unit
  ) {
    var fixedBuffer = byteBuffer.removeInfo(bufferInfo)
    //remove temporal delimitered OBU if found on start
    if (parser.getObuType(fixedBuffer.get(0)) == ObuType.TEMPORAL_DELIMITER) {
      fixedBuffer.position(2)
      fixedBuffer = fixedBuffer.slice()
    }
    val obuList = parser.getObus(fixedBuffer.duplicate().toByteArray())
    val ts = bufferInfo.presentationTimeUs * 1000L
    if (obuList.isEmpty()) return
    var data = byteArrayOf()
    obuList.forEachIndexed { index, obu ->
      val obuData = obu.getFullData()
      data = if (index == obuList.size - 1) {
        data.plus(obuData)
      } else {
        data.plus(parser.writeLeb128(obuData.size.toLong()).plus(obuData))
      }
    }
    fixedBuffer = ByteBuffer.wrap(data)
    val size = fixedBuffer.remaining()
    var sum = 0
    while (sum < size) {
      val isFirstPacket = sum == 0
      var isLastPacket = false
      val length = if (size - sum > maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 1) {
        maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 1
      } else {
        fixedBuffer.remaining()
      }
      val buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 1)
      val rtpTs = updateTimeStamp(buffer, ts)
      fixedBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 1, length)
      sum += length
      // Last packet before next NAL
      if (sum >= size) {
        isLastPacket = true
        markPacket(buffer) //mark end frame
      }
      val oSize = if (isFirstPacket) obuList.size else 1
      buffer[RtpConstants.RTP_HEADER_LENGTH] = generateAv1AggregationHeader(bufferInfo.isKeyframe(), isFirstPacket, isLastPacket, oSize)
      updateSeq(buffer)
      val rtpFrame = RtpFrame(buffer, rtpTs, buffer.size, rtpPort, rtcpPort, channelIdentifier)
      callback(rtpFrame)
    }
  }

  override fun reset() {
    super.reset()
  }

  private fun generateAv1AggregationHeader(isKeyFrame: Boolean, isFirstPacket: Boolean, isLastPacket: Boolean, numObu: Int): Byte {
    val z = if (isFirstPacket) 0 else 1
    val y = if (isLastPacket) 0 else 1
    val w = numObu
    val n = if (isKeyFrame && isFirstPacket) 1 else 0
    return ((z shl 7) or (y shl 6) or (w shl 4) or (n shl 3) or 0).toByte()
  }

}