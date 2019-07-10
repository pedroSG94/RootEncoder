package net.ossrs.rtmp;

/**
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
    if (System.currentTimeMillis() - timeStamp >= 1000) {
      connectCheckerRtmp.onNewBitrateRtmp(bitrate);
      timeStamp = System.currentTimeMillis();
      bitrate = 0;
    }
  }
}
