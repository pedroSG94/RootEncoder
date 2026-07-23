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

package com.pedro.rtmp.amf

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 20/04/21.
 *
 * A Map of others amf packets where key is an AmfString and value could be any amf packet
 */
open class AmfObject(private val properties: LinkedHashMap<AmfString, AmfData> = LinkedHashMap()): AmfData() {

  protected var bodySize = 0

  init {
    properties.forEach {
      //Get size of all elements and include in size value. + 1 because include header size
      bodySize += it.key.getSize()
      bodySize += it.value.getSize() + 1
    }
    val objectEnd = AmfObjectEnd()
    bodySize += objectEnd.getSize()
  }

  fun getProperty(name: String): AmfData? {
    properties.forEach {
      if (it.key.value == name) {
        return it.value
      }
    }
    return null
  }

  open fun setProperty(name: String, data: String) = putProperty(name, AmfString(data))

  open fun setProperty(name: String, data: Boolean) = putProperty(name, AmfBoolean(data))

  open fun setProperty(name: String) = putProperty(name, AmfNull())

  open fun setProperty(name: String, data: Double) = putProperty(name, AmfNumber(data))

  open fun setProperty(name: String, data: AmfData) = putProperty(name, data)

  open fun setProperty(name: String, data: Any) {
    val newValue = when (data) {
      is String -> AmfString(data)
      is Int -> AmfNumber(data.toDouble())
      is Long -> AmfNumber(data.toDouble())
      is Double -> AmfNumber(data)
      is Float -> AmfNumber(data.toDouble())
      is Boolean -> AmfBoolean(data)
      is AmfData -> data
      else -> throw IllegalArgumentException("Unsupported value type: ${data::class.java.name}")
    }
    putProperty(name, newValue)
  }

  private fun putProperty(name: String, value: AmfData) {
    val key = AmfString(name)
    val previous = properties.put(key, value)
    bodySize += if (previous != null) {
      value.getSize() - previous.getSize()
    } else {
      key.getSize() + value.getSize() + 1
    }
  }

  fun getProperties() = properties

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    properties.clear()
    bodySize = 0
    val objectEnd = AmfObjectEnd()
    val markInputStream = if (input.markSupported()) input else BufferedInputStream(input)
    while (!objectEnd.found) {
      markInputStream.mark(objectEnd.getSize())
      objectEnd.readBody(markInputStream)
      if (objectEnd.found) {
        bodySize += objectEnd.getSize()
      } else {
        markInputStream.reset()

        val key = AmfString()
        key.readBody(markInputStream)
        bodySize += key.getSize()

        val value = getAmfData(markInputStream)
        bodySize += value.getSize() + 1

        properties[key] = value
      }
    }
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    properties.forEach {
      it.key.writeBody(output)

      it.value.writeHeader(output)
      it.value.writeBody(output)
    }
    val objectEnd = AmfObjectEnd()
    objectEnd.writeBody(output)
  }

  override fun getType(): AmfType = AmfType.OBJECT

  override fun getSize(): Int = bodySize

  override fun toString(): String {
    return "AmfObject properties: $properties"
  }
}