package com.pedro.rtsp.utils

/**
 * Created by pedro on 20/02/17.
 */
interface ConnectCheckerRtsp {
  fun onConnectionStartedRtsp(rtspUrl: String)
  fun onConnectionSuccessRtsp()
  fun onConnectionFailedRtsp(reason: String)
  fun onNewBitrateRtsp(bitrate: Long)
  fun onDisconnectRtsp()
  fun onAuthErrorRtsp()
  fun onAuthSuccessRtsp()
}