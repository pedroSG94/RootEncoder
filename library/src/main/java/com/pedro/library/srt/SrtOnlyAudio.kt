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
package com.pedro.library.srt

import android.media.MediaCodec
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.library.base.OnlyAudioBase
import com.pedro.library.util.streamclient.SrtStreamClient
import com.pedro.srt.srt.SrtClient
import java.nio.ByteBuffer

/**
 * More documentation see:
 * [OnlyAudioBase]
 *
 * Created by pedro on 8/9/23.
 */
class SrtOnlyAudio(connectChecker: ConnectChecker) : OnlyAudioBase() {

  private val srtClient = SrtClient(connectChecker).apply { setOnlyAudio(true) }
  private val streamClient = SrtStreamClient(srtClient, null)

  override fun getStreamClient(): SrtStreamClient = streamClient

  override fun setAudioCodecImp(codec: AudioCodec) {
    srtClient.setAudioCodec(codec)
  }

  override fun onAudioInfoImp(isStereo: Boolean, sampleRate: Int) {
    srtClient.setAudioInfo(sampleRate, isStereo)
  }

  override fun startStreamImp(url: String) {
    srtClient.connect(url)
  }

  override fun stopStreamImp() {
    srtClient.disconnect()
  }

  override fun getAudioDataImp(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    srtClient.sendAudio(audioBuffer, info)
  }
}
