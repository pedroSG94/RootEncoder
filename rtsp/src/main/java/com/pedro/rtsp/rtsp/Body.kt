package com.pedro.rtsp.rtsp

import com.pedro.rtsp.utils.RtpConstants

/**
 * Created by pedro on 21/02/17.
 */
object Body {

  /** supported sampleRates.  */
  private val AUDIO_SAMPLING_RATES = intArrayOf(
      96000,  // 0
      88200,  // 1
      64000,  // 2
      48000,  // 3
      44100,  // 4
      32000,  // 5
      24000,  // 6
      22050,  // 7
      16000,  // 8
      12000,  // 9
      11025,  // 10
      8000,  // 11
      7350,  // 12
      -1,  // 13
      -1,  // 14
      -1)

  fun createAacBody(trackAudio: Int, sampleRate: Int, isStereo: Boolean): String {
    val sampleRateNum = AUDIO_SAMPLING_RATES.toList().indexOf(sampleRate)
    val channel = if (isStereo) 2 else 1
    val config = 2 and 0x1F shl 11 or (sampleRateNum and 0x0F shl 7) or (channel and 0x0F shl 3)
    return "m=audio 0 RTP/AVP ${RtpConstants.payloadTypeAudio}\r\n" +
        "a=rtpmap:${RtpConstants.payloadTypeAudio} MPEG4-GENERIC/$sampleRate/$channel\r\n" +
        "a=fmtp:${RtpConstants.payloadTypeAudio} streamtype=5; profile-level-id=15; mode=AAC-hbr; config=${Integer.toHexString(config)}; SizeLength=13; IndexLength=3; IndexDeltaLength=3;\n" +
        "a=control:trackID=$trackAudio\r\n"
  }

  fun createH264Body(trackVideo: Int, sps: String, pps: String): String {
    return "m=video 0 RTP/AVP ${RtpConstants.payloadTypeVideo}\r\n" +
        "a=rtpmap:${RtpConstants.payloadTypeVideo} H264/${RtpConstants.clockVideoFrequency}\r\n" +
        "a=fmtp:${RtpConstants.payloadTypeVideo} packetization-mode=1; sprop-parameter-sets=$sps,$pps;\r\n" +
        "a=control:trackID=$trackVideo\r\n"
  }

  fun createH265Body(trackVideo: Int, sps: String, pps: String, vps: String): String {
    return "m=video 0 RTP/AVP ${RtpConstants.payloadTypeVideo}\r\n" +
        "a=rtpmap:${RtpConstants.payloadTypeVideo} H265/${RtpConstants.clockVideoFrequency}\r\n" +
        "a=fmtp:${RtpConstants.payloadTypeVideo} sprop-sps=$sps; sprop-pps=$pps; sprop-vps=$vps;\r\n" +
        "a=control:trackID=$trackVideo\r\n"
  }
}