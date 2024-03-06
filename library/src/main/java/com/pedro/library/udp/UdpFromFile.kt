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
package com.pedro.library.udp

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoderInterface
import com.pedro.library.base.FromFileBase
import com.pedro.library.util.streamclient.StreamClientListener
import com.pedro.library.util.streamclient.UdpStreamClient
import com.pedro.library.view.OpenGlView
import com.pedro.udp.UdpClient
import java.nio.ByteBuffer

/**
 * More documentation see:
 * [FromFileBase]
 *
 * Created by pedro on 6/3/24.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class UdpFromFile: FromFileBase {

  private val streamClientListener = object: StreamClientListener {
    override fun onRequestKeyframe() {
      requestKeyFrame()
    }
  }
  private lateinit var udpClient: UdpClient
  private lateinit var streamClient: UdpStreamClient

  constructor(
    openGlView: OpenGlView, connectChecker: ConnectChecker,
    videoDecoderInterface: VideoDecoderInterface, audioDecoderInterface: AudioDecoderInterface
  ): super(openGlView, videoDecoderInterface, audioDecoderInterface) {
    init(connectChecker)
  }

  constructor(
    context: Context, connectChecker: ConnectChecker,
    videoDecoderInterface: VideoDecoderInterface, audioDecoderInterface: AudioDecoderInterface
  ): super(context, videoDecoderInterface, audioDecoderInterface) {
    init(connectChecker)
  }

  constructor(
    connectChecker: ConnectChecker,
    videoDecoderInterface: VideoDecoderInterface, audioDecoderInterface: AudioDecoderInterface
  ): super(videoDecoderInterface, audioDecoderInterface) {
    init(connectChecker)
  }

  private fun init(connectChecker: ConnectChecker) {
    udpClient = UdpClient(connectChecker)
    streamClient = UdpStreamClient(udpClient, streamClientListener)
  }

  override fun setVideoCodecImp(codec: VideoCodec) {
    udpClient.setVideoCodec(codec)
  }

  override fun setAudioCodecImp(codec: AudioCodec) {
    udpClient.setAudioCodec(codec)
  }

  override fun getStreamClient(): UdpStreamClient = streamClient

  override fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int) {
    udpClient.setAudioInfo(sampleRate, isStereo)
  }

  override fun startStreamRtp(url: String) {
    udpClient.connect(url)
  }

  override fun stopStreamRtp() {
    udpClient.disconnect()
  }

  override fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    udpClient.setVideoInfo(sps, pps, vps)
  }

  override fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    udpClient.sendVideo(h264Buffer, info)
  }

  override fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    udpClient.sendAudio(aacBuffer, info)
  }
}
