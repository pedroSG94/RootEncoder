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
package com.pedro.encoder.utils.gl.gif;

import java.util.ArrayList;
import java.util.List;

/**
 * A header object containing the number of frames in an animated GIF image as well as basic
 * metadata like width and height that can be used to decode each individual frame of the GIF. Can
 * be shared by one or more {@link GifDecoder}s to play the same animated GIF in multiple views.
 */
public class GifHeader {

  int[] gct = null;
  int status = GifDecoder.STATUS_OK;
  int frameCount = 0;

  GifFrame currentFrame;
  List<GifFrame> frames = new ArrayList<>();
  // Logical screen size.
  // Full image width.
  int width;
  // Full image height.
  int height;

  // 1 : global color table flag.
  boolean gctFlag;
  // 2-4 : color resolution.
  // 5 : gct sort flag.
  // 6-8 : gct size.
  int gctSize;
  // Background color index.
  int bgIndex;
  // Pixel aspect ratio.
  int pixelAspect;
  //TODO: this is set both during reading the header and while decoding frames...
  int bgColor;
  int loopCount = 0;

  public int getHeight() {
    return height;
  }

  public int getWidth() {
    return width;
  }

  public int getNumFrames() {
    return frameCount;
  }

  /**
   * Global status code of GIF data parsing.
   */
  public int getStatus() {
    return status;
  }
}