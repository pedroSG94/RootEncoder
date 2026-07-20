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
 * AMF3 arrays have an associative part (name/value pairs, empty string terminated)
 * followed by a dense part of U29A-value items. References can't be resolved without
 * payload scope, so they are rejected.
 */
class Amf3Array(val items: MutableList<Amf3Data> = mutableListOf()): Amf3Data() {

  private val associative = LinkedHashMap<Amf3String, Amf3Data>()
  private var referenceSize = -1

  fun getProperty(name: String): Amf3Data? {
    associative.forEach {
      if (it.key.value == name) {
        return it.value
      }
    }
    return null
  }

  fun setProperty(name: String, data: Amf3Data) {
    val existingKey = associative.keys.find { it.value == name }
    if (existingKey != null) associative[existingKey] = data
    else associative[Amf3String(name)] = data
  }

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    items.clear()
    associative.clear()
    val u29 = input.readUInt29()
    if (u29 and 0x01 == 0) { //reference to a previous array of this payload, no tables to resolve it
      referenceSize = u29.getUInt29Size()
      return
    }
    referenceSize = -1
    val denseCount = u29 ushr 1
    while (true) {
      val key = Amf3String()
      key.readBody(input)
      if (key.isReference) throw IOException("AMF3 string reference as array key is not supported")
      if (key.value.isEmpty()) break
      associative[key] = getAmf3Data(input)
    }
    repeat(denseCount) {
      items.add(getAmf3Data(input))
    }
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    output.writeUInt29((items.size shl 1) or 1)
    associative.forEach { (key, value) ->
      key.writeBody(output)
      value.writeHeader(output)
      value.writeBody(output)
    }
    output.write(0x01) //empty string, end of associative part
    items.forEach {
      it.writeHeader(output)
      it.writeBody(output)
    }
  }

  override fun getType(): Amf3Type = Amf3Type.ARRAY

  override fun getSize(): Int {
    if (referenceSize != -1) return referenceSize
    var size = ((items.size shl 1) or 1).getUInt29Size() + 1
    associative.forEach { (key, value) ->
      size += key.getSize() + value.getSize() + 1
    }
    items.forEach { size += it.getSize() + 1 }
    return size
  }

  override fun toString(): String {
    return "Amf3Array(items=$items, associative=${associative.entries.joinToString { "${it.key.value}=${it.value}" }})"
  }
}
