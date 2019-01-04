package com.pedro.encoder.input.video;

/**
 * Created by pedro on 11/10/18.
 */

public class FpsLimiter {

  private long lastFrameTimestamp = 0L;

  public boolean limitFPS(int fps) {
    if (System.currentTimeMillis() - lastFrameTimestamp > 1000 / fps) {
      lastFrameTimestamp = System.currentTimeMillis();
      return false;
    }
    return true;
  }

  public void reset() {
    lastFrameTimestamp = 0;
  }
}
