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

package com.pedro.rtmp.amf.v3

import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 29/04/21.
 */
open class Amf3Object(private val properties: HashMap<Amf3String, Amf3Data> = LinkedHashMap()): Amf3Data() {

  protected var bodySize = 0

  init {
    properties.forEach {
      //Get size of all elements and include in size value. + 1 because include header size
      bodySize += it.key.getSize()
      bodySize += it.value.getSize() + 1
    }
    val objectInfo = (properties.size shl 4) or (0x02 shl 2) or 0x03
    val amf3Integer = Amf3Integer(objectInfo)
    bodySize += amf3Integer.getSize()
    val amf3String = Amf3String(this.javaClass.name)
    bodySize += amf3String.getSize()
  }

  fun getProperty(name: String): Amf3Data? {
    properties.forEach {
      if (it.key.value == name) {
        return it.value
      }
    }
    return null
  }

  open fun setProperty(name: String, data: String) {
    val key = Amf3String(name)
    val value = Amf3String(data)
    properties[key] = value
    bodySize += key.getSize()
    bodySize += value.getSize() + 1
  }

  open fun setProperty(name: String, data: Boolean) {
    val key = Amf3String(name)
    val value = if (data) Amf3True() else Amf3False()
    properties[key] = value
    bodySize += key.getSize()
    bodySize += value.getSize() + 1
  }

  open fun setProperty(name: String, data: Amf3Data) {
    val key = Amf3String(name)
    properties[key] = data
    bodySize += key.getSize()
    bodySize += data.getSize() + 1
  }

  open fun setProperty(name: String) {
    val key = Amf3String(name)
    val value = Amf3Null()
    properties[key] = value
    bodySize += key.getSize()
    bodySize += value.getSize() + 1
  }

  open fun setProperty(name: String, data: Double) {
    val key = Amf3String(name)
    val value = Amf3Double(data)
    properties[key] = value
    bodySize += key.getSize()
    bodySize += value.getSize() + 1
  }

  override fun readBody(input: InputStream) {
    TODO("Not yet implemented")
  }

  override fun writeBody(output: OutputStream) {
    val objectInfo = (properties.size shl 4) or (0x02 shl 2) or 0x03
    val amf3Integer = Amf3Integer(objectInfo)
    amf3Integer.writeBody(output)
    val amf3String = Amf3String(this.javaClass.name)
    amf3String.writeBody(output)
    properties.forEach {
      it.key.writeBody(output)

      it.value.writeHeader(output)
      it.value.writeBody(output)
    }
  }

  override fun getType(): Amf3Type = Amf3Type.OBJECT

  override fun getSize(): Int = bodySize

  override fun toString(): String {
    return "Amf3Object properties: $properties"
  }
}