package com.pedro.rtsp.rtsp;

/**
 * Created by pedro on 13/02/17.
 */

public class VideoBody {

    private static int width = 640;
    private static int height = 480;
    private static int framerate = 30;
    private static String profil = "42c029";
    private static String sps = "Z0LAKY1oCgPSAeEQjUA=";
    private static String pps = "aM4BqDXI";

    public static String createVideoBody(int trackVideo) {
        String video = "";
        video += "m=video " + (5000 + 2 * trackVideo) + " RTP/AVP 96\r\n" +
                "a=rtpmap:96 H264/90000\r\n" +
                "a=fmtp:96 packetization-mode=1;profile-level-id=" + profil + ";sprop-parameter-sets=" + sps + "," + pps + ";\r\n"
        + "a=control:trackID=" + 1 + "\r\n";
        return video;
    }
}
