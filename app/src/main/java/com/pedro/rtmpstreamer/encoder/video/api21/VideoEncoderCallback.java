package com.pedro.rtmpstreamer.encoder.video.api21;

import android.media.MediaCodec;
import android.media.MediaFormat;

/**
 * Created by pedro on 20/01/17.
 * Useless atm
 */

public class VideoEncoderCallback extends MediaCodec.Callback{

    @Override
    public void onInputBufferAvailable(MediaCodec codec, int index) {

    }

    @Override
    public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {

    }

    @Override
    public void onError(MediaCodec codec, MediaCodec.CodecException e) {

    }

    @Override
    public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {

    }
}
