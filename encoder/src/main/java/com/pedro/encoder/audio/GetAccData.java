package com.pedro.encoder.audio;

import android.media.MediaCodec;

import java.nio.ByteBuffer;

/**
 * Created by pedro on 19/01/17.
 */

public interface GetAccData {

    void getAccData(ByteBuffer accBuffer, MediaCodec.BufferInfo info);
}
