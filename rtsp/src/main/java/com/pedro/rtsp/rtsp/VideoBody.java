package com.pedro.rtsp.rtsp;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Base64;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Created by pedro on 13/02/17.
 */

public class VideoBody {

    private final String TAG = "VideoBody";

    //test values
    //TODO get this values properly
    private static String profile = "42c029";
    //private static String sps = "Z0LAKY1oCgPSAeEQjUA=";
    //private static String pps = "aM4BqDXI";

    public static String createVideoBody(int trackVideo, String sps, String pps) {
        return "m=video " + (5000 + 2 * trackVideo) + " RTP/AVP 96\r\n" +
                "a=rtpmap:96 H264/90000\r\n" +
                "a=fmtp:96 packetization-mode=1;profile-level-id=" + profile + ";sprop-parameter-sets=" + sps + "," + pps + ";\r\n"
                + "a=control:trackID=" + 1 + "\r\n";
    }
}
