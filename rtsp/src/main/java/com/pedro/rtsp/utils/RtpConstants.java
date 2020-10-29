package com.pedro.rtsp.utils;

/**
 * Created by pedro on 19/02/17.
 */

public class RtpConstants {
  public static final Object lock = new Object();
  public static final long clockVideoFrequency = 90000L;
  public static final int RTP_HEADER_LENGTH = 12;
  public static final int MTU = 1300;
  public static final int payloadType = 96;
  //H264 IDR
  public static final int IDR = 5;
  //H265 IDR
  public static final int IDR_N_LP = 20;
  public static final int IDR_W_DLP = 19;
}
