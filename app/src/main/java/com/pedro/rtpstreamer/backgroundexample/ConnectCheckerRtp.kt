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
  fun onConnectionSuccessRtp()

  fun onConnectionFailedRtp(reason: String)

  fun onDisconnectRtp()

  fun onAuthErrorRtp()

  fun onAuthSuccessRtp()

  /**
   * RTMP
   */
  override fun onConnectionSuccessRtmp() {
    onConnectionSuccessRtp()
  }

  override fun onConnectionFailedRtmp(reason: String) {
    onConnectionFailedRtp(reason)
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
  override fun onConnectionSuccessRtsp() {
    onConnectionSuccessRtp()
  }

  override fun onConnectionFailedRtsp(reason: String) {
    onConnectionFailedRtp(reason)
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