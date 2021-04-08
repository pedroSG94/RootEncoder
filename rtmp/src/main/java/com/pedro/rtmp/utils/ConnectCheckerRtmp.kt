package com.pedro.rtmp.utils

/**
 * Created by pedro on 8/04/21.
 */
interface ConnectCheckerRtmp {
  fun onConnectionSuccessRtmp()
  fun onConnectionFailedRtmp(reason: String?)
  fun onNewBitrateRtmp(bitrate: Long)
  fun onDisconnectRtmp()
  fun onAuthErrorRtmp()
  fun onAuthSuccessRtmp()
}