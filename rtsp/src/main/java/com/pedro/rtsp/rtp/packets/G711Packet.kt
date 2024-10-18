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
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants

/**
 *
 * RFC 7655.
 * Valid for G711A and G711U
 */
class G711Packet: BasePacket(
  0,
  RtpConstants.payloadTypeG711
) {

  init {
    channelIdentifier = RtpConstants.trackAudio
  }

  fun setAudioInfo(sampleRate: Int) {
    setClock(sampleRate.toLong())
  }

  override suspend fun createAndSendPacket(
    mediaFrame: MediaFrame,
    callback: suspend (List<RtpFrame>) -> Unit
  ) {
    val length = mediaFrame.info.size - mediaFrame.data.position()
    val maxPayload = maxPacketSize - RtpConstants.RTP_HEADER_LENGTH
    val ts = mediaFrame.info.timestamp * 1000
    var sum = 0
    val frames = mutableListOf<RtpFrame>()
    while (sum < length) {
      val size = if (length - sum < maxPayload) length - sum else maxPayload
      val buffer = getBuffer(size + RtpConstants.RTP_HEADER_LENGTH)
      mediaFrame.data.get(buffer, RtpConstants.RTP_HEADER_LENGTH, size)
      markPacket(buffer)
      val rtpTs = updateTimeStamp(buffer, ts)
      updateSeq(buffer)
      val rtpFrame = RtpFrame(buffer, rtpTs, RtpConstants.RTP_HEADER_LENGTH + size , channelIdentifier)
      sum += size
      frames.add(rtpFrame)
    }
    if (frames.isNotEmpty()) callback(frames)
  }
}