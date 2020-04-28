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
    long timeDiff = System.currentTimeMillis() - timeStamp;
    if (timeDiff >= 1000) {
      connectCheckerRtsp.onNewBitrateRtsp((int) (bitrate / (timeDiff / 1000f)));
      timeStamp = System.currentTimeMillis();
      bitrate = 0;
    }
  }
}
