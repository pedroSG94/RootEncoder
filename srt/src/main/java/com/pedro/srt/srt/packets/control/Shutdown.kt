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

package com.pedro.srt.srt.packets.control

import com.pedro.srt.srt.packets.ControlPacket
import com.pedro.srt.utils.readUInt32
import com.pedro.srt.utils.writeUInt32
import java.io.InputStream

/**
 * Created by pedro on 22/8/23.
 */
class Shutdown: ControlPacket(ControlType.SHUTDOWN) {

  fun write(ts: Int, socketId: Int) {
    super.writeHeader(ts, socketId)
    buffer.writeUInt32(0)
  }

  fun read(input: InputStream) {
    super.readHeader(input)
    input.readUInt32()
  }

  override fun toString(): String {
    return "Shutdown()"
  }
}