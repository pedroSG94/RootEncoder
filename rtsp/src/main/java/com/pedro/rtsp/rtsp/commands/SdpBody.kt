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

import com.pedro.common.SpsH264Parser
import com.pedro.common.av1.Av1SequenceHeaderParser
import com.pedro.common.config.AacAudioSpecificConfig
import com.pedro.common.config.AudioObjectType
import com.pedro.common.getData
import com.pedro.common.toInt
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.encodeToString
import java.nio.ByteBuffer

/**
 * Created by pedro on 21/02/17.
 */
object SdpBody {

  fun createOpusBody(trackAudio: Int, sampleRate: Int, isStereo: Boolean, secured: Boolean = false): String {
    val payload = RtpConstants.payloadType + trackAudio
    val type = if (secured) "UDP/TLS/RTP/SAVPF" else "RTP/AVP"
    val port = if (secured) 9 else 0
    val identifier = if (secured) {
      "a=sendonly\r\n" +
      "a=mid:$trackAudio\r\n"
    } else "a=control:streamid=$trackAudio\r\n"
    return "m=audio $port $type ${payload}\r\n" +
        "a=rtpmap:$payload OPUS/${RtpConstants.clockOpusFrequency}/2\r\n" +
        "a=fmtp:$payload sprop-stereo=${isStereo.toInt()}; sprop-maxcapturerate=$sampleRate\r\n" +
        identifier
  }

  fun createG711Body(trackAudio: Int, secured: Boolean = false): String {
    val payload = RtpConstants.payloadTypeG711
    val type = if (secured) "UDP/TLS/RTP/SAVPF" else "RTP/AVP"
    val port = if (secured) 9 else 0
    val identifier = if (secured) {
      "a=sendonly\r\n" +
      "a=mid:$trackAudio\r\n"
    } else "a=control:streamid=$trackAudio\r\n"
    return "m=audio $port $type ${payload}\r\n" +
        "a=rtpmap:$payload PCMA/8000/1\r\n" +
        identifier
  }

  fun createAacBody(trackAudio: Int, sampleRate: Int, isStereo: Boolean, isHeAac: Boolean, secured: Boolean = false): String {
    val channels = if (isStereo) 2 else 1
    val objectType = if (isHeAac) AudioObjectType.AAC_SBR else AudioObjectType.AAC_LC
    val config = AacAudioSpecificConfig(objectType, sampleRate, channels)
    val configHex = config.calculate().toHexString()
    val payload = RtpConstants.payloadType + trackAudio
    val type = if (secured) "UDP/TLS/RTP/SAVPF" else "RTP/AVP"
    val port = if (secured) 9 else 0
    val identifier = if (secured) {
      "a=sendonly\r\n" +
      "a=mid:$trackAudio\r\n"
    } else "a=control:streamid=$trackAudio\r\n"
    return "m=audio $port $type ${payload}\r\n" +
        "a=rtpmap:$payload MPEG4-GENERIC/$sampleRate/$channels\r\n" +
        "a=fmtp:$payload profile-level-id=1; mode=AAC-hbr; config=$configHex; sizelength=13; indexlength=3; indexdeltalength=3\r\n" +
        identifier
  }

  fun createAV1Body(trackVideo: Int, header: ByteBuffer, secured: Boolean = false): String {
    val sequenceHeader = Av1SequenceHeaderParser()
    sequenceHeader.parse(header)
    val payload = RtpConstants.payloadType + trackVideo
    val type = if (secured) "UDP/TLS/RTP/SAVPF" else "RTP/AVP"
    val port = if (secured) 9 else 0
    val identifier = if (secured) {
      "a=sendonly\r\n" +
      "a=mid:$trackVideo\r\n"
    } else "a=control:streamid=$trackVideo\r\n"
    return "m=video $port $type $payload\r\n" +
        "a=rtpmap:$payload AV1/${RtpConstants.clockVideoFrequency}\r\n" +
        "a=fmtp:$payload profile=${sequenceHeader.seqProfile}; level-idx=${sequenceHeader.seqLevelIdx}\r\n" +
        identifier
  }

  fun createH264Body(trackVideo: Int, sps: ByteBuffer, pps: ByteBuffer, secured: Boolean = false): String {
    val spsH264Parser = SpsH264Parser()
    spsH264Parser.parse(sps)
    val spsString = sps.getData().encodeToString()
    val ppsString = pps.getData().encodeToString()
    val payload = RtpConstants.payloadType + trackVideo
    val type = if (secured) "UDP/TLS/RTP/SAVPF" else "RTP/AVP"
    val port = if (secured) 9 else 0
    val profileLevelId = "%02x%02x%02x".format(
      spsH264Parser.profileIdc, spsH264Parser.profileCompatibility, spsH264Parser.levelIdc
    )
    val identifier = if (secured) {
      "a=sendonly\r\n" +
          "a=mid:$trackVideo\r\n"
    } else "a=control:streamid=$trackVideo\r\n"
    return "m=video $port $type $payload\r\n" +
        "a=rtpmap:$payload H264/${RtpConstants.clockVideoFrequency}\r\n" +
        "a=fmtp:$payload packetization-mode=1; sprop-parameter-sets=$spsString,$ppsString; profile-level-id=$profileLevelId\r\n" +
        identifier
  }

  fun createH265Body(trackVideo: Int, sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer, secured: Boolean = false): String {
    val vpsString = vps.getData().encodeToString()
    val spsString = sps.getData().encodeToString()
    val ppsString = pps.getData().encodeToString()
    val payload = RtpConstants.payloadType + trackVideo
    val type = if (secured) "UDP/TLS/RTP/SAVPF" else "RTP/AVP"
    val port = if (secured) 9 else 0
    val identifier = if (secured) {
      "a=sendonly\r\n" +
          "a=mid:$trackVideo\r\n"
    } else "a=control:streamid=$trackVideo\r\n"
    return "m=video $port $type ${payload}\r\n" +
        "a=rtpmap:$payload H265/${RtpConstants.clockVideoFrequency}\r\n" +
        "a=fmtp:$payload sprop-sps=$spsString; sprop-pps=$ppsString; sprop-vps=$vpsString\r\n" +
        identifier
  }

  fun createVp8Body(trackVideo: Int, secured: Boolean = false): String {
    val payload = RtpConstants.payloadType + trackVideo
    val type = if (secured) "UDP/TLS/RTP/SAVPF" else "RTP/AVP"
    val port = if (secured) 9 else 0
    val identifier = if (secured) {
          "a=sendonly\r\n" +
          "a=mid:$trackVideo\r\n"
    } else "a=control:streamid=$trackVideo\r\n"
    return "m=video $port $type ${payload}\r\n" +
        "a=rtpmap:$payload VP8/${RtpConstants.clockVideoFrequency}\r\n" +
        identifier
  }

  fun createVp9Body(trackVideo: Int, secured: Boolean = false): String {
    val payload = RtpConstants.payloadType + trackVideo
    val type = if (secured) "UDP/TLS/RTP/SAVPF" else "RTP/AVP"
    val port = if (secured) 9 else 0
    val identifier = if (secured) {
      "a=sendonly\r\n" +
          "a=mid:$trackVideo\r\n"
    } else "a=control:streamid=$trackVideo\r\n"
    return "m=video $port $type ${payload}\r\n" +
        "a=rtpmap:$payload VP9/${RtpConstants.clockVideoFrequency}\r\n" +
        identifier
  }
}
