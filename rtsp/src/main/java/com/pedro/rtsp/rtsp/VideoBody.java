package com.pedro.rtsp.rtsp;

/**
 * Created by pedro on 13/02/17.
 */

public class VideoBody {

    private final String TAG = "VideoBody";

    //TODO get this values properly
    private static String profile = "42c029";

    public static String createVideoBody(int trackVideo, String sps, String pps) {
        return "m=video " + (5000 + 2 * trackVideo) + " RTP/AVP 96\r\n" +
                "a=rtpmap:96 H264/90000\r\n" +
                "a=fmtp:96 packetization-mode=1;profile-level-id=" + profile + ";sprop-parameter-sets=" + sps + "," + pps + ";\r\n"
                + "a=control:trackID=" + 1 + "\r\n";
    }
}
