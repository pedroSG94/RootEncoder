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

package com.pedro.encoder;

import android.graphics.ImageFormat;

/**
 * Created by pedro on 17/02/18.
 */

public class Frame {

  private byte[] buffer;
  private int offset;
  private int size;
  private int orientation;
  private boolean flip;
  private int format = ImageFormat.NV21; //nv21 or yv12 supported

  /**
   * Used with video frame
   */
  public Frame(byte[] buffer, int orientation, boolean flip, int format) {
    this.buffer = buffer;
    this.orientation = orientation;
    this.flip = flip;
    this.format = format;
    offset = 0;
    size = buffer.length;
  }

  /**
   * Used with audio frame
   */
  public Frame(byte[] buffer, int offset, int size) {
    this.buffer = buffer;
    this.offset = offset;
    this.size = size;
  }

  public byte[] getBuffer() {
    return buffer;
  }

  public void setBuffer(byte[] buffer) {
    this.buffer = buffer;
  }

  public int getOrientation() {
    return orientation;
  }

  public void setOrientation(int orientation) {
    this.orientation = orientation;
  }

  public boolean isFlip() {
    return flip;
  }

  public void setFlip(boolean flip) {
    this.flip = flip;
  }

  public int getFormat() {
    return format;
  }

  public void setFormat(int format) {
    this.format = format;
  }

  public int getOffset() {
    return offset;
  }

  public void setOffset(int offset) {
    this.offset = offset;
  }

  public int getSize() {
    return size;
  }

  public void setSize(int size) {
    this.size = size;
  }
}
