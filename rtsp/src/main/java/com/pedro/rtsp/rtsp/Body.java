package com.pedro.rtsp.rtsp;

import com.pedro.rtsp.utils.RtpConstants;

/**
 * Created by pedro on 21/02/17.
 */

public class Body {

  /** supported sampleRates. **/
  private static final int[] AUDIO_SAMPLING_RATES = {
      96000, // 0
      88200, // 1
      64000, // 2
      48000, // 3
      44100, // 4
      32000, // 5
      24000, // 6
      22050, // 7
      16000, // 8
      12000, // 9
      11025, // 10
      8000,  // 11
      7350,  // 12
      -1,   // 13
      -1,   // 14
      -1,   // 15
  };

  public static String createAacBody(int trackAudio, int sampleRate, boolean isStereo) {
    int sampleRateNum = -1;
    for (int i = 0; i < AUDIO_SAMPLING_RATES.length; i++) {
      if (AUDIO_SAMPLING_RATES[i] == sampleRate) {
        sampleRateNum = i;
        break;
      }
    }
    int channel = (isStereo) ? 2 : 1;
    int config = (2 & 0x1F) << 11 | (sampleRateNum & 0x0F) << 7 | (channel & 0x0F) << 3;
    return "m=audio 0 RTP/AVP "
        + RtpConstants.payloadType
        + "\r\n"
        + "a=rtpmap:"
        + RtpConstants.payloadType
        + " MPEG4-GENERIC/"
        + sampleRate
        + "/"
        + channel
        + "\r\n"
        + "a=fmtp:"
        + RtpConstants.payloadType
        + " streamtype=5; profile-level-id=15; mode=AAC-hbr; config="
        + Integer.toHexString(config)
        + "; SizeLength=13; IndexLength=3; IndexDeltaLength=3;\r\n"
        + "a=control:trackID="
        + trackAudio
        + "\r\n";
  }

  public static String createH264Body(int trackVideo, String sps, String pps) {
    return "m=video 0 RTP/AVP "
        + RtpConstants.payloadType
        + "\r\n"
        + "a=rtpmap:"
        + RtpConstants.payloadType
        + " H264/"
        + RtpConstants.clockVideoFrequency
        + "\r\n"
        + "a=fmtp:"
        + RtpConstants.payloadType
        + " packetization-mode=1;sprop-parameter-sets="
        + sps
        + ","
        + pps
        + ";\r\n"
        + "a=control:trackID="
        + trackVideo
        + "\r\n";
  }

  public static String createH265Body(int trackVideo, String sps, String pps, String vps) {
    return "m=video 0 RTP/AVP "
        + RtpConstants.payloadType
        + "\r\n"
        + "a=rtpmap:"
        + RtpConstants.payloadType
        + " H265/"
        + RtpConstants.clockVideoFrequency
        + "\r\n"
        + "a=fmtp:"
        + RtpConstants.payloadType
        + " sprop-sps="
        + sps
        + "; sprop-pps="
        + pps
        + "; sprop-vps="
        + vps
        + ";\r\n"
        + "a=control:trackID="
        + trackVideo
        + "\r\n";
  }
}
