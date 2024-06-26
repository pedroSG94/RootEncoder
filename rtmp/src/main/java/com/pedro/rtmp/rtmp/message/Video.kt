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

import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import java.io.InputStream

/**
 * Created by pedro on 21/04/21.
 */
class Video(private val flvPacket: FlvPacket = FlvPacket(), streamId: Int = 0): RtmpMessage(BasicHeader(ChunkType.TYPE_0, ChunkStreamId.VIDEO.mark)) {

  init {
    header.messageStreamId = streamId
    header.timeStamp = flvPacket.timeStamp.toInt()
    header.messageLength = flvPacket.length
  }

  override fun readBody(input: InputStream) {
  }

  override fun storeBody(): ByteArray = flvPacket.buffer

  override fun getType(): MessageType = MessageType.VIDEO

  override fun getSize(): Int = flvPacket.length

  override fun toString(): String {
    return "Video, size: ${getSize()}"
  }
}