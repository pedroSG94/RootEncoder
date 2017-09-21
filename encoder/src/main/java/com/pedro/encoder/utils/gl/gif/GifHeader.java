/**
 * Copyright 2014 Google, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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