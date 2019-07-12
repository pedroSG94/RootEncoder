package com.pedro.rtplibrary.util;

/**
 * Created by pedro on 09/07/19.
 */
public class FpsListener {

  private int fpsCont = 0;
  private long ts = System.currentTimeMillis();
  private Callback callback;

  public interface Callback {
    void onFps(int fps);
  }

  public void setCallback(Callback callback) {
    this.callback = callback;
  }

  public void calculateFps() {
    fpsCont++;
    if (System.currentTimeMillis() - ts >= 1000) {
      if (callback != null) callback.onFps(fpsCont);
      fpsCont = 0;
      ts = System.currentTimeMillis();
    }
  }
}
