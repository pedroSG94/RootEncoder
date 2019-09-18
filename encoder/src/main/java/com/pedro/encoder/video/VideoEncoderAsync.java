package com.pedro.encoder.video;

import android.media.MediaCodec;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 12/09/19.
 */
@RequiresApi(api = Build.VERSION_CODES.M)
public class VideoEncoderAsync extends VideoEncoder {

  public VideoEncoderAsync(GetVideoData getVideoData) {
    super(getVideoData);
  }

  public void startImp(Handler handler) {
    videoEncoder.setCallback(callback, handler);
    videoEncoder.start();
  }

  @Override
  public void inputAvailable(@NonNull MediaCodec mediaCodec, int inBufferIndex) {
    ByteBuffer byteBuffer = mediaCodec.getInputBuffer(inBufferIndex);
    processInput(byteBuffer, mediaCodec, inBufferIndex);
  }

  @Override
  public void outputAvailable(@NonNull MediaCodec mediaCodec, int outBufferIndex,
      @NonNull MediaCodec.BufferInfo bufferInfo) {
    ByteBuffer byteBuffer = mediaCodec.getOutputBuffer(outBufferIndex);
    processOutput(byteBuffer, mediaCodec, outBufferIndex, bufferInfo);
  }
}