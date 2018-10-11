package com.pedro.encoder.input.video;

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
