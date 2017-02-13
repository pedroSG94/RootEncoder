package com.pedro.rtsp.rtsp;

/**
 * Created by pedro on 13/02/17.
 */

public class AudioBody {

    /** There are 13 supported frequencies by ADTS. **/
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

    private static int sampleRate = AUDIO_SAMPLING_RATES[4];
    private static int channel = 1; //stereo = 2, mono = 1

    public static String createAudioBody(int trackAudio) {
        String audio = "";
        int config = (2 & 0x1F) << 11 | (4 & 0x0F) << 7 | (channel & 0x0F) << 3;
        audio += "m=audio " + (5000 + 2 * trackAudio) + " RTP/AVP 96\r\n" +
                "a=rtpmap:96 mpeg4-generic/" + sampleRate + "\r\n" +
                "a=fmtp:96 streamtype=5; profile-level-id=15; mode=AAC-hbr; config=" + Integer.toHexString(config) + "; SizeLength=13; IndexLength=3; IndexDeltaLength=3;\r\n"
        + "a=control:trackID=" + 0 + "\r\n";
        return audio;
    }
}
