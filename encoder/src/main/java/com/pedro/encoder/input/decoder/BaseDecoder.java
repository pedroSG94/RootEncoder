package com.pedro.encoder.input.decoder;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import androidx.annotation.RequiresApi;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Map;

public abstract class BaseDecoder {

  protected static final String TAG = "BaseDecoder";
  protected LoopFileInterface loopFileInterface;
  protected MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
  protected MediaExtractor extractor;
  protected MediaCodec codec;
  protected volatile boolean running = false;
  protected MediaFormat mediaFormat;
  private HandlerThread handlerThread;
  protected String mime = "";
  protected boolean loopMode = false;
  protected volatile long seekTime = 0;
  protected volatile long startMs = 0;
  protected long duration;

  public BaseDecoder(LoopFileInterface loopFileInterface) {
    this.loopFileInterface = loopFileInterface;
  }

  public boolean initExtractor(String filePath) throws IOException {
    extractor = new MediaExtractor();
    extractor.setDataSource(filePath);
    return extract(extractor);
  }

  public boolean initExtractor(FileDescriptor fileDescriptor) throws IOException {
    extractor = new MediaExtractor();
    extractor.setDataSource(fileDescriptor);
    return extract(extractor);
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  public boolean initExtractor(AssetFileDescriptor assetFileDescriptor) throws IOException {
    extractor = new MediaExtractor();
    extractor.setDataSource(assetFileDescriptor);
    return extract(extractor);
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  public boolean initExtractor(MediaDataSource mediaDataSource) throws IOException {
    extractor = new MediaExtractor();
    extractor.setDataSource(mediaDataSource);
    return extract(extractor);
  }

  public boolean initExtractor(String filePath, Map<String, String> headers) throws IOException {
    extractor = new MediaExtractor();
    extractor.setDataSource(filePath, headers);
    return extract(extractor);
  }

  public boolean initExtractor(FileDescriptor fileDescriptor, long offset, long length)
      throws IOException {
    extractor = new MediaExtractor();
    extractor.setDataSource(fileDescriptor, offset, length);
    return extract(extractor);
  }

  public boolean initExtractor(Context context, Uri uri, Map<String, String> headers)
      throws IOException {
    extractor = new MediaExtractor();
    extractor.setDataSource(context, uri, headers);
    return extract(extractor);
  }

  public void start() {
    running = true;
    handlerThread = new HandlerThread(TAG);
    handlerThread.start();
    Handler handler = new Handler(handlerThread.getLooper());
    codec.start();
    handler.post(new Runnable() {
      @Override
      public void run() {
        try {
          decode();
        } catch (IllegalStateException e) {
          Log.i(TAG, "Decoding error", e);
        } catch (NullPointerException e) {
          Log.i(TAG, "Decoder maybe was stopped");
          Log.i(TAG, "Decoding error", e);
        }
      }
    });
  }

  public void stop() {
    running = false;
    stopDecoder();
    if (extractor != null) {
      extractor.release();
      extractor = null;
      mime = "";
    }
  }

  protected boolean prepare(Surface surface) {
    try {
      codec = MediaCodec.createDecoderByType(mime);
      codec.configure(mediaFormat, surface, null, 0);
      return true;
    } catch (IOException e) {
      Log.e(TAG, "Prepare decoder error:", e);
      return false;
    }
  }

  protected void resetCodec(Surface surface) {
    boolean wasRunning = running;
    stopDecoder();
    if (extractor != null) seekTime = extractor.getSampleTime() / 1000;
    prepare(surface);
    if (wasRunning) {
      start();
    }
  }

  protected void stopDecoder() {
    running = false;
    seekTime = 0;
    if (handlerThread != null) {
      if (handlerThread.getLooper() != null) {
        if (handlerThread.getLooper().getThread() != null) {
          handlerThread.getLooper().getThread().interrupt();
        }
        handlerThread.getLooper().quit();
      }
      handlerThread.quit();
      if (codec != null) {
        try {
          codec.flush();
        } catch (IllegalStateException ignored) { }
      }
      //wait for thread to die for 500ms.
      try {
        handlerThread.getLooper().getThread().join(500);
      } catch (Exception ignored) { }
      handlerThread = null;
    }
    try {
      codec.stop();
      codec.release();
      codec = null;
    } catch (IllegalStateException | NullPointerException e) {
      codec = null;
    }
  }

  public void moveTo(double time) {
    extractor.seekTo((long) (time * 10E5), MediaExtractor.SEEK_TO_CLOSEST_SYNC);
    seekTime = extractor.getSampleTime() / 1000;
    startMs = System.currentTimeMillis();
  }

  public void setLoopMode(boolean loopMode) {
    this.loopMode = loopMode;
  }

  public double getDuration() {
    return duration / 10E5;
  }

  public double getTime() {
    if (running) {
      return extractor.getSampleTime() / 10E5;
    } else {
      return 0;
    }
  }

  protected abstract boolean extract(MediaExtractor extractor);

  protected abstract void decode();
}
