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
import com.pedro.rtmp.utils.writeUInt29
import com.pedro.rtmp.utils.writeUInt32
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 29/04/21.
 *
 * A number in U29. Unsigned 29 bits integer
 */
class Amf3Integer(var value: Int = 0): Amf3Data() {

  private var bodySize = value.getUInt29Size()

  override fun readBody(input: InputStream) {
    this.value = input.readUInt29()
    bodySize = value.getUInt29Size()
  }

  override fun writeBody(output: OutputStream) {
    output.writeUInt29(value)
  }

  override fun getType(): Amf3Type = Amf3Type.INTEGER

  override fun getSize(): Int = bodySize

  override fun toString(): String {
    return "Amf3Integer value: $value"
  }
}