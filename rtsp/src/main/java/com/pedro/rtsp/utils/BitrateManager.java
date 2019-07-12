package com.pedro.rtsp.utils;

/**
 * Created by pedro on 10/07/19.
 *
 * Calculate video and audio bitrate per second
 */
public class BitrateManager {

  private long bitrate;
  private long timeStamp = System.currentTimeMillis();
  private ConnectCheckerRtsp connectCheckerRtsp;

  public BitrateManager(ConnectCheckerRtsp connectCheckerRtsp) {
    this.connectCheckerRtsp = connectCheckerRtsp;
  }

  public synchronized void calculateBitrate(long size) {
    bitrate += size;
    if (System.currentTimeMillis() - timeStamp >= 1000) {
      connectCheckerRtsp.onNewBitrateRtsp(bitrate);
      timeStamp = System.currentTimeMillis();
      bitrate = 0;
    }
  }
}
