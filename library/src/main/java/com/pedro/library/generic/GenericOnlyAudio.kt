/*
 * Copyright (C) 2023 pedroSG94.
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
package com.pedro.library.generic

import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.common.ConnectChecker
import com.pedro.library.base.OnlyAudioBase
import com.pedro.library.util.streamclient.GenericStreamClient
import com.pedro.library.util.streamclient.RtmpStreamClient
import com.pedro.library.util.streamclient.RtspStreamClient
import com.pedro.library.util.streamclient.SrtStreamClient
import com.pedro.library.util.streamclient.StreamClientListener
import com.pedro.rtmp.rtmp.RtmpClient
import com.pedro.rtsp.rtsp.RtspClient
import com.pedro.srt.srt.SrtClient
import java.nio.ByteBuffer
import java.util.Locale

/**
 * Created by Ernovation on 9/11/21.
 *
 *
 * Experiment class.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class GenericOnlyAudio(private val connectChecker: ConnectChecker): OnlyAudioBase() {

  private val streamClientListener = object: StreamClientListener {
    override fun onRequestKeyframe() {}
  }
  private val rtmpClient = RtmpClient(connectChecker)
  private val rtspClient = RtspClient(connectChecker)
  private val srtClient = SrtClient(connectChecker)
  private val streamClient = GenericStreamClient(
    RtmpStreamClient(rtmpClient, streamClientListener),
    RtspStreamClient(rtspClient, streamClientListener),
    SrtStreamClient(srtClient, streamClientListener),
    streamClientListener
  ).apply {
    setOnlyAudio(true)
  }
  private var connectedType = ClientType.NONE

  override fun getStreamClient(): GenericStreamClient = streamClient

  override fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int) {
    rtmpClient.setAudioInfo(sampleRate, isStereo)
    rtspClient.setAudioInfo(sampleRate, isStereo)
    srtClient.setAudioInfo(sampleRate, isStereo)
  }

  override fun startStreamRtp(url: String) {
    streamClient.connecting(url)
    if (url.lowercase(Locale.getDefault()).startsWith("rtmp")) {
      connectedType = ClientType.RTMP
      startStreamRtpRtmp(url)
    } else if (url.lowercase(Locale.getDefault()).startsWith("rtsp")) {
      connectedType = ClientType.RTSP
      startStreamRtpRtsp(url)
    } else if (url.lowercase(Locale.getDefault()).startsWith("srt")) {
      connectedType = ClientType.SRT
      startStreamRtpSrt(url)
    } else {
      connectChecker.onConnectionFailed("unsupported protocol, only support rtmp, rtsp and srt")
    }
  }

  private fun startStreamRtpRtmp(url: String) {
    rtmpClient.connect(url)
  }

  private fun startStreamRtpRtsp(url: String) {
    rtspClient.connect(url)
  }

  private fun startStreamRtpSrt(url: String) {
    srtClient.connect(url)
  }

  override fun stopStreamRtp() {
    when (connectedType) {
      ClientType.RTMP -> rtmpClient.disconnect()
      ClientType.RTSP -> rtspClient.disconnect()
      ClientType.SRT -> srtClient.disconnect()
      else -> {}
    }
    connectedType = ClientType.NONE
  }

  override fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    when (connectedType) {
      ClientType.RTMP -> rtmpClient.sendAudio(aacBuffer, info)
      ClientType.RTSP -> rtspClient.sendAudio(aacBuffer, info)
      ClientType.SRT -> srtClient.sendAudio(aacBuffer, info)
      else -> {}
    }
  }
}