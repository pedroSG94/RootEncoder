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

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.RequiresApi
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.common.onMainThreadHandler
import com.pedro.library.base.Camera1Base
import com.pedro.library.util.streamclient.GenericStreamClient
import com.pedro.library.util.streamclient.RtmpStreamClient
import com.pedro.library.util.streamclient.RtspStreamClient
import com.pedro.library.util.streamclient.SrtStreamClient
import com.pedro.library.util.streamclient.StreamClientListener
import com.pedro.library.util.streamclient.UdpStreamClient
import com.pedro.library.view.OpenGlView
import com.pedro.rtmp.rtmp.RtmpClient
import com.pedro.rtsp.rtsp.RtspClient
import com.pedro.srt.srt.SrtClient
import com.pedro.udp.UdpClient
import java.nio.ByteBuffer
import java.util.Locale

/**
 * Created by Ernovation on 9/11/21.
 *
 */
class GenericCamera1: Camera1Base {

  private val streamClientListener = object: StreamClientListener {
    override fun onRequestKeyframe() {
      requestKeyFrame()
    }
  }
  private lateinit var rtmpClient: RtmpClient
  private lateinit var rtspClient: RtspClient
  private lateinit var srtClient: SrtClient
  private lateinit var udpClient: UdpClient
  private lateinit var streamClient: GenericStreamClient
  private lateinit var connectChecker: ConnectChecker
  private var connectedType = ClientType.NONE

  constructor(surfaceView: SurfaceView, connectChecker: ConnectChecker) : super(surfaceView) {
    init(connectChecker)
  }

  constructor(textureView: TextureView, connectChecker: ConnectChecker) : super(textureView) {
    init(connectChecker)
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  constructor(openGlView: OpenGlView, connectChecker: ConnectChecker) : super(openGlView) {
    init(connectChecker)
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  constructor(context: Context, connectChecker: ConnectChecker) : super(context) {
    init(connectChecker)
  }

  private fun init(connectChecker: ConnectChecker) {
    this.connectChecker = connectChecker
    rtmpClient = RtmpClient(connectChecker)
    rtspClient = RtspClient(connectChecker)
    srtClient = SrtClient(connectChecker)
    udpClient = UdpClient(connectChecker)
    streamClient = GenericStreamClient(
      RtmpStreamClient(rtmpClient, streamClientListener),
      RtspStreamClient(rtspClient, streamClientListener),
      SrtStreamClient(srtClient, streamClientListener),
      UdpStreamClient(udpClient, streamClientListener),
    )
  }

  override fun getStreamClient(): GenericStreamClient {
    return streamClient
  }

  override fun setVideoCodecImp(codec: VideoCodec) {
    require(!(codec != VideoCodec.H264 && codec != VideoCodec.H265)) {
      "Unsupported codec: " + codec.name + ". Generic only support video " + VideoCodec.H264.name + " and " + VideoCodec.H265.name
    }
    rtmpClient.setVideoCodec(codec)
    rtspClient.setVideoCodec(codec)
    srtClient.setVideoCodec(codec)
    udpClient.setVideoCodec(codec)
  }

  override fun setAudioCodecImp(codec: AudioCodec) {
    require(codec == AudioCodec.AAC) {
      "Unsupported codec: " + codec.name + ". Generic only support audio " + AudioCodec.AAC.name
    }
    rtmpClient.setAudioCodec(codec)
    rtspClient.setAudioCodec(codec)
    srtClient.setAudioCodec(codec)
    udpClient.setAudioCodec(codec)
  }

  override fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int) {
    rtmpClient.setAudioInfo(sampleRate, isStereo)
    rtspClient.setAudioInfo(sampleRate, isStereo)
    srtClient.setAudioInfo(sampleRate, isStereo)
    udpClient.setAudioInfo(sampleRate, isStereo)
  }

  override fun startStreamRtp(url: String) {
    streamClient.connecting(url)
    if (url.lowercase(Locale.getDefault()).startsWith("rtmp")) {
      connectedType = ClientType.RTMP
      if (videoEncoder.rotation == 90 || videoEncoder.rotation == 270) {
        rtmpClient.setVideoResolution(videoEncoder.height, videoEncoder.width)
      } else {
        rtmpClient.setVideoResolution(videoEncoder.width, videoEncoder.height)
      }
      rtmpClient.setFps(videoEncoder.fps)
      rtmpClient.connect(url)
    } else if (url.lowercase(Locale.getDefault()).startsWith("rtsp")) {
      connectedType = ClientType.RTSP
      rtspClient.connect(url)
    } else if (url.lowercase(Locale.getDefault()).startsWith("srt")) {
      connectedType = ClientType.SRT
      srtClient.connect(url)
    } else if (url.lowercase(Locale.getDefault()).startsWith("udp")) {
      connectedType = ClientType.UDP
      udpClient.connect(url)
    } else {
      onMainThreadHandler {
        connectChecker.onConnectionFailed("Unsupported protocol. Only support rtmp, rtsp and srt")
      }
    }
  }

  override fun stopStreamRtp() {
    when (connectedType) {
      ClientType.RTMP -> rtmpClient.disconnect()
      ClientType.RTSP -> rtspClient.disconnect()
      ClientType.SRT -> srtClient.disconnect()
      ClientType.UDP -> udpClient.disconnect()
      else -> {}
    }
    connectedType = ClientType.NONE
  }

  override fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    when (connectedType) {
      ClientType.RTMP -> rtmpClient.sendAudio(aacBuffer, info)
      ClientType.RTSP -> rtspClient.sendAudio(aacBuffer, info)
      ClientType.SRT -> srtClient.sendAudio(aacBuffer, info)
      ClientType.UDP -> udpClient.sendAudio(aacBuffer, info)
      else -> {}
    }
  }

  override fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    rtmpClient.setVideoInfo(sps, pps, vps)
    rtspClient.setVideoInfo(sps, pps, vps)
    srtClient.setVideoInfo(sps, pps, vps)
    udpClient.setVideoInfo(sps, pps, vps)
  }

  override fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    when (connectedType) {
      ClientType.RTMP -> rtmpClient.sendVideo(h264Buffer, info)
      ClientType.RTSP -> rtspClient.sendVideo(h264Buffer, info)
      ClientType.SRT -> srtClient.sendVideo(h264Buffer, info)
      ClientType.UDP -> udpClient.sendVideo(h264Buffer, info)
      else -> {}
    }
  }
}
