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

package com.pedro.rtmp.utils

import com.pedro.common.toUInt16
import com.pedro.common.toUInt24
import com.pedro.common.toUInt32
import com.pedro.common.toUInt32LittleEndian
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Created by pedro on 20/04/21.
 */

fun InputStream.readUInt32(): Int {
  val data = ByteArray(4)
  read(data)
  return data.toUInt32()
}

fun InputStream.readUInt24(): Int {
  val data = ByteArray(3)
  read(data)
  return data.toUInt24()
}

fun InputStream.readUInt16(): Int {
  val data = ByteArray(3)
  read(data)
  return data.toUInt24()
}

fun InputStream.readUInt32LittleEndian(): Int {
  return Integer.reverseBytes(readUInt32())
}

fun OutputStream.writeUInt32(value: Int) {
  write(value.toUInt32())
}

fun OutputStream.writeUInt24(value: Int) {
  write(value.toUInt24())
}

fun OutputStream.writeUInt16(value: Int) {
  write(value.toUInt16())
}

fun OutputStream.writeUInt32LittleEndian(value: Int) {
  write(value.toUInt32LittleEndian())
}

fun Long.toByteArray(): ByteArray {
  val buffer = ByteBuffer.allocate(Long.SIZE_BYTES)
  return buffer.putLong(this).array()
}

fun ByteBuffer.indicesOf(prefix: ByteArray): List<Int> {
  if (prefix.isEmpty()) {
    return emptyList()
  }

  val indices = mutableListOf<Int>()

  outer@ for (i in 0 until this.limit() - prefix.size + 1) {
    for (j in prefix.indices) {
      if (this.get(i + j) != prefix[j]) {
        continue@outer
      }
    }
    indices.add(i)
  }
  return indices
}

fun ByteBuffer.put(buffer: ByteBuffer, offset: Int, length: Int) {
  val limit = buffer.limit()
  buffer.position(offset)
  buffer.limit(offset + length)
  this.put(buffer)
  buffer.limit(limit)
}