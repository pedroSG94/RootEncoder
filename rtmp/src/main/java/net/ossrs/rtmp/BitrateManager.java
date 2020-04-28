package net.ossrs.rtmp;

/**
 * Created by pedro on 10/07/19.
 *
 * Calculate video and audio bitrate per second
 */
public class BitrateManager {

  private long bitrate;
  private long timeStamp = System.currentTimeMillis();
  private ConnectCheckerRtmp connectCheckerRtmp;

  public BitrateManager(ConnectCheckerRtmp connectCheckerRtsp) {
    this.connectCheckerRtmp = connectCheckerRtsp;
  }

  public synchronized void calculateBitrate(long size) {
    bitrate += size;
    long timeDiff = System.currentTimeMillis() - timeStamp;
    if (timeDiff >= 1000) {
      connectCheckerRtmp.onNewBitrateRtmp((int) (bitrate / (timeDiff / 1000f)));
      timeStamp = System.currentTimeMillis();
      bitrate = 0;
    }
  }
}
