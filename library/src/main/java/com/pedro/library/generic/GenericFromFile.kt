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

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.common.onMainThreadHandler
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoderInterface
import com.pedro.library.base.FromFileBase
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

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class GenericFromFile: FromFileBase {

  private val streamClientListener = object: StreamClientListener {
    override fun onRequestKeyframe() {
      requestKeyFrame()
    }
  }
  private lateinit var connectChecker: ConnectChecker
  private lateinit var rtmpClient: RtmpClient
  private lateinit var rtspClient: RtspClient
  private lateinit var srtClient: SrtClient
  private lateinit var udpClient: UdpClient
  private lateinit var streamClient: GenericStreamClient
  private var connectedType = ClientType.NONE

  constructor(
    openGlView: OpenGlView, connectChecker: ConnectChecker,
    videoDecoderInterface: VideoDecoderInterface, audioDecoderInterface: AudioDecoderInterface
  ) : super(openGlView, videoDecoderInterface, audioDecoderInterface) {
    init(connectChecker)
  }

  constructor(
    context: Context, connectChecker: ConnectChecker,
    videoDecoderInterface: VideoDecoderInterface, audioDecoderInterface: AudioDecoderInterface
  ) : super(context, videoDecoderInterface, audioDecoderInterface) {
    init(connectChecker)
  }

  constructor(
    connectChecker: ConnectChecker,
    videoDecoderInterface: VideoDecoderInterface, audioDecoderInterface: AudioDecoderInterface
  ) : super(videoDecoderInterface, audioDecoderInterface) {
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

  override fun getStreamClient(): GenericStreamClient = streamClient

  override fun setVideoCodecImp(codec: VideoCodec) {
    if (codec != VideoCodec.H264 && codec != VideoCodec.H265) {
      throw IllegalArgumentException("Unsupported codec: ${codec.name}. Generic only support video ${VideoCodec.H264.name} and ${VideoCodec.H265.name}")
    }
    rtmpClient.setVideoCodec(codec)
    rtspClient.setVideoCodec(codec)
    srtClient.setVideoCodec(codec)
    udpClient.setVideoCodec(codec)
  }

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
      if (videoEncoder.rotation == 90 || videoEncoder.rotation == 270) {
        rtmpClient.setVideoResolution(videoEncoder.height, videoEncoder.width)
      } else {
        rtmpClient.setVideoResolution(videoEncoder.width, videoEncoder.height)
      }
      rtmpClient.setFps(videoEncoder.fps)
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

  override fun onVideoInfoImp(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    rtmpClient.setVideoInfo(sps, pps, vps)
    rtspClient.setVideoInfo(sps, pps, vps)
    srtClient.setVideoInfo(sps, pps, vps)
    udpClient.setVideoInfo(sps, pps, vps)
  }

  override fun getVideoDataImp(videoBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    when (connectedType) {
      ClientType.RTMP -> rtmpClient.sendVideo(videoBuffer, info)
      ClientType.RTSP -> rtspClient.sendVideo(videoBuffer, info)
      ClientType.SRT -> srtClient.sendVideo(videoBuffer, info)
      ClientType.UDP -> udpClient.sendVideo(videoBuffer, info)
      else -> {}
    }
  }
}