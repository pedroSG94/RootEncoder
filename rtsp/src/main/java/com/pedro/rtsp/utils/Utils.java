package com.pedro.rtsp.utils;

public class Utils {

  public static void setLong(byte[] buffer, long n, int begin, int end) {
    for (end--; end >= begin; end--) {
      buffer[end] = (byte) (n % 256);
      n >>= 8;
    }
  }

  public static void setLongSSRC(byte[] buffer, int ssrc) {
    setLong(buffer, ssrc, 8, 12);
  }
}
