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

package com.pedro.rtsp.rtsp.commands

import com.pedro.common.AudioUtils
import com.pedro.rtsp.utils.RtpConstants

/**
 * Created by pedro on 21/02/17.
 */
object SdpBody {

  /**
   * Opus only support sample rate 48khz and stereo channel but Android encoder accept others values.
   * The encoder internally transform the sample rate to 48khz and channels to stereo
   */
  fun createOpusBody(trackAudio: Int, secured: Boolean = false): String {
    val payload = RtpConstants.payloadType + trackAudio
    val type = if (secured) "UDP/TLS/RTP/SAVPF" else "RTP/AVP"
    val identifier = if (secured) {
      "a=sendonly\r\n" +
      "a=mid:$trackAudio\r\n"
    } else "a=control:streamid=$trackAudio\r\n"
    return "m=audio 0 $type ${payload}\r\n" +
        "a=rtpmap:$payload OPUS/48000/2\r\n" +
        identifier
  }

  fun createG711Body(trackAudio: Int, sampleRate: Int, isStereo: Boolean, secured: Boolean = false): String {
    val channel = if (isStereo) 2 else 1
    val payload = RtpConstants.payloadTypeG711
    val type = if (secured) "UDP/TLS/RTP/SAVPF" else "RTP/AVP"
    val identifier = if (secured) {
      "a=sendonly\r\n" +
          "a=mid:$trackAudio\r\n"
    } else "a=control:streamid=$trackAudio\r\n"
    return "m=audio 0 $type ${payload}\r\n" +
        "a=rtpmap:$payload PCMA/$sampleRate/$channel\r\n" +
        identifier
  }

  fun createAacBody(trackAudio: Int, sampleRate: Int, isStereo: Boolean, secured: Boolean = false): String {
    val frequency = AudioUtils.getFrequency(sampleRate)
    val channel = if (isStereo) 2 else 1
    val config = 2 and 0x1F shl 11 or (frequency and 0x0F shl 7) or (channel and 0x0F shl 3)
    val payload = RtpConstants.payloadType + trackAudio
    val type = if (secured) "UDP/TLS/RTP/SAVPF" else "RTP/AVP"
    val identifier = if (secured) {
      "a=sendonly\r\n" +
          "a=mid:$trackAudio\r\n"
    } else "a=control:streamid=$trackAudio\r\n"
    return "m=audio 0 $type ${payload}\r\n" +
        "a=rtpmap:$payload MPEG4-GENERIC/$sampleRate/$channel\r\n" +
        "a=fmtp:$payload profile-level-id=1; mode=AAC-hbr; config=${Integer.toHexString(config)}; sizelength=13; indexlength=3; indexdeltalength=3\r\n" +
        identifier
  }

  fun createAV1Body(trackVideo: Int, secured: Boolean = false): String {
    val payload = RtpConstants.payloadType + trackVideo
    val type = if (secured) "UDP/TLS/RTP/SAVPF" else "RTP/AVP"
    val identifier = if (secured) {
      "a=sendonly\r\n" +
          "a=mid:$trackVideo\r\n"
    } else "a=control:streamid=$trackVideo\r\n"
    return "m=video 0 $type $payload\r\n" +
        "a=rtpmap:$payload AV1/${RtpConstants.clockVideoFrequency}\r\n" +
        "a=fmtp:$payload profile=0; level-idx=0;\r\n" +
        identifier
  }

  fun createH264Body(trackVideo: Int, sps: String, pps: String, secured: Boolean = false): String {
    val payload = RtpConstants.payloadType + trackVideo
    val type = if (secured) "UDP/TLS/RTP/SAVPF" else "RTP/AVP"
    val identifier = if (secured) {
      "a=sendonly\r\n" +
          "a=mid:$trackVideo\r\n"
    } else "a=control:streamid=$trackVideo\r\n"
    return "m=video 0 $type $payload\r\n" +
        "a=rtpmap:$payload H264/${RtpConstants.clockVideoFrequency}\r\n" +
        "a=fmtp:$payload packetization-mode=1; sprop-parameter-sets=$sps,$pps\r\n" +
        identifier
  }

  fun createH265Body(trackVideo: Int, sps: String, pps: String, vps: String, secured: Boolean = false): String {
    val payload = RtpConstants.payloadType + trackVideo
    val type = if (secured) "UDP/TLS/RTP/SAVPF" else "RTP/AVP"
    val identifier = if (secured) {
      "a=sendonly\r\n" +
          "a=mid:$trackVideo\r\n"
    } else "a=control:streamid=$trackVideo\r\n"
    return "m=video 0 $type ${payload}\r\n" +
        "a=rtpmap:$payload H265/${RtpConstants.clockVideoFrequency}\r\n" +
        "a=fmtp:$payload packetization-mode=1; sprop-sps=$sps; sprop-pps=$pps; sprop-vps=$vps\r\n" +
        identifier
  }
}