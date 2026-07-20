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
import com.pedro.common.readUntil
import com.pedro.common.writeUInt29
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 8/04/21.
 */
open class Amf3String(var value: String = ""): Amf3Data() {

  private var bodySize: Int

  init {
    val length = value.toByteArray(Charsets.UTF_8).size
    bodySize = length + ((length shl 1) or 1).getUInt29Size()
  }

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    val u29 = input.readUInt29()
    if (u29 and 0x01 == 0) throw IOException("AMF3 string references are not supported")
    val length = u29 ushr 1
    bodySize = length + length.getUInt29Size()
    val bytes = ByteArray(length)
    input.readUntil(bytes)
    value = String(bytes, Charsets.UTF_8)
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    output.writeUInt29((bytes.size shl 1) or 1)
    output.write(bytes)
  }

  override fun getType(): Amf3Type = Amf3Type.STRING

  override fun getSize(): Int = bodySize
}