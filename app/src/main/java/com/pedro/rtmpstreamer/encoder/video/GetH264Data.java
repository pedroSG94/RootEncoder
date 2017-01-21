package com.pedro.rtmpstreamer.encoder.video;

import android.media.MediaCodec;

import java.nio.ByteBuffer;

/**
 * Created by pedro on 20/01/17.
 */

public interface GetH264Data {

    void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);
}
