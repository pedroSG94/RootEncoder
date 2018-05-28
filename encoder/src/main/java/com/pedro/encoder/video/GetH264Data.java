package com.pedro.encoder.video;

import android.media.MediaCodec;

import android.media.MediaFormat;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 20/01/17.
 */

public interface GetH264Data {

  void onSPSandPPS(ByteBuffer sps, ByteBuffer pps);

  void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

  void onVideoFormat(MediaFormat mediaFormat);
}
