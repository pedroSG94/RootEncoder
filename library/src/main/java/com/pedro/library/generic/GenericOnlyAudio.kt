/*
 * Copyright (C) 2024 pedroSG94.
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
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.onMainThreadHandler
import com.pedro.library.base.OnlyAudioBase
import com.pedro.library.util.streamclient.GenericStreamClient
import com.pedro.library.util.streamclient.RtmpStreamClient
import com.pedro.library.util.streamclient.RtspStreamClient
import com.pedro.library.util.streamclient.SrtStreamClient
import com.pedro.library.util.streamclient.UdpStreamClient
import com.pedro.rtmp.rtmp.RtmpClient
import com.pedro.rtsp.rtsp.RtspClient
import com.pedro.srt.srt.SrtClient
import com.pedro.udp.UdpClient
import java.nio.ByteBuffer

class GenericOnlyAudio(private val connectChecker: ConnectChecker): OnlyAudioBase() {

  private val rtmpClient = RtmpClient(connectChecker)
  private val rtspClient = RtspClient(connectChecker)
  private val srtClient = SrtClient(connectChecker)
  private val udpClient = UdpClient(connectChecker)
  private val streamClient = GenericStreamClient(
    RtmpStreamClient(rtmpClient, null),
    RtspStreamClient(rtspClient, null),
    SrtStreamClient(srtClient, null),
    UdpStreamClient(udpClient, null)
  ).apply {
    setOnlyAudio(true)
  }
  private var connectedType = ClientType.NONE

  override fun getStreamClient(): GenericStreamClient = streamClient

  override fun setAudioCodecImp(codec: AudioCodec) {
    if (codec != AudioCodec.AAC) {
      throw IllegalArgumentException("Unsupported codec: ${codec.name}. Generic only support audio ${AudioCodec.AAC.name}")
    }
    rtmpClient.setAudioCodec(codec)
    rtspClient.setAudioCodec(codec)
    srtClient.setAudioCodec(codec)
    udpClient.setAudioCodec(codec)
  }

  override fun onAudioInfoImp(isStereo: Boolean, sampleRate: Int) {
    rtmpClient.setAudioInfo(sampleRate, isStereo)
    rtspClient.setAudioInfo(sampleRate, isStereo)
    srtClient.setAudioInfo(sampleRate, isStereo)
    udpClient.setAudioInfo(sampleRate, isStereo)
  }

  override fun startStreamImp(url: String) {
    streamClient.connecting(url)
    if (url.startsWith("rtmp", ignoreCase = true)) {
      connectedType = ClientType.RTMP
      rtmpClient.connect(url)
    } else if (url.startsWith("rtsp", ignoreCase = true)) {
      connectedType = ClientType.RTSP
      rtspClient.connect(url)
    } else if (url.startsWith("srt", ignoreCase = true)) {
      connectedType = ClientType.SRT
      srtClient.connect(url)
    } else if (url.startsWith("udp", ignoreCase = true)) {
      connectedType = ClientType.UDP
      udpClient.connect(url)
    } else {
      onMainThreadHandler {
        connectChecker.onConnectionFailed("Unsupported protocol. Only support rtmp, rtsp and srt")
      }
    }
  }

  override fun stopStreamImp() {
    when (connectedType) {
      ClientType.RTMP -> rtmpClient.disconnect()
      ClientType.RTSP -> rtspClient.disconnect()
      ClientType.SRT -> srtClient.disconnect()
      ClientType.UDP -> udpClient.disconnect()
      else -> {}
    }
    connectedType = ClientType.NONE
  }

  override fun getAudioDataImp(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    when (connectedType) {
      ClientType.RTMP -> rtmpClient.sendAudio(audioBuffer, info)
      ClientType.RTSP -> rtspClient.sendAudio(audioBuffer, info)
      ClientType.SRT -> srtClient.sendAudio(audioBuffer, info)
      ClientType.UDP -> udpClient.sendAudio(audioBuffer, info)
      else -> {}
    }
  }
}