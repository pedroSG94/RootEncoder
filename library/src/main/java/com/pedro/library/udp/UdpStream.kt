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
import com.pedro.library.base.StreamBase
import com.pedro.library.util.sources.audio.AudioSource
import com.pedro.library.util.sources.audio.MicrophoneSource
import com.pedro.library.util.sources.video.Camera2Source
import com.pedro.library.util.sources.video.VideoSource
import com.pedro.library.util.streamclient.StreamClientListener
import com.pedro.library.util.streamclient.UdpStreamClient
import com.pedro.udp.UdpClient
import java.nio.ByteBuffer

/**
 * Created by pedro on 6/3/24.
 *
 * If you use VideoManager.Source.SCREEN/AudioManager.Source.INTERNAL. Call
 * changeVideoSourceScreen/changeAudioSourceInternal is necessary to start it.
 */

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class UdpStream(
  context: Context, connectChecker: ConnectChecker, videoSource: VideoSource,
  audioSource: AudioSource
): StreamBase(context, videoSource, audioSource) {

  private val streamClientListener = object: StreamClientListener {
    override fun onRequestKeyframe() {
      requestKeyframe()
    }
  }
  private val udpClient = UdpClient(connectChecker)
  private val streamClient = UdpStreamClient(udpClient, streamClientListener)

  override fun getStreamClient(): UdpStreamClient = streamClient

  constructor(context: Context, connectChecker: ConnectChecker):
      this(context, connectChecker, Camera2Source(context), MicrophoneSource())

  override fun setVideoCodecImp(codec: VideoCodec) {
    udpClient.setVideoCodec(codec)
  }

  override fun setAudioCodecImp(codec: AudioCodec) {
    udpClient.setAudioCodec(codec)
  }

  override fun audioInfo(sampleRate: Int, isStereo: Boolean) {
    udpClient.setAudioInfo(sampleRate, isStereo)
  }

  override fun rtpStartStream(endPoint: String) {
    udpClient.connect(endPoint)
  }

  override fun rtpStopStream() {
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