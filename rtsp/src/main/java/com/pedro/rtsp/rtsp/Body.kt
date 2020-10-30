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

    @JvmStatic
    fun createAacBody(trackAudio: Int, sampleRate: Int, isStereo: Boolean): String {
        val sampleRateNum = AUDIO_SAMPLING_RATES.toList().indexOf(sampleRate)
        val channel = if (isStereo) 2 else 1
        val config = 2 and 0x1F shl 11 or (sampleRateNum and 0x0F shl 7) or (channel and 0x0F shl 3)
        return """m=audio 0 RTP/AVP ${RtpConstants.payloadType}
a=rtpmap:${RtpConstants.payloadType} MPEG4-GENERIC/$sampleRate/$channel
a=fmtp:${RtpConstants.payloadType} streamtype=5; profile-level-id=15; mode=AAC-hbr; config=${Integer.toHexString(config)}; SizeLength=13; IndexLength=3; IndexDeltaLength=3;
a=control:trackID=$trackAudio
"""
    }

    @JvmStatic
    fun createH264Body(trackVideo: Int, sps: String, pps: String): String {
        return """m=video 0 RTP/AVP ${RtpConstants.payloadType}
a=rtpmap:${RtpConstants.payloadType} H264/${RtpConstants.clockVideoFrequency}
a=fmtp:${RtpConstants.payloadType} packetization-mode=1;sprop-parameter-sets=$sps,$pps;
a=control:trackID=$trackVideo
"""
    }

    @JvmStatic
    fun createH265Body(trackVideo: Int, sps: String, pps: String, vps: String): String {
        return """m=video 0 RTP/AVP ${RtpConstants.payloadType}
a=rtpmap:${RtpConstants.payloadType} H265/${RtpConstants.clockVideoFrequency}
a=fmtp:${RtpConstants.payloadType} sprop-sps=$sps; sprop-pps=$pps; sprop-vps=$vps;
a=control:trackID=$trackVideo
"""
    }
}