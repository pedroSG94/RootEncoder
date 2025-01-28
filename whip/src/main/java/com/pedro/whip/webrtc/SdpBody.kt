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

package com.pedro.whip.webrtc

import com.pedro.rtsp.utils.RtpConstants

/**
 * Created by pedro on 21/02/17.
 */
object SdpBody {

  /**
   * Opus only support sample rate 48khz and stereo channel but Android encoder accept others values.
   * The encoder internally transform the sample rate to 48khz and channels to stereo
   */
  fun createOpusBody(trackAudio: Int): String {
    val payload = RtpConstants.payloadType + trackAudio
    return "m=audio 0 UDP/TLS/RTP/SAVPF ${payload}\r\n" +
            "a=mid:$trackAudio\r\n" +
            "a=sendonly\r\n" +
            "a=msid:d46fb922-d52a-4e9c-aa87-444eadc1521b ce326ecf-a081-453a-8f9f-0605d5ef4128\r\n" +
            "a=rtpmap:$payload OPUS/48000/2\r\n"
  }

  fun createG711Body(trackAudio: Int, sampleRate: Int, isStereo: Boolean): String {
    val channel = if (isStereo) 2 else 1
    val payload = RtpConstants.payloadTypeG711
    return "m=audio 0 UDP/TLS/RTP/SAVPF ${payload}\r\n" +
            "a=mid:$trackAudio\r\n" +
            "a=sendonly\r\n" +
            "a=msid:d46fb922-d52a-4e9c-aa87-444eadc1521b ce326ecf-a081-453a-8f9f-0605d5ef4128\r\n" +
            "a=rtpmap:$payload PCMA/$sampleRate/$channel\r\n"
  }

  fun createAV1Body(trackVideo: Int): String {
    val payload = RtpConstants.payloadType + trackVideo
    return "m=video 0 UDP/TLS/RTP/SAVPF $payload\r\n" +
            "a=mid:$trackVideo\r\n" +
            "a=sendonly\r\n" +
            "a=msid:d46fb922-d52a-4e9c-aa87-444eadc1521b 3956b460-40f4-4d05-acef-03abcdd8c6fd\r\n" +
            "a=fmtp:$payload profile=0; level-idx=0;\r\n" +
            "a=rtpmap:$payload AV1/${RtpConstants.clockVideoFrequency}\r\n"
  }

  fun createH264Body(trackVideo: Int, sps: String, pps: String): String {
    val payload = RtpConstants.payloadType + trackVideo
    return "m=video 0 UDP/TLS/RTP/SAVPF $payload\r\n" +
            "a=mid:$trackVideo\r\n" +
            "a=sendonly\r\n" +
            "a=msid:d46fb922-d52a-4e9c-aa87-444eadc1521b 3956b460-40f4-4d05-acef-03abcdd8c6fd\r\n" +
            "a=fmtp:$payload packetization-mode=1; sprop-parameter-sets=$sps,$pps\r\n" +
            "a=rtpmap:$payload H264/${RtpConstants.clockVideoFrequency}\r\n"
  }

  fun createH265Body(trackVideo: Int, sps: String, pps: String, vps: String): String {
    val payload = RtpConstants.payloadType + trackVideo
    return "m=video 0 UDP/TLS/RTP/SAVPF ${payload}\r\n" +
            "a=mid:$trackVideo\r\n" +
            "a=sendonly\r\n" +
            "a=msid:d46fb922-d52a-4e9c-aa87-444eadc1521b 3956b460-40f4-4d05-acef-03abcdd8c6fd\r\n" +
            "a=fmtp:$payload packetization-mode=1; sprop-sps=$sps; sprop-pps=$pps; sprop-vps=$vps\r\n" +
            "a=rtpmap:$payload H265/${RtpConstants.clockVideoFrequency}\r\n"
  }
}