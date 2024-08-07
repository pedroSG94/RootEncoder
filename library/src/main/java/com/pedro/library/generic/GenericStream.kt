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
import com.pedro.library.base.StreamBase
import com.pedro.library.util.sources.audio.AudioSource
import com.pedro.library.util.sources.audio.MicrophoneSource
import com.pedro.library.util.sources.video.Camera2Source
import com.pedro.library.util.sources.video.VideoSource
import com.pedro.library.util.streamclient.GenericStreamClient
import com.pedro.library.util.streamclient.RtmpStreamClient
import com.pedro.library.util.streamclient.RtspStreamClient
import com.pedro.library.util.streamclient.SrtStreamClient
import com.pedro.library.util.streamclient.StreamClientListener
import com.pedro.library.util.streamclient.UdpStreamClient
import com.pedro.rtmp.rtmp.RtmpClient
import com.pedro.rtsp.rtsp.RtspClient
import com.pedro.srt.srt.SrtClient
import com.pedro.udp.UdpClient
import java.nio.ByteBuffer

/**
 * Created by pedro on 14/3/22.
 *
 * If you use VideoManager.Source.SCREEN/AudioManager.Source.INTERNAL. Call
 * changeVideoSourceScreen/changeAudioSourceInternal is necessary to start it.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class GenericStream(
  context: Context,
  private val connectChecker: ConnectChecker,
  videoSource: VideoSource,
  audioSource: AudioSource
): StreamBase(context, videoSource, audioSource) {

  private val streamClientListener = object: StreamClientListener {
    override fun onRequestKeyframe() {
      requestKeyframe()
    }
  }
  private val rtmpClient = RtmpClient(connectChecker)
  private val rtspClient = RtspClient(connectChecker)
  private val srtClient = SrtClient(connectChecker)
  private val udpClient = UdpClient(connectChecker)
  private val streamClient = GenericStreamClient(
    RtmpStreamClient(rtmpClient, streamClientListener),
    RtspStreamClient(rtspClient, streamClientListener),
    SrtStreamClient(srtClient, streamClientListener),
    UdpStreamClient(udpClient, streamClientListener)
  )
  private var connectedType = ClientType.NONE

  constructor(context: Context, connectChecker: ConnectChecker):
      this(context, connectChecker, Camera2Source(context), MicrophoneSource())

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

  override fun onAudioInfoImp(sampleRate: Int, isStereo: Boolean) {
    rtmpClient.setAudioInfo(sampleRate, isStereo)
    rtspClient.setAudioInfo(sampleRate, isStereo)
    srtClient.setAudioInfo(sampleRate, isStereo)
    udpClient.setAudioInfo(sampleRate, isStereo)
  }

  override fun startStreamImp(endPoint: String) {
    streamClient.connecting(endPoint)
    if (endPoint.startsWith("rtmp", ignoreCase = true)) {
      connectedType = ClientType.RTMP
      val resolution = super.getVideoResolution()
      rtmpClient.setVideoResolution(resolution.width, resolution.height)
      rtmpClient.setFps(super.getVideoFps())
      rtmpClient.connect(endPoint)
    } else if (endPoint.startsWith("rtsp", ignoreCase = true)) {
      connectedType = ClientType.RTSP
      rtspClient.connect(endPoint)
    } else if (endPoint.startsWith("srt", ignoreCase = true)) {
      connectedType = ClientType.SRT
      srtClient.connect(endPoint)
    } else if (endPoint.startsWith("udp", ignoreCase = true)) {
      connectedType = ClientType.UDP
      udpClient.connect(endPoint)
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