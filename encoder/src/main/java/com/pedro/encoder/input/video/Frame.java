package com.pedro.encoder.input.video;

import android.graphics.ImageFormat;

/**
 * Created by pedro on 17/02/18.
 */

public class Frame {

  private byte[] buffer;
  private boolean flip = false;
  private int format = ImageFormat.NV21; //nv21 or yv12 supported

  public Frame(byte[] buffer, boolean flip, int format) {
    this.buffer = buffer;
    this.flip = flip;
    this.format = format;
  }

  public byte[] getBuffer() {
    return buffer;
  }

  public void setBuffer(byte[] buffer) {
    this.buffer = buffer;
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
}
