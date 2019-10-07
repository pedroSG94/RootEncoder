package com.pedro.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import androidx.annotation.NonNull;
import com.pedro.encoder.utils.CodecUtil;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 18/09/19.
 */
public abstract class BaseEncoder implements EncoderCallback {

  private static final String TAG = "BaseEncoder";
  private MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
  protected MediaCodec codec;
  protected long presentTimeUs;
  protected volatile boolean running = false;
  protected boolean isBufferMode = true;
  protected CodecUtil.Force force = CodecUtil.Force.FIRST_COMPATIBLE_FOUND;

  public void start() {
    start(true);
  }

  public abstract void start(boolean resetTs);

  protected abstract void stopImp();

  public void stop() {
    running = false;
    if (codec != null) {
      codec.stop();
      codec.release();
      codec = null;
    }
    stopImp();
  }

  protected abstract MediaCodecInfo chooseEncoder(String mime);

  protected void getDataFromEncoder(Frame frame) throws IllegalStateException {
    if (isBufferMode) {
      int inBufferIndex = codec.dequeueInputBuffer(0);
      if (inBufferIndex >= 0) {
        inputAvailable(codec, inBufferIndex, frame);
      }
    }
    for (; running; ) {
      int outBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0);
      if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        MediaFormat mediaFormat = codec.getOutputFormat();
        formatChanged(codec, mediaFormat);
      } else if (outBufferIndex >= 0) {
        outputAvailable(codec, outBufferIndex, bufferInfo);
      } else {
        break;
      }
    }
  }

  protected abstract Frame getInputFrame() throws InterruptedException;

  private void processInput(@NonNull ByteBuffer byteBuffer, @NonNull MediaCodec mediaCodec,
      int inBufferIndex, Frame frame) throws IllegalStateException {
    try {
      if (frame == null) frame = getInputFrame();
      byteBuffer.clear();
      byteBuffer.put(frame.getBuffer(), frame.getOffset(), frame.getSize());
      long pts = System.nanoTime() / 1000 - presentTimeUs;
      mediaCodec.queueInputBuffer(inBufferIndex, 0, frame.getSize(), pts, 0);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  protected abstract void checkBuffer(@NonNull ByteBuffer byteBuffer,
      @NonNull MediaCodec.BufferInfo bufferInfo);

  protected abstract void sendBuffer(@NonNull ByteBuffer byteBuffer,
      @NonNull MediaCodec.BufferInfo bufferInfo);

  private void processOutput(@NonNull ByteBuffer byteBuffer, @NonNull MediaCodec mediaCodec,
      int outBufferIndex, @NonNull MediaCodec.BufferInfo bufferInfo) throws IllegalStateException {
    checkBuffer(byteBuffer, bufferInfo);
    sendBuffer(byteBuffer, bufferInfo);
    mediaCodec.releaseOutputBuffer(outBufferIndex, false);
  }

  public void setForce(CodecUtil.Force force) {
    this.force = force;
  }

  public boolean isRunning() {
    return running;
  }

  @Override
  public void inputAvailable(@NonNull MediaCodec mediaCodec, int inBufferIndex, Frame frame)
      throws IllegalStateException {
    ByteBuffer byteBuffer;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      byteBuffer = mediaCodec.getInputBuffer(inBufferIndex);
    } else {
      byteBuffer = mediaCodec.getInputBuffers()[inBufferIndex];
    }
    processInput(byteBuffer, mediaCodec, inBufferIndex, frame);
  }

  @Override
  public void outputAvailable(@NonNull MediaCodec mediaCodec, int outBufferIndex,
      @NonNull MediaCodec.BufferInfo bufferInfo) throws IllegalStateException {
    ByteBuffer byteBuffer;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      byteBuffer = mediaCodec.getOutputBuffer(outBufferIndex);
    } else {
      byteBuffer = mediaCodec.getOutputBuffers()[outBufferIndex];
    }
    processOutput(byteBuffer, mediaCodec, outBufferIndex, bufferInfo);
  }
}
