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

package com.pedro.udp

import com.pedro.common.AudioCodec
import com.pedro.common.VideoCodec
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.utils.Constants
import com.pedro.udp.utils.UdpSocket
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Created by pedro on 6/3/24.
 */
class CommandManager {

  var MTU = Constants.MTU
  var audioDisabled = false
  var videoDisabled = false
  var host = ""
  //Avoid write a packet in middle of other.
  private val writeSync = Mutex(locked = false)
  var videoCodec = VideoCodec.H264
  var audioCodec = AudioCodec.AAC

  @Throws(IOException::class)
  suspend fun writeData(packet: MpegTsPacket, socket: UdpSocket?): Int {
    writeSync.withLock {
      return socket?.write(packet) ?: 0
    }
  }

  fun reset() {
    MTU = Constants.MTU
    host = ""
  }
}