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
import com.pedro.rtmp.amf.v3.Amf3Integer
import com.pedro.rtmp.amf.v3.Amf3Object
import com.pedro.rtmp.amf.v3.Amf3String
import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.rtmp.message.BasicHeader
import com.pedro.rtmp.rtmp.message.MessageType
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Created by pedro on 21/04/21.
 *
 * In AMF3 sessions servers like Red5 keep replying with this message type but encode
 * arguments as AMF3 values escaped with the 0x11 avmplus marker, so reading must accept
 * both encodings per value.
 */
class CommandAmf0(name: String = "", commandId: Int = 0, private val timestamp: Int = 0, private val streamId: Int = 0, basicHeader: BasicHeader =
    BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark)): Command(name, commandId, timestamp, streamId, basicHeader = basicHeader) {

  private val data: MutableList<Any> = mutableListOf()

  init {
    val amfString = AmfString(name)
    bodySize += amfString.getSize() + 1
    val amfNumber = AmfNumber(commandId.toDouble())
    bodySize += amfNumber.getSize() + 1
    header.messageLength = bodySize
  }

  fun addData(amfData: AmfData) {
    data.add(amfData)
    bodySize += amfData.getSize() + 1
    header.messageLength = bodySize
  }

  override fun getStreamId(): Int {
    return when (val value = data[1]) {
      is AmfNumber -> value.value.toInt()
      is Amf3Double -> value.value.toInt()
      is Amf3Integer -> value.value
      else -> throw IOException("Unexpected stream id type: $value")
    }
  }

  override fun getObjectEncoding(): Int {
    return when (val value = getInfoProperty("objectEncoding")) {
      is AmfNumber -> value.value.toInt()
      is Amf3Double -> value.value.toInt()
      is Amf3Integer -> value.value
      else -> 0
    }
  }

  override fun getDescription(): String {
    return when (val value = getInfoProperty("description")) {
      is AmfString -> value.value
      is Amf3String -> value.value
      else -> throw IOException("Unexpected description type: $value")
    }
  }

  override fun getCode(): String {
    return when (val value = getInfoProperty("code")) {
      is AmfString -> value.value
      is Amf3String -> value.value
      else -> throw IOException("Unexpected code type: $value")
    }
  }

  private fun getInfoProperty(name: String): Any? {
    return when (val info = data.getOrNull(1)) {
      is AmfObject -> info.getProperty(name)
      is Amf3Object -> info.getProperty(name)
      else -> null
    }
  }

  override fun readBody(input: InputStream) {
    data.clear()
    bodySize = 0

    //Red5 in AMF3 sessions escapes every value including command name and transaction id
    this.name = when (val value = readMixedValue(input)) {
      is AmfString -> value.value
      is Amf3String -> value.value
      else -> throw IOException("Unexpected command name type: $value")
    }
    this.commandId = when (val value = readMixedValue(input)) {
      is AmfNumber -> value.value.toInt()
      is Amf3Integer -> value.value
      is Amf3Double -> value.value.toInt()
      else -> throw IOException("Unexpected transaction id type: $value")
    }
    while (bodySize < header.messageLength) {
      data.add(readMixedValue(input))
    }
    header.messageLength = bodySize
  }

  private fun readMixedValue(input: InputStream): Any {
    val mark = input.read()
    bodySize += 1
    return if (mark == 0x11) {
      val amf3Data = Amf3Data.getAmf3Data(input)
      bodySize += amf3Data.getSize() + 1
      amf3Data
    } else {
      val amfData = AmfData.getAmfData(mark, input)
      bodySize += amfData.getSize()
      amfData
    }
  }

  override fun storeBody(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val amfString = AmfString(name)
    amfString.writeHeader(byteArrayOutputStream)
    amfString.writeBody(byteArrayOutputStream)
    val amfNumber = AmfNumber(commandId.toDouble())
    amfNumber.writeHeader(byteArrayOutputStream)
    amfNumber.writeBody(byteArrayOutputStream)

    data.forEach {
      when (it) {
        is AmfData -> {
          it.writeHeader(byteArrayOutputStream)
          it.writeBody(byteArrayOutputStream)
        }
        is Amf3Data -> {
          byteArrayOutputStream.write(0x11)
          it.writeHeader(byteArrayOutputStream)
          it.writeBody(byteArrayOutputStream)
        }
      }
    }
    return byteArrayOutputStream.toByteArray()
  }

  override fun getType(): MessageType = MessageType.COMMAND_AMF0

  override fun toString(): String {
    return "Command(name='$name', transactionId=$commandId, timeStamp=$timestamp, streamId=$streamId, data=$data, bodySize=$bodySize)"
  }
}