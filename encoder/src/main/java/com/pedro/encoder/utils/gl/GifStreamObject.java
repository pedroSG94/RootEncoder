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

package com.pedro.encoder.utils.gl;

import android.graphics.Bitmap;
import android.util.Log;

import com.pedro.encoder.utils.gl.gif.GifDecoder;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by pedro on 22/09/17.
 */

public class GifStreamObject extends StreamObjectBase {

  private static final String TAG = "GifStreamObject";

  private int numFrames;
  private Bitmap[] gifBitmaps;
  private int[] gifDelayFrames;
  private long startDelayFrame;
  private int currentGifFrame;

  public GifStreamObject() {
  }

  @Override
  public int getWidth() {
    return gifBitmaps != null ? gifBitmaps[0].getWidth() : 0;
  }

  @Override
  public int getHeight() {
    return gifBitmaps != null ? gifBitmaps[0].getHeight() : 0;
  }

  public void load(InputStream inputStreamGif) throws IOException {
    GifDecoder gifDecoder = new GifDecoder();
    if (gifDecoder.read(inputStreamGif, inputStreamGif.available()) == 0) {
      Log.i(TAG, "read gif ok");
      numFrames = gifDecoder.getFrameCount();
      gifDelayFrames = new int[numFrames];
      gifBitmaps = new Bitmap[numFrames];
      for (int i = 0; i < numFrames; i++) {
        gifDecoder.advance();
        gifBitmaps[i] = gifDecoder.getNextFrame();
        gifDelayFrames[i] = gifDecoder.getNextDelay();
      }
      Log.i(TAG, "finish load gif frames");
    } else {
      throw new IOException("Read gif error");
    }
  }

  @Override
  public void recycle() {
    if (gifBitmaps != null) {
      for (int i = 0; i < numFrames; i++) {
        if (gifBitmaps[i] != null && !gifBitmaps[i].isRecycled()) gifBitmaps[i].recycle();
      }
    }
  }

  @Override
  public int getNumFrames() {
    return numFrames;
  }

  @Override
  public Bitmap[] getBitmaps() {
    return gifBitmaps;
  }

  public int[] getGifDelayFrames() {
    return gifDelayFrames;
  }

  public int updateFrame(int size) {
    return size <= 1 ? 0 : updateFrame();
  }

  @Override
  public int updateFrame() {
    if (startDelayFrame == 0) {
      startDelayFrame = System.currentTimeMillis();
    }
    if (System.currentTimeMillis() - startDelayFrame >= gifDelayFrames[currentGifFrame]) {
      if (currentGifFrame >= numFrames - 1) {
        currentGifFrame = 0;
      } else {
        currentGifFrame++;
      }
      startDelayFrame = 0;
    }
    return currentGifFrame;
  }
}
