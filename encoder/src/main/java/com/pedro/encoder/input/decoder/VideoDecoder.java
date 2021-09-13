/*
 * Copyright (C) 2021 pedroSG94.
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

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 20/06/17.
 */
public class VideoDecoder extends BaseDecoder {

  private VideoDecoderInterface videoDecoderInterface;
  private int width;
  private int height;

  public VideoDecoder(VideoDecoderInterface videoDecoderInterface,
      LoopFileInterface loopFileInterface) {
    super(loopFileInterface);
    this.videoDecoderInterface = videoDecoderInterface;
  }

  @Override
  protected boolean extract(MediaExtractor videoExtractor) {
    running = false;
    for (int i = 0; i < videoExtractor.getTrackCount() && !mime.startsWith("video/"); i++) {
      mediaFormat = videoExtractor.getTrackFormat(i);
      mime = mediaFormat.getString(MediaFormat.KEY_MIME);
      if (mime.startsWith("video/")) {
        videoExtractor.selectTrack(i);
      } else {
        mediaFormat = null;
      }
    }
    if (mediaFormat != null) {
      width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
      height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
      duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
      return true;
      //video decoder not supported
    } else {
      mime = "";
      mediaFormat = null;
      return false;
    }
  }

  public boolean prepareVideo(Surface surface) {
    return prepare(surface);
  }

  public void reset(Surface surface) {
    resetCodec(surface);
  }

  @Override
  protected void decode() {
    ByteBuffer[] inputBuffers = codec.getInputBuffers();
    startMs = System.currentTimeMillis();
    while (running) {
      int inIndex = codec.dequeueInputBuffer(10000);
      if (inIndex >= 0) {
        ByteBuffer buffer = inputBuffers[inIndex];
        int sampleSize = extractor.readSampleData(buffer, 0);
        if (sampleSize < 0) {
          codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        } else {
          codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
          extractor.advance();
        }
      }
      int outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000);
      if (outIndex >= 0) {
        while (extractor.getSampleTime() / 1000 > System.currentTimeMillis() - startMs + seekTime) {
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
          }
        }
        codec.releaseOutputBuffer(outIndex, bufferInfo.size != 0);
      }
    }
    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
      seekTime = 0;
      Log.i(TAG, "end of file out");
      if (loopMode) {
        loopFileInterface.onReset(true);
      } else {
        videoDecoderInterface.onVideoDecoderFinished();
      }
    }
  }

  public void changeOutputSurface(Surface surface) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      codec.setOutputSurface(surface);
    } else {
      reset(surface);
    }
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }
}
