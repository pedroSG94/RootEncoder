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

import android.os.Build;
import android.view.Surface;

import com.pedro.common.frame.MediaFrame;
import com.pedro.encoder.utils.CodecUtil;

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
    typeError = CodecUtil.CodecTypeError.VIDEO_CODEC;
  }

  @Override
  protected boolean extract(Extractor extractor) {
    try {
      mime = extractor.selectTrack(MediaFrame.Type.VIDEO);
      VideoInfo info = extractor.getVideoInfo();
      mediaFormat = extractor.getFormat();
      this.width = info.getWidth();
      this.height = info.getHeight();
      this.duration = info.getDuration();
      this.fps = info.getFps();
      return true;
    } catch (Exception e) {
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
