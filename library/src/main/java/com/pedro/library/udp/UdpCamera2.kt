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
package com.pedro.library.udp

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.library.base.Camera2Base
import com.pedro.library.util.streamclient.StreamClientListener
import com.pedro.library.util.streamclient.UdpStreamClient
import com.pedro.library.view.OpenGlView
import com.pedro.udp.UdpClient
import java.nio.ByteBuffer

/**
 * More documentation see:
 * [Camera2Base]
 *
 * Created by pedro on 6/3/24.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class UdpCamera2: Camera2Base {

  private val streamClientListener = object: StreamClientListener {
    override fun onRequestKeyframe() {
      requestKeyFrame()
    }
  }
  private lateinit var udpClient: UdpClient
  private lateinit var streamClient: UdpStreamClient

  constructor(openGlView: OpenGlView, connectChecker: ConnectChecker): super(openGlView) {
    init(connectChecker)
  }

  constructor(context: Context, connectChecker: ConnectChecker): super(context) {
    init(connectChecker)
  }

  private fun init(connectChecker: ConnectChecker) {
    udpClient = UdpClient(connectChecker)
    streamClient = UdpStreamClient(udpClient, streamClientListener)
  }

  override fun getStreamClient(): UdpStreamClient = streamClient

  override fun setVideoCodecImp(codec: VideoCodec) {
    udpClient.setVideoCodec(codec)
  }

  override fun setAudioCodecImp(codec: AudioCodec) {
    udpClient.setAudioCodec(codec)
  }

  override fun onAudioInfoImp(isStereo: Boolean, sampleRate: Int) {
    udpClient.setAudioInfo(sampleRate, isStereo)
  }

  override fun startStreamImp(url: String) {
    udpClient.connect(url)
  }

  override fun stopStreamImp() {
    udpClient.disconnect()
  }

  override fun getAudioDataImp(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    udpClient.sendAudio(audioBuffer, info)
  }

  override fun onVideoInfoImp(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    udpClient.setVideoInfo(sps, pps, vps)
  }

  override fun getVideoDataImp(videoBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    udpClient.sendVideo(videoBuffer, info)
  }
}
