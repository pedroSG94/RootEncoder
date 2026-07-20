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

package com.pedro.rtmp.amf.v3

import com.pedro.common.getUInt29Size
import com.pedro.common.readUInt29
import com.pedro.common.writeUInt29
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 29/04/21.
 *
 * Unlike Amf3Object, dictionary keys are full value-types (marker included) and entries
 * are preceded by a U29 count plus a weak-keys flag byte. Only string keys are supported.
 * Introduced in Flash Player 10; several servers don't understand it, prefer Amf3Object
 * or the associative part of Amf3Array for interop.
 */
class Amf3Dictionary(properties: LinkedHashMap<Amf3String, Amf3Data> = LinkedHashMap()): Amf3Object(properties) {

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    properties.clear()
    val u29 = input.readUInt29()
    bodySize = u29.getUInt29Size()
    if (u29 and 0x01 == 0) { //reference to a previous dictionary of this payload, no tables to resolve it
      reference = u29 ushr 1
      return
    }
    reference = -1
    val count = u29 ushr 1
    input.read() //weak keys flag, ignored
    bodySize += 1
    repeat(count) {
      val key = getAmf3Data(input)
      if (key !is Amf3String) throw IOException("Only string keys are supported in AMF3 dictionary")
      if (key.isReference) throw IOException("AMF3 string reference as dictionary key is not supported")
      bodySize += key.getSize() + 1
      val value = getAmf3Data(input)
      bodySize += value.getSize() + 1
      properties[key] = value
    }
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    output.writeUInt29((properties.size shl 1) or 1)
    output.write(0x00) //strong keys
    properties.forEach { (key, value) ->
      key.writeHeader(output)
      key.writeBody(output)
      value.writeHeader(output)
      value.writeBody(output)
    }
  }

  override fun getType(): Amf3Type = Amf3Type.DICTIONARY

  override fun getSize(): Int {
    if (reference != -1) return bodySize
    var size = ((properties.size shl 1) or 1).getUInt29Size() + 1
    properties.forEach { (key, value) ->
      size += key.getSize() + value.getSize() + 2
    }
    return size
  }

  override fun toString(): String {
    return "Amf3Dictionary(properties=${properties.entries.joinToString { "${it.key.value}=${it.value}" }})"
  }
}
