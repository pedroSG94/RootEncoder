package net.ossrs.rtmp;

/**
 * Created by pedro on 25/01/17.
 */
public interface ConnectCheckerRtmp {

  void onConnectionStarted(String rtmpUrl);

  void onConnectionSuccessRtmp();

  void onConnectionFailedRtmp(String reason);

  void onNewBitrateRtmp(long bitrate);

  void onDisconnectRtmp();

  void onAuthErrorRtmp();

  void onAuthSuccessRtmp();
}
