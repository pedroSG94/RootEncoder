package com.pedro.rtsp.utils;

/**
 * Created by pedro on 20/02/17.
 */

public interface ConnectCheckerRtsp {

  void onConnectionSuccessRtsp();

  void onConnectionFailedRtsp();

  void onDisconnectRtsp();

  void onAuthErrorRtsp();

  void onAuthSuccessRtsp();
}
