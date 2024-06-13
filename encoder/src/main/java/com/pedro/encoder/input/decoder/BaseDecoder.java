/*
 * Copyright (C) 2023 pedroSG94.
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

import com.pedro.encoder.input.decoder.extractor.MultiMediaExtractor;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseDecoder {

  protected String TAG = "BaseDecoder";
  protected MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
  protected MultiMediaExtractor extractor;
  protected MediaCodec codec;
  protected volatile boolean running = false;
  protected MediaFormat mediaFormat;
  private HandlerThread handlerThread;
  protected String mime = "";
  protected boolean loopMode = false;
  private volatile long startTs = 0;
  protected long duration;
  protected final Object sync = new Object();
  private volatile long lastExtractorTs = 0;
  //Avoid decode while change output surface
  protected AtomicBoolean pause = new AtomicBoolean(false);
  protected volatile boolean looped = false;
  private final DecoderInterface decoderInterface;

  public BaseDecoder(DecoderInterface decoderInterface) {
    this.decoderInterface = decoderInterface;
  }

  public boolean initExtractor(List<String> filePath) throws IOException {
    extractor = new MultiMediaExtractor();
    extractor.setDataSource(filePath);
    return extract(extractor);
  }

  public boolean initExtractor(String filePath) throws IOException {
    extractor = new MultiMediaExtractor();
    extractor.setDataSource(List.of(filePath));
    return extract(extractor);
  }

  public boolean initExtractor(FileDescriptor fileDescriptor) throws IOException {
    extractor = new MultiMediaExtractor();
    extractor.setDataSource1(List.of(fileDescriptor));
    return extract(extractor);
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  public boolean initExtractor(AssetFileDescriptor assetFileDescriptor) throws IOException {
    extractor = new MultiMediaExtractor();
    extractor.setDataSource2(List.of(assetFileDescriptor));
    return extract(extractor);
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  public boolean initExtractor(MediaDataSource mediaDataSource) throws IOException {
    extractor = new MultiMediaExtractor();
    extractor.setDataSource3(List.of(mediaDataSource));
    return extract(extractor);
  }

  public boolean initExtractor(String filePath, Map<String, String> headers) throws IOException {
    extractor = new MultiMediaExtractor();
    List<Map<String, String>> h = new ArrayList<>();
    h.add(headers);
    extractor.setDataSource4(List.of(filePath), h);
    return extract(extractor);
  }

  public boolean initExtractor(FileDescriptor fileDescriptor, long offset, long length)
      throws IOException {
    extractor = new MultiMediaExtractor();
    extractor.setDataSource5(List.of(fileDescriptor), List.of(offset), List.of(length));
    return extract(extractor);
  }

  public boolean initExtractor(Context context, Uri uri, Map<String, String> headers)
      throws IOException {
    extractor = new MultiMediaExtractor();
    List<Map<String, String>> h = new ArrayList<>();
    h.add(headers);
    extractor.setDataSource6(context, List.of(uri), h);
    return extract(extractor);
  }

  public boolean initExtractor(Context context, List<Uri> uri, List<Map<String, String>> headers)
      throws IOException {
    extractor = new MultiMediaExtractor();
    extractor.setDataSource6(context, uri, headers);
    return extract(extractor);
  }

  public void start() {
    Log.i(TAG, "start decoder");
    running = true;
    handlerThread = new HandlerThread(TAG);
    handlerThread.start();
    Handler handler = new Handler(handlerThread.getLooper());
    codec.start();
    handler.post(() -> {
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
    lastExtractorTs = 0;
    startTs = 0;
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
    if (handlerThread != null) {
      if (handlerThread.getLooper() != null) {
        handlerThread.getLooper().getThread().interrupt();
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
    synchronized (sync) {
      extractor.seekTo((long) (time * 10E5), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
    }
  }

  public void setLoopMode(boolean loopMode) {
    this.loopMode = loopMode;
  }

  public boolean isLoopMode() {
    return loopMode;
  }

  public double getDuration() {
    return duration / 10E5;
  }

  public double getTime() {
    if (running) {
      long ts = System.nanoTime() / 1000 - startTs;
      return ts / 10E5;
    } else {
      return 0;
    }
  }

  protected abstract boolean extract(MultiMediaExtractor extractor);

  protected abstract boolean decodeOutput(ByteBuffer outputBuffer, long timeStamp);

  protected abstract void finished();

  private void decode() {
    if (startTs == 0) {
      moveTo(0); //make sure that we are on the start
      startTs = System.nanoTime() / 1000;
    }
    long sleepTime = 0;
    long accumulativeTs = 0;
    while (running) {
      synchronized (sync) {
        if (pause.get()) continue;
        if (looped) {
          if (extractor.isLastFrame()) {
            moveTo(0);
            continue;
          } else {
            decoderInterface.onLoop();
            looped = false;
          }
        }
        extractor.shouldReset(lastExtractorTs + sleepTime);
        int inIndex = codec.dequeueInputBuffer(10000);
        long timeStamp = System.nanoTime() / 1000;
        if (inIndex >= 0) {
          ByteBuffer input;
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            input = codec.getInputBuffer(inIndex);
          } else {
            input = codec.getInputBuffers()[inIndex];
          }
          if (input == null) continue;
          int sampleSize = extractor.readSampleData(input, 0);

          long ts = System.nanoTime() / 1000 - startTs;
          long extractorTs = extractor.getSampleTime();
          accumulativeTs += extractorTs - lastExtractorTs;
          lastExtractorTs = extractorTs;

          if (accumulativeTs > ts) sleepTime = (accumulativeTs - ts) / 1000;
          else sleepTime = 0;

          if (sampleSize < 0) {
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
          boolean finished = extractor.isLastFrame();
          if (finished) {
            if (loopMode) {
              moveTo(0);
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
}
