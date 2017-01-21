package com.pedro.rtmpstreamer.encoder.video;

import android.media.MediaCodecInfo;

/**
 * Created by pedro on 21/01/17.
 */

public enum FormatVideoEncoder {

    YUV420, YUV422, YUV444, SURFACE;

    public int getFormatCodec(){
        switch (this) {
            case YUV420:
                return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
            case YUV422:
                return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422Flexible;
            case YUV444:
                return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV444Flexible;
            case SURFACE:
                return MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
            default:
                return -1;
        }
    }
}
