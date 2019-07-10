package com.pedro.rtplibrary.network;

@Deprecated
public class AdapterBitrateParser {

  public static long DELAY = 1000;
  public static long DIFFERENCE = 500;
  private static long cont = 0;
  public static int maxVideoBitrate = 0;

  public interface Callback {
    void onNewBitrate(int bitrate);
  }

  public static void parseBitrate(int oldBitrate, int bandwidth, Callback callback) {
    if (cont == 0) cont = System.currentTimeMillis();
    if (System.currentTimeMillis() - cont > DELAY) {
      cont = 0;
      if (oldBitrate / 1000 - bandwidth >= DIFFERENCE
          || maxVideoBitrate != 0 && oldBitrate / 1000 >= maxVideoBitrate) {
        callback.onNewBitrate((int) (oldBitrate - (DIFFERENCE * 1000)));
      } else if (oldBitrate / 1000 - bandwidth <= DIFFERENCE) {
        callback.onNewBitrate((int) (oldBitrate + (DIFFERENCE * 1000)));
      }
    }
  }

  public static void calculateMaxVideoBitrate(int resolutionValue) {
    maxVideoBitrate = (int) (1.65287774651705E-10 * Math.pow(resolutionValue, 2)
        + 0.002653652033201 * resolutionValue
        + 640.220156152395);
  }

  public static void reset() {
    DELAY = 1000;
    DIFFERENCE = 500;
    cont = 0;
    maxVideoBitrate = 0;
  }
}
