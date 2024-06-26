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

import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 29/04/21.
 */
open class Amf3Object(private val properties: HashMap<Amf3String, Amf3Data> = LinkedHashMap()): Amf3Data() {

  protected var bodySize = 0

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
    TODO("Not yet implemented")
  }

  override fun getType(): Amf3Type = Amf3Type.OBJECT

  override fun getSize(): Int {
    TODO("Not yet implemented")
  }
}