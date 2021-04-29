package com.pedro.rtpstreamer.backgroundexample

import com.pedro.rtsp.utils.ConnectCheckerRtsp
import net.ossrs.rtmp.ConnectCheckerRtmp

/**
 * (Only working in kotlin)
 * Mix both connect interfaces to support RTMP and RTSP in service with same code.
 */
interface ConnectCheckerRtp: ConnectCheckerRtmp, ConnectCheckerRtsp {

  /**
   * Commons
   */
  fun onConnectionStartedRtp(rtpUrl: String)

  fun onConnectionSuccessRtp()

  fun onConnectionFailedRtp(reason: String)

  fun onNewBitrateRtp(bitrate: Long)

  fun onDisconnectRtp()

  fun onAuthErrorRtp()

  fun onAuthSuccessRtp()

  /**
   * RTMP
   */
  override fun onConnectionStartedRtmp(rtmpUrl: String) {
    onConnectionStartedRtp(rtmpUrl)
  }

  override fun onConnectionSuccessRtmp() {
    onConnectionSuccessRtp()
  }

  override fun onConnectionFailedRtmp(reason: String) {
    onConnectionFailedRtp(reason)
  }

  override fun onNewBitrateRtmp(bitrate: Long) {
    onNewBitrateRtp(bitrate)
  }

  override fun onDisconnectRtmp() {
    onDisconnectRtp()
  }

  override fun onAuthErrorRtmp() {
    onAuthErrorRtp()
  }

  override fun onAuthSuccessRtmp() {
    onAuthSuccessRtp()
  }

  /**
   * RTSP
   */
  override fun onConnectionStartedRtsp(rtspUrl: String) {
    onConnectionStartedRtp(rtspUrl)
  }

  override fun onConnectionSuccessRtsp() {
    onConnectionSuccessRtp()
  }

  override fun onConnectionFailedRtsp(reason: String) {
    onConnectionFailedRtp(reason)
  }

  override fun onNewBitrateRtsp(bitrate: Long) {
    onNewBitrateRtp(bitrate)
  }

  override fun onDisconnectRtsp() {
    onDisconnectRtp()
  }

  override fun onAuthErrorRtsp() {
    onAuthErrorRtp()
  }

  override fun onAuthSuccessRtsp() {
    onAuthSuccessRtp()
  }
}