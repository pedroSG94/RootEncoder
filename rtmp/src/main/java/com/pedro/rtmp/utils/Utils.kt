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

import java.nio.ByteBuffer

/**
 * Created by pedro on 20/04/21.
 */

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