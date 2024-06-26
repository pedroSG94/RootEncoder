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

import com.pedro.rtmp.amf.v0.AmfData
import com.pedro.rtmp.amf.v0.AmfNumber
import com.pedro.rtmp.amf.v0.AmfObject
import com.pedro.rtmp.amf.v0.AmfString
import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.rtmp.message.BasicHeader
import com.pedro.rtmp.rtmp.message.MessageType
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Created by pedro on 21/04/21.
 */
class CommandAmf0(name: String = "", commandId: Int = 0, private val timestamp: Int = 0, private val streamId: Int = 0, basicHeader: BasicHeader =
    BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark)): Command(name, commandId, timestamp, streamId, basicHeader = basicHeader) {

  private val data: MutableList<AmfData> = mutableListOf()

  init {
    val amfString = AmfString(name)
    data.add(amfString)
    bodySize += amfString.getSize() + 1
    val amfNumber = AmfNumber(commandId.toDouble())
    bodySize += amfNumber.getSize() + 1
    data.add(amfNumber)
  }

  fun addData(amfData: AmfData) {
    data.add(amfData)
    bodySize += amfData.getSize() + 1
    header.messageLength = bodySize
  }

  override fun getStreamId(): Int {
    return (data[3] as AmfNumber).value.toInt()
  }

  override fun getDescription(): String {
    return ((data[3] as AmfObject).getProperty("description") as AmfString).value
  }

  override fun getCode(): String {
    return ((data[3] as AmfObject).getProperty("code") as AmfString).value
  }

  override fun readBody(input: InputStream) {
    data.clear()
    var bytesRead = 0
    while (bytesRead < header.messageLength) {
      val amfData = AmfData.getAmfData(input)
      bytesRead += amfData.getSize() + 1
      data.add(amfData)
    }
    if (data.isNotEmpty()) {
      if (data[0] is AmfString) {
        name = (data[0] as AmfString).value
      }
      if (data.size >= 2 && data[1] is AmfNumber) {
        commandId = (data[1] as AmfNumber).value.toInt()
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

  override fun getType(): MessageType = MessageType.COMMAND_AMF0

  override fun toString(): String {
    return "Command(name='$name', transactionId=$commandId, timeStamp=$timestamp, streamId=$streamId, data=$data, bodySize=$bodySize)"
  }
}