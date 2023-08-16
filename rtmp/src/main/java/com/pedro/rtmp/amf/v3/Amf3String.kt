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

import com.pedro.rtmp.utils.getUInt29Size
import com.pedro.rtmp.utils.readUInt29
import com.pedro.rtmp.utils.readUntil
import com.pedro.rtmp.utils.writeUInt29
import com.pedro.rtmp.utils.writeUInt32
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.jvm.Throws

/**
 * Created by pedro on 8/04/21.
 */
class Amf3String(var value: String = ""): Amf3Data() {

  private var bodySize: Int = value.toByteArray(Charsets.UTF_8).size

  init {
    bodySize += bodySize.getUInt29Size()
  }

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    //read value size as UInt32
    bodySize = input.readUInt29()
    //read value in UTF-8
    val bytes = ByteArray(bodySize)
    bodySize += bodySize.getUInt29Size()
    input.readUntil(bytes)
    value = String(bytes, Charsets.UTF_8)
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    //write value size as UInt32. Value size not included
    output.writeUInt29((bodySize - 4 shl 1) or 0x01)
    //write value bytes in UTF-8
    output.write(bytes)
  }

  override fun getType(): Amf3Type = Amf3Type.STRING

  override fun getSize(): Int = bodySize

  override fun toString(): String {
    return "Amf3String value: $value"
  }
}