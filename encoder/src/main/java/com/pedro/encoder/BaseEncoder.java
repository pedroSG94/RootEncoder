package com.pedro.encoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.pedro.encoder.utils.CodecUtil;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by pedro on 18/09/19.
 */
public abstract class BaseEncoder implements EncoderCallback {

  private static final String TAG = "BaseEncoder";
  private MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
  protected MediaCodec codec;
  protected HandlerThread handlerThread;
  protected long presentTimeUs;
  protected volatile boolean running = false;
  protected boolean isBufferMode = true;
  protected CodecUtil.Force force = CodecUtil.Force.FIRST_COMPATIBLE_FOUND;
  protected BlockingQueue<Frame> queue = new ArrayBlockingQueue<>(80);

  protected abstract void startImp(boolean resetTs);

  public void start() {
    start(true);
  }

  public void start(boolean resetTs) {
    startImp(resetTs);
    handlerThread.start();
    Handler handler = new Handler(handlerThread.getLooper());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      codec.setCallback(callback, handler);
      codec.start();
    } else {
      codec.start();
      handler.post(new Runnable() {
        @Override
        public void run() {
          while (running) {
            getDataFromEncoder();
          }
        }
      });
    }
    running = true;
  }

  protected abstract void stopImp();

  public void stop() {
    running = false;
    if (codec != null) {
      codec.stop();
      codec.release();
      codec = null;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      handlerThread.quitSafely();
    } else {
      handlerThread.quit();
    }
    queue.clear();
    stopImp();
  }

  private void getDataFromEncoder() {
    if (isBufferMode) {
      int inBufferIndex = codec.dequeueInputBuffer(0);
      if (inBufferIndex >= 0) {
        inputAvailable(codec, inBufferIndex);
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

  @RequiresApi(api = Build.VERSION_CODES.M)
  private MediaCodec.Callback callback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int inBufferIndex) {
          inputAvailable(mediaCodec, inBufferIndex);
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int outBufferIndex,
            @NonNull MediaCodec.BufferInfo bufferInfo) {
          outputAvailable(mediaCodec, outBufferIndex, bufferInfo);
        }

        @Override
        public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
          Log.e(TAG, "Error", e);
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec,
            @NonNull MediaFormat mediaFormat) {
          formatChanged(mediaCodec, mediaFormat);
        }
      };

  protected abstract Frame getInputFrame() throws InterruptedException;

  private void processInput(@NonNull ByteBuffer byteBuffer, @NonNull MediaCodec mediaCodec,
      int inBufferIndex) {
    try {
      Frame frame = getInputFrame();
      byteBuffer.clear();
      byteBuffer.put(frame.getBuffer(), frame.getOffset(), frame.getSize());
      long pts = System.nanoTime() / 1000 - presentTimeUs;
      mediaCodec.queueInputBuffer(inBufferIndex, 0, frame.getSize(), pts, 0);
    } catch (InterruptedException | IllegalStateException e) {
      Thread.currentThread().interrupt();
    }
  }

  protected abstract void checkBuffer(@NonNull ByteBuffer byteBuffer,
      @NonNull MediaCodec.BufferInfo bufferInfo);

  protected abstract void sendBuffer(@NonNull ByteBuffer byteBuffer,
      @NonNull MediaCodec.BufferInfo bufferInfo);

  private void processOutput(@NonNull ByteBuffer byteBuffer, @NonNull MediaCodec mediaCodec,
      int outBufferIndex, @NonNull MediaCodec.BufferInfo bufferInfo) {
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
  public void inputAvailable(@NonNull MediaCodec mediaCodec, int inBufferIndex) {
    ByteBuffer byteBuffer;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      byteBuffer = mediaCodec.getInputBuffer(inBufferIndex);
    } else {
      byteBuffer = mediaCodec.getInputBuffers()[inBufferIndex];
    }
    processInput(byteBuffer, mediaCodec, inBufferIndex);
  }

  @Override
  public void outputAvailable(@NonNull MediaCodec mediaCodec, int outBufferIndex,
      @NonNull MediaCodec.BufferInfo bufferInfo) {
    ByteBuffer byteBuffer;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      byteBuffer = mediaCodec.getOutputBuffer(outBufferIndex);
    } else {
      byteBuffer = mediaCodec.getOutputBuffers()[outBufferIndex];
    }
    processOutput(byteBuffer, mediaCodec, outBufferIndex, bufferInfo);
  }
}
