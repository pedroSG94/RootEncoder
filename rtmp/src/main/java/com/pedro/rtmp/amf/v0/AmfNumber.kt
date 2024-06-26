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

package com.pedro.rtmp.amf.v0

import com.pedro.rtmp.utils.readUntil
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Created by pedro on 20/04/21.
 *
 * A number in 8 bytes IEEE-754 double precision floating point value
 */
class AmfNumber(var value: Double = 0.0): AmfData() {

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    val bytes = ByteArray(getSize())
    input.readUntil(bytes)
    val value = ByteBuffer.wrap(bytes).long
    this.value = Double.Companion.fromBits(value)
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    val byteBuffer = ByteBuffer.allocate(getSize()).putLong(value.toRawBits())
    output.write(byteBuffer.array())
  }

  override fun getType(): AmfType = AmfType.NUMBER

  override fun getSize(): Int = 8

  override fun toString(): String {
    return "AmfNumber value: $value"
  }
}