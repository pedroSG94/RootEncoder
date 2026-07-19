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

  private val dataAmf0: MutableList<AmfData> = mutableListOf()
  private val dataAmf3: MutableList<Amf3Data> = mutableListOf()
  private var isAmf3 = true
  private var infoIndex = 0

  init {
    val amfString = AmfString(name)
    bodySize += amfString.getSize() + 1
    val amfNumber = AmfNumber(commandId.toDouble())
    bodySize += amfNumber.getSize() + 1
    bodySize += 1
    header.messageLength = bodySize
  }

  fun addData(amf3Data: Amf3Data) {
    dataAmf3.add(amf3Data)
    bodySize += amf3Data.getSize() + 2
    header.messageLength = bodySize
  }

  override fun getObjectEncoding(): Int {
    return if (isAmf3) {
      ((dataAmf3[infoIndex] as? Amf3Object)?.getProperty("objectEncoding") as? Amf3Double)?.value?.toInt() ?: 0
    } else {
      ((dataAmf0[infoIndex] as? AmfObject)?.getProperty("objectEncoding") as? AmfNumber)?.value?.toInt() ?: 0
    }
  }

  override fun getStreamId(): Int {
    return if (isAmf3) {
      (dataAmf3[infoIndex] as Amf3Double).value.toInt()
    } else {
      (dataAmf0[infoIndex] as AmfNumber).value.toInt()
    }
  }

  override fun getDescription(): String {
    return if (isAmf3) {
      ((dataAmf3[infoIndex] as Amf3Object).getProperty("description") as Amf3String).value
    } else {
      ((dataAmf0[infoIndex] as AmfObject).getProperty("description") as AmfString).value
    }
  }

  override fun getCode(): String {
    return if (isAmf3) {
      ((dataAmf3[infoIndex] as Amf3Object).getProperty("code") as Amf3String).value
    } else {
      ((dataAmf0[infoIndex] as AmfObject).getProperty("code") as AmfString).value
    }
  }

  override fun readBody(input: InputStream) {
    dataAmf0.clear()
    dataAmf3.clear()
    bodySize = 0

    input.read()
    bodySize += 1
    val name = AmfData.getAmfData(input) as AmfString
    bodySize += name.getSize() + 1
    this.name = name.value
    val commandId = AmfData.getAmfData(input) as AmfNumber
    bodySize += commandId.getSize() + 1
    this.commandId = commandId.value.toInt()

    var cont = 0
    while (bodySize < header.messageLength) {
      val mark = input.read()
      bodySize += 1
      if (mark == 0x11) {
        val amf3Data = Amf3Data.getAmf3Data(input)
        dataAmf3.add(amf3Data)
        bodySize += amf3Data.getSize() + 1
        if (cont == 1) {
          isAmf3 = true
          infoIndex = dataAmf3.size - 1
        }
      } else {
        val amfData = AmfData.getAmfData(mark, input)
        dataAmf0.add(amfData)
        bodySize += amfData.getSize()
        if (cont == 1) {
          isAmf3 = false
          infoIndex = dataAmf0.size - 1
        }
      }
      cont++
    }
    header.messageLength = bodySize
  }

  override fun storeBody(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    byteArrayOutputStream.write(0x00)
    val amfString = AmfString(name)
    amfString.writeHeader(byteArrayOutputStream)
    amfString.writeBody(byteArrayOutputStream)
    val amfNumber = AmfNumber(commandId.toDouble())
    amfNumber.writeHeader(byteArrayOutputStream)
    amfNumber.writeBody(byteArrayOutputStream)

    dataAmf3.forEach {
      byteArrayOutputStream.write(0x11)
      it.writeHeader(byteArrayOutputStream)
      it.writeBody(byteArrayOutputStream)
    }
    return byteArrayOutputStream.toByteArray()
  }

  override fun getType(): MessageType = MessageType.COMMAND_AMF3

  override fun toString(): String {
    return "Command(name='$name', transactionId=$commandId, timeStamp=$timestamp, streamId=$streamId, data=${dataAmf0.plus(dataAmf3)}, bodySize=$bodySize)"
  }
}