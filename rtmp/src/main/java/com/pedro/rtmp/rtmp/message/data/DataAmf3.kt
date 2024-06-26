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

package com.pedro.rtmp.rtmp.message.data

import com.pedro.rtmp.amf.v3.Amf3Data
import com.pedro.rtmp.amf.v3.Amf3String
import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.rtmp.message.BasicHeader
import com.pedro.rtmp.rtmp.message.MessageType
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Created by pedro on 21/04/21.
 */
class DataAmf3(private val name: String = "", timeStamp: Int = 0, streamId: Int = 0, basicHeader: BasicHeader = BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark)):
  Data(timeStamp, streamId, basicHeader) {

  private val data: MutableList<Amf3Data> = mutableListOf()

  init {
    val amf3String = Amf3String(name)
    bodySize += amf3String.getSize() + 1
    data.forEach {
      bodySize += it.getSize() + 1
    }
  }

  fun addData(amf3Data: Amf3Data) {
    data.add(amf3Data)
    bodySize += amf3Data.getSize() + 1
    header.messageLength = bodySize
  }

  override fun readBody(input: InputStream) {
    data.clear()
    bodySize = 0
    val amf3String = Amf3String()
    amf3String.readHeader(input)
    amf3String.readBody(input)
    bodySize += amf3String.getSize() + 1
    while (bodySize < header.messageLength) {
      val amf3Data = Amf3Data.getAmf3Data(input)
      data.add(amf3Data)
      bodySize += amf3Data.getSize() + 1
    }
  }

  override fun storeBody(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val amf3String = Amf3String(name)
    amf3String.writeHeader(byteArrayOutputStream)
    amf3String.writeBody(byteArrayOutputStream)
    data.forEach {
      it.writeHeader(byteArrayOutputStream)
      it.writeBody(byteArrayOutputStream)
    }
    return byteArrayOutputStream.toByteArray()
  }

  override fun getType(): MessageType = MessageType.DATA_AMF3

  override fun toString(): String {
    return "Data(name='$name', data=$data, bodySize=$bodySize)"
  }
}