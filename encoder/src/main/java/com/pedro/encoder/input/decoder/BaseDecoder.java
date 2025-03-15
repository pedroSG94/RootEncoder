/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.encoder.input.decoder;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import com.pedro.common.TimeUtils;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseDecoder {

  protected String TAG = "BaseDecoder";
  protected MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
  protected MediaCodec codec;
  protected volatile boolean running = false;
  protected MediaFormat mediaFormat;
  private ExecutorService executor;
  protected String mime = "";
  protected boolean loopMode = false;
  private volatile long startTs = 0;
  protected long duration;
  protected final Object sync = new Object();
  protected AtomicBoolean pause = new AtomicBoolean(false);
  protected volatile boolean looped = false;
  private final DecoderInterface decoderInterface;
  private Extractor extractor = new AndroidExtractor();

  public BaseDecoder(DecoderInterface decoderInterface) {
    this.decoderInterface = decoderInterface;
  }

  public boolean initExtractor(String filePath) throws IOException {
    extractor.initialize(filePath);
    return extract(extractor);
  }

  public boolean initExtractor(FileDescriptor fileDescriptor) throws IOException {
    extractor.initialize(fileDescriptor);
    return extract(extractor);
  }

  public boolean initExtractor(Context context, Uri uri) throws IOException {
    extractor.initialize(context, uri);
    return extract(extractor);
  }

  public void start() {
    Log.i(TAG, "start decoder");
    running = true;
    codec.start();
    executor = Executors.newSingleThreadExecutor();
    executor.execute(() -> {
      try {
        decode();
      } catch (IllegalStateException e) {
        Log.i(TAG, "Decoding error", e);
      } catch (NullPointerException e) {
        Log.i(TAG, "Decoder maybe was stopped");
        Log.i(TAG, "Decoding error", e);
      }
    });
  }

  public void stop() {
    Log.i(TAG, "stop decoder");
    running = false;
    stopDecoder();
    startTs = 0;
    extractor.release();
  }

  public void reset(Surface surface) {
    boolean wasRunning = running;
    stopDecoder(!wasRunning);
    moveTo(0);
    prepare(surface);
    if (wasRunning) {
      start();
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
    stopDecoder(!wasRunning);
    prepare(surface);
    if (wasRunning) {
      start();
    }
  }

  protected void stopDecoder() {
    stopDecoder(true);
  }

  protected void stopDecoder(boolean clearTs) {
    running = false;
    if (clearTs) startTs = 0;
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
      if (codec != null) {
        try {
          codec.flush();
        } catch (IllegalStateException ignored) { }
      }
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
    synchronized (sync) {
      extractor.seekTo((long) (time * 10E5));
    }
  }

  public void setLoopMode(boolean loopMode) {
    this.loopMode = loopMode;
  }

  public boolean isLoopMode() {
    return loopMode;
  }

  public double getDuration() {
    if (duration < 0) return duration; //fail to extract duration from file.
    return duration / 10E5;
  }

  public double getTime() {
    if (running) {
      return extractor.getTimeStamp() / 10E5;
    } else {
      return 0;
    }
  }

  public boolean isRunning() {
    return running;
  }

  protected abstract boolean extract(Extractor extractor) throws IOException;

  protected abstract boolean decodeOutput(ByteBuffer outputBuffer, long timeStamp);

  protected abstract void finished();

  private void decode() {
    if (startTs == 0) {
      moveTo(0); //make sure that we are on the start
      startTs = TimeUtils.getCurrentTimeMicro();
    }
    long sleepTime = 0;
    while (running) {
      synchronized (sync) {
        if (pause.get()) continue;
        if (looped) {
          decoderInterface.onLoop();
          looped = false;
        }
        int inIndex = codec.dequeueInputBuffer(10000);
        int sampleSize;
        long timeStamp = TimeUtils.getCurrentTimeMicro();
        boolean finished = false;
        if (inIndex >= 0) {
          ByteBuffer input;
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            input = codec.getInputBuffer(inIndex);
          } else {
            input = codec.getInputBuffers()[inIndex];
          }
          if (input == null) continue;
          sampleSize = extractor.readFrame(input);
          long ts = TimeUtils.getCurrentTimeMicro() - startTs;
          sleepTime = extractor.getSleepTime(ts);
          finished = !extractor.advance();
          if (finished) {
            if (!loopMode) {
              codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
          } else {
            codec.queueInputBuffer(inIndex, 0, sampleSize, ts + sleepTime, 0);
          }
        }
        int outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000);
        if (outIndex >= 0) {
          if (!sleep(sleepTime)) return;
          ByteBuffer output;
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            output = codec.getOutputBuffer(outIndex);
          } else {
            output = codec.getOutputBuffers()[outIndex];
          }
          boolean render = decodeOutput(output, timeStamp);
          codec.releaseOutputBuffer(outIndex, render && bufferInfo.size != 0);
          if (finished) {
            if (loopMode) {
              looped = true;
            } else {
              Log.i(TAG, "end of file");
              finished();
            }
          }
        }
      }
    }
  }

  private boolean sleep(long sleepTime) {
    try {
      Thread.sleep(sleepTime);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  public void setExtractor(Extractor extractor) {
    this.extractor = extractor;
  }

  public Extractor getExtractor() {
    return extractor;
  }
}
