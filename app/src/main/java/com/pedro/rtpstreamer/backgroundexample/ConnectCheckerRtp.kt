/*
 * Copyright (C) 2021 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.rtpstreamer.backgroundexample

import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtsp.utils.ConnectCheckerRtsp

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