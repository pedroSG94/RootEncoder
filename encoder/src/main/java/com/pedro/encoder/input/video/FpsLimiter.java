package com.pedro.encoder.input.video;

/**
 * Created by pedro on 11/10/18.
 */

public class FpsLimiter {

  private long startTS = System.currentTimeMillis();
  private long ratioF = 1000 / 30;
  private long ratio = 1000 / 30;

  public void setFPS(int fps) {
    startTS = System.currentTimeMillis();
    ratioF = 1000 / fps;
    ratio = 1000 / fps;
  }

  public boolean limitFPS() {
    long lastFrameTimestamp = System.currentTimeMillis() - startTS;
    if (ratio < lastFrameTimestamp) {
      ratio += ratioF;
      return false;
    }
    return true;
  }
}
