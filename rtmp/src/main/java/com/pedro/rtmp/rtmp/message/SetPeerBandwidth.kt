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

package com.pedro.rtmp.rtmp.message

import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.utils.readUInt32
import com.pedro.rtmp.utils.writeUInt32
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Created by pedro on 21/04/21.
 */
class SetPeerBandwidth(private var acknowledgementWindowSize: Int = 0, private var type: Type = Type.DYNAMIC):
    RtmpMessage(BasicHeader(ChunkType.TYPE_0, ChunkStreamId.PROTOCOL_CONTROL.mark)) {

  enum class Type(val mark: Byte) {
    HARD(0x00), SOFT(0x01), DYNAMIC(0x02)
  }

  override fun readBody(input: InputStream) {
    acknowledgementWindowSize = input.readUInt32()
    val t = input.read().toByte()
    type = Type.entries.find { it.mark == t } ?: throw IOException("Unknown bandwidth type: $t")
  }

  override fun storeBody(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    byteArrayOutputStream.writeUInt32(acknowledgementWindowSize)
    byteArrayOutputStream.write(type.mark.toInt())
    return byteArrayOutputStream.toByteArray()
  }

  override fun getType(): MessageType = MessageType.SET_PEER_BANDWIDTH

  override fun getSize(): Int = 5

  override fun toString(): String {
    return "SetPeerBandwidth(acknowledgementWindowSize=$acknowledgementWindowSize, type=$type)"
  }
}