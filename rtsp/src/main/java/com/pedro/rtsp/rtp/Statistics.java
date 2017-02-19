package com.pedro.rtsp.rtp;

/**
 * Created by pedro on 19/02/17.
 *
 * Computes the proper rate at which packets are sent.
 */

public class Statistics {

  public final static String TAG = "Statistics";

  private int count = 500, c = 0;
  private float m = 0, q = 0;
  private long elapsed = 0;
  private long start = 0;
  private long duration = 0;
  private long period = 6000000000L;
  private boolean initOffset = false;

  public Statistics(int count, long period) {
    this.count = count;
    this.period = period * 1000000L;
  }

  public void push(long value) {
    duration += value;
    elapsed += value;
    if (elapsed > period) {
      elapsed = 0;
      long now = System.nanoTime();
      if (!initOffset || (now - start < 0)) {
        start = now;
        duration = 0;
        initOffset = true;
      }
      value -= (now - start) - duration;
      //Log.d(TAG, "sum1: "+duration/1000000+" sum2: "+(now-start)/1000000+" drift: "+((now-start)-duration)/1000000+" v: "+value/1000000);
    }
    if (c < 40) {
      // We ignore the first 40 measured values because they may not be accurate
      c++;
      m = value;
    } else {
      m = (m * q + value) / (q + 1);
      if (q < count) q++;
    }
  }

  public long average() {
    long l = (long) m - 2000000;
    return l > 0 ? l : 0;
  }
}
