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

package com.pedro.rtmp.rtmp.message.control

import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.rtmp.message.BasicHeader
import com.pedro.rtmp.rtmp.message.MessageType
import com.pedro.rtmp.rtmp.message.RtmpMessage
import com.pedro.rtmp.utils.readUInt16
import com.pedro.rtmp.utils.readUInt32
import com.pedro.rtmp.utils.writeUInt16
import com.pedro.rtmp.utils.writeUInt32
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Created by pedro on 21/04/21.
 */
class UserControl(var type: Type = Type.PING_REQUEST, var event: Event = Event(-1, -1)): RtmpMessage(BasicHeader(ChunkType.TYPE_0, ChunkStreamId.PROTOCOL_CONTROL.mark)) {

  private val TAG = "UserControl"
  private var bodySize = 6

  override fun readBody(input: InputStream) {
    bodySize = 0
    val t = input.readUInt16()
    type = Type.entries.find { it.mark.toInt() == t } ?: throw IOException("unknown user control type: $t")
    bodySize += 2
    val data = input.readUInt32()
    bodySize += 4
    event = if (type == Type.SET_BUFFER_LENGTH) {
      val bufferLength = input.readUInt32()
      Event(data, bufferLength)
    } else {
      Event(data)
    }
  }

  override fun storeBody(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    byteArrayOutputStream.writeUInt16(type.mark.toInt())
    byteArrayOutputStream.writeUInt32(event.data)
    if (event.bufferLength != -1) {
      byteArrayOutputStream.writeUInt32(event.bufferLength)
    }
    return byteArrayOutputStream.toByteArray()
  }

  override fun getType(): MessageType = MessageType.USER_CONTROL

  override fun getSize(): Int = bodySize

  override fun toString(): String {
    return "UserControl(type=$type, event=$event, bodySize=$bodySize)"
  }
}