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

import com.pedro.common.TimeUtils
import com.pedro.rtmp.utils.readUntil
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Created by pedro on 19/07/22.
 *
 * milliseconds from 1st Jan 1970 in UTC time zone.
 * timeZone value is a reserved value that should be 0x0000
 */
class AmfDate(var date: Double = TimeUtils.getCurrentTimeMillis().toDouble()): AmfData() {

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    val bytes = ByteArray(getSize() - 2)
    input.readUntil(bytes)
    val value = ByteBuffer.wrap(bytes).long
    date = Double.Companion.fromBits(value)
    val timeZone = byteArrayOf(0x00, 0x00)
    input.readUntil(timeZone)
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    val byteBuffer = ByteBuffer.allocate(getSize() - 2).putLong(date.toRawBits())
    output.write(byteBuffer.array())
    val timeZone = byteArrayOf(0x00, 0x00)
    output.write(timeZone)
  }

  override fun getType(): AmfType = AmfType.DATE

  override fun getSize(): Int = 10

  override fun toString(): String {
    return "AmfUnsupported"
  }
}