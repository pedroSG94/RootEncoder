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

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import java.nio.ByteBuffer;

/**
 * Created by pedro on 20/06/17.
 */
public class VideoDecoder extends BaseDecoder {

  private final VideoDecoderInterface videoDecoderInterface;
  private int width;
  private int height;
  private int fps;

  public VideoDecoder(VideoDecoderInterface videoDecoderInterface, DecoderInterface decoderInterface) {
    super(decoderInterface);
    TAG = "VideoDecoder";
    this.videoDecoderInterface = videoDecoderInterface;
  }

  @Override
  protected boolean extract(MediaExtractor videoExtractor) {
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
      fps = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
      return true;
      //video decoder not supported
    } else {
      mime = "";
      return false;
    }
  }

  public boolean prepareVideo(Surface surface) {
    return prepare(surface);
  }

  @Override
  protected boolean decodeOutput(ByteBuffer outputBuffer, long timeStamp) {
    return true;
  }

  @Override
  protected void finished() {
    videoDecoderInterface.onVideoDecoderFinished();
  }

  public void changeOutputSurface(Surface surface) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      codec.setOutputSurface(surface);
    } else {
      resetCodec(surface);
    }
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public int getFps() {
    return fps;
  }

  public void pauseRender() {
    synchronized (sync) {
      pause.set(true);
    }
  }

  public void resumeRender() {
    synchronized (sync) {
      pause.set(false);
    }
  }
}
