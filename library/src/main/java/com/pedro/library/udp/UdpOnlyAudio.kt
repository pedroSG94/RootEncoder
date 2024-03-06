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

import android.media.MediaCodec
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.library.base.OnlyAudioBase
import com.pedro.library.util.streamclient.UdpStreamClient
import com.pedro.udp.UdpClient
import java.nio.ByteBuffer

/**
 * More documentation see:
 * [OnlyAudioBase]
 *
 * Created by pedro on 6/3/24.
 */
class UdpOnlyAudio(connectChecker: ConnectChecker) : OnlyAudioBase() {

  private val udpClient = UdpClient(connectChecker).apply { setOnlyAudio(true) }
  private val streamClient = UdpStreamClient(udpClient, null)

  override fun getStreamClient(): UdpStreamClient = streamClient

  override fun setAudioCodecImp(codec: AudioCodec) {
    udpClient.setAudioCodec(codec)
  }

  override fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int) {
    udpClient.setAudioInfo(sampleRate, isStereo)
  }

  override fun startStreamRtp(url: String) {
    udpClient.connect(url)
  }

  override fun stopStreamRtp() {
    udpClient.disconnect()
  }

  override fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    udpClient.sendAudio(aacBuffer, info)
  }
}
