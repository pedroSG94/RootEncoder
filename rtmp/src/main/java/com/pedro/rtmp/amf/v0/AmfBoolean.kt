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

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 20/04/21.
 *
 * Only 1 byte of size where 0 is false and another value is true
 */
class AmfBoolean(var value: Boolean = false): AmfData() {

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    val b = input.read()
    this.value = b != 0
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    output.write(if (value) 1 else 0)
  }

  override fun getType(): AmfType = AmfType.BOOLEAN

  override fun getSize(): Int = 1

  override fun toString(): String {
    return "AmfBoolean value: $value"
  }
}