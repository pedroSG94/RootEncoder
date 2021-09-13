/*
 * Copyright (C) 2021 pedroSG94.
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
import com.pedro.rtmp.amf.v0.AmfString
import com.pedro.rtmp.rtmp.message.BasicHeader
import com.pedro.rtmp.rtmp.message.RtmpMessage
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Created by pedro on 8/04/21.
 *
 * Represent packets like connect, createStream, play, pause...
 * Can be encoded using AMF0 or AMF3
 *
 * TODO use amf3 or amf0 depend of getType method
 */
abstract class Command(var name: String = "", var commandId: Int, private val timeStamp: Int, private val streamId: Int = 0, basicHeader: BasicHeader): RtmpMessage(basicHeader) {

  val data: MutableList<AmfData> = mutableListOf()
  private var bodySize = 0

  init {
    val amfString = AmfString(name)
    data.add(amfString)
    bodySize += amfString.getSize() + 1
    val amfNumber = AmfNumber(commandId.toDouble())
    bodySize += amfNumber.getSize() + 1
    data.add(amfNumber)
    header.messageLength = bodySize
    header.timeStamp = timeStamp
    header.messageStreamId = streamId
  }

  fun addData(amfData: AmfData) {
    data.add(amfData)
    bodySize += amfData.getSize() + 1
    header.messageLength = bodySize
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

  override fun getSize(): Int = bodySize

  override fun toString(): String {
    return "Command(name='$name', transactionId=$commandId, timeStamp=$timeStamp, streamId=$streamId, data=$data, bodySize=$bodySize)"
  }
}