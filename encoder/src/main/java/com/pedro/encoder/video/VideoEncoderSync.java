package com.pedro.encoder.video;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import androidx.annotation.NonNull;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 18/09/19.
 */
public class VideoEncoderSync extends VideoEncoder {

  private MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();

  public VideoEncoderSync(GetVideoData getVideoData) {
    super(getVideoData);
  }

  public void startImp(Handler handler) {
    videoEncoder.start();
    handler.post(new Runnable() {
      @Override
      public void run() {
        while (running) {
          getDataFromEncoder();
        }
      }
    });
  }

  private void getDataFromEncoder() {
    if (formatVideoEncoder != FormatVideoEncoder.SURFACE) {
      int inBufferIndex = videoEncoder.dequeueInputBuffer(0);
      if (inBufferIndex >= 0) {
        inputAvailable(videoEncoder, inBufferIndex);
      }
    }
    for (; running; ) {
      int outBufferIndex = videoEncoder.dequeueOutputBuffer(videoInfo, 0);
      if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        MediaFormat mediaFormat = videoEncoder.getOutputFormat();
        formatChanged(videoEncoder, mediaFormat);
      } else if (outBufferIndex >= 0) {
        outputAvailable(videoEncoder, outBufferIndex, videoInfo);
      } else {
        break;
      }
    }
  }

  @Override
  public void inputAvailable(@NonNull MediaCodec mediaCodec, int inBufferIndex) {
    ByteBuffer byteBuffer = videoEncoder.getInputBuffers()[inBufferIndex];
    processInput(byteBuffer, mediaCodec, inBufferIndex);
  }

  @Override
  public void outputAvailable(@NonNull MediaCodec mediaCodec, int outBufferIndex,
      @NonNull MediaCodec.BufferInfo bufferInfo) {
    ByteBuffer byteBuffer = videoEncoder.getOutputBuffers()[outBufferIndex];
    processOutput(byteBuffer, mediaCodec, outBufferIndex, bufferInfo);
  }
}
