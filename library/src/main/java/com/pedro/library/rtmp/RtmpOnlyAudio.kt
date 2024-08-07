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
package com.pedro.library.rtmp

import android.media.MediaCodec
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.library.base.OnlyAudioBase
import com.pedro.library.util.streamclient.RtmpStreamClient
import com.pedro.rtmp.rtmp.RtmpClient
import java.nio.ByteBuffer

/**
 * More documentation see:
 * [com.pedro.library.base.OnlyAudioBase]
 *
 * Created by pedro on 10/07/18.
 */
class RtmpOnlyAudio(connectChecker: ConnectChecker) : OnlyAudioBase() {

  private val rtmpClient = RtmpClient(connectChecker).apply { setOnlyAudio(true) }
  private val streamClient = RtmpStreamClient(rtmpClient, null)

  override fun getStreamClient(): RtmpStreamClient = streamClient

  override fun setAudioCodecImp(codec: AudioCodec) {
    rtmpClient.setAudioCodec(codec)
  }

  override fun onAudioInfoImp(isStereo: Boolean, sampleRate: Int) {
    rtmpClient.setAudioInfo(sampleRate, isStereo)
  }

  override fun startStreamImp(url: String) {
    rtmpClient.connect(url)
  }

  override fun stopStreamImp() {
    rtmpClient.disconnect()
  }

  override fun getAudioDataImp(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtmpClient.sendAudio(audioBuffer, info)
  }
}
