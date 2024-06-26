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

package com.pedro.rtmp.rtmp.message.command

import com.pedro.rtmp.amf.v3.Amf3Data
import com.pedro.rtmp.amf.v3.Amf3Double
import com.pedro.rtmp.amf.v3.Amf3Object
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
class CommandAmf3(name: String = "", commandId: Int = 0, private val timestamp: Int = 0, private val streamId: Int = 0, basicHeader: BasicHeader =
    BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark)): Command(name, commandId, timestamp, streamId, basicHeader = basicHeader) {

  private val data: MutableList<Amf3Data> = mutableListOf()

  init {
    val amf3String = Amf3String(name)
    data.add(amf3String)
    bodySize += amf3String.getSize() + 1
    val amf3Double = Amf3Double(commandId.toDouble())
    bodySize += amf3Double.getSize() + 1
    data.add(amf3Double)
  }

  fun addData(amf3Data: Amf3Data) {
    data.add(amf3Data)
    bodySize += amf3Data.getSize() + 1
    header.messageLength = bodySize
  }

  override fun getStreamId(): Int {
    return (data[3] as Amf3Double).value.toInt()
  }

  override fun getDescription(): String {
    return ((data[3] as Amf3Object).getProperty("description") as Amf3String).value
  }

  override fun getCode(): String {
    return ((data[3] as Amf3Object).getProperty("code") as Amf3String).value
  }

  override fun readBody(input: InputStream) {
    data.clear()
    var bytesRead = 0
    while (bytesRead < header.messageLength) {
      val amf3Data = Amf3Data.getAmf3Data(input)
      bytesRead += amf3Data.getSize() + 1
      data.add(amf3Data)
    }
    if (data.isNotEmpty()) {
      if (data[0] is Amf3String) {
        name = (data[0] as Amf3String).value
      }
      if (data.size >= 2 && data[1] is Amf3Double) {
        commandId = (data[1] as Amf3Double).value.toInt()
      }
    }
    bodySize = bytesRead
    header.messageLength = bodySize
  }

  override fun storeBody(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    data.forEach {
      it.writeHeader(byteArrayOutputStream)
      it.writeBody(byteArrayOutputStream)
    }
    return byteArrayOutputStream.toByteArray()
  }

  override fun getType(): MessageType = MessageType.COMMAND_AMF3

  override fun toString(): String {
    return "Command(name='$name', transactionId=$commandId, timeStamp=$timestamp, streamId=$streamId, data=$data, bodySize=$bodySize)"
  }
}