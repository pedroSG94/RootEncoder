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

import com.pedro.rtmp.amf.v0.AmfData
import com.pedro.rtmp.amf.v0.AmfString
import com.pedro.rtmp.amf.v3.Amf3Data
import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.rtmp.message.BasicHeader
import com.pedro.rtmp.rtmp.message.MessageType
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Created by pedro on 21/04/21.
 */
class DataAmf3(private var name: String = "", timeStamp: Int = 0, streamId: Int = 0, basicHeader: BasicHeader = BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark)):
  Data(timeStamp, streamId, basicHeader) {

  private val dataAmf0: MutableList<AmfData> = mutableListOf()
  private val dataAmf3: MutableList<Amf3Data> = mutableListOf()

  init {
    val amfString = AmfString(name)
    bodySize += amfString.getSize() + 1
    bodySize += 1
    header.messageLength = bodySize
  }

  fun addData(amf3Data: Amf3Data) {
    dataAmf3.add(amf3Data)
    bodySize += amf3Data.getSize() + 2
    header.messageLength = bodySize
  }

  override fun readBody(input: InputStream) {
    dataAmf0.clear()
    dataAmf3.clear()
    bodySize = 0

    input.read()
    bodySize += 1
    val amfString = AmfString()
    amfString.readHeader(input)
    amfString.readBody(input)
    bodySize += amfString.getSize() + 1
    name = amfString.value

    while (bodySize < header.messageLength) {
      val mark = input.read()
      bodySize += 1
      if (mark == 0x11) {
        val amf3Data = Amf3Data.getAmf3Data(input)
        dataAmf3.add(amf3Data)
        bodySize += amf3Data.getSize() + 1
      } else {
        val amfData = AmfData.getAmfData(mark, input)
        dataAmf0.add(amfData)
        bodySize += amfData.getSize()
      }
    }
  }

  override fun storeBody(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    byteArrayOutputStream.write(0x00)
    val amfString = AmfString(name)
    amfString.writeHeader(byteArrayOutputStream)
    amfString.writeBody(byteArrayOutputStream)

    dataAmf3.forEach {
      byteArrayOutputStream.write(0x11)
      it.writeHeader(byteArrayOutputStream)
      it.writeBody(byteArrayOutputStream)
    }
    return byteArrayOutputStream.toByteArray()
  }

  override fun getType(): MessageType = MessageType.DATA_AMF3

  override fun toString(): String {
    return "Data(name='$name', data=$dataAmf3, bodySize=$bodySize)"
  }
}