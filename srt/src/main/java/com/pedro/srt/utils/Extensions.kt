/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.srt.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

fun ByteBuffer.put48Bits(long: Long) {
  val shiftedLong = long and 0x0000FFFFFFFFFFFFL
  val i = (shiftedLong shr 32).toInt()
  val s = long.toShort()
  this.putInt(i)
  this.putShort(s)
}

fun ByteBuffer.chunks(chunkSize: Int): List<ByteArray> {
  val chunks = mutableListOf<ByteArray>()
  this.flip()

  while (this.remaining() > 0) {
    val chunk = ByteArray(minOf(chunkSize, this.remaining()))
    this.get(chunk)
    chunks.add(chunk)
  }
  return chunks
}

fun Boolean.toInt(): Int {
  return if (this) 1 else 0
}

fun Int.toBoolean(): Boolean {
  return this == 1
}

fun InputStream.readUInt16(): Int {
  return read() and 0xff shl 8 or (read() and 0xff)
}

fun InputStream.readUInt32(): Int {
  return read() and 0xff shl 24 or (read() and 0xff shl 16) or (read() and 0xff shl 8) or (read() and 0xff)
}

fun OutputStream.writeUInt32(value: Int) {
  write(value ushr 24)
  write(value ushr 16)
  write(value ushr 8)
  write(value)
}

fun OutputStream.writeUInt16(value: Int) {
  write(value ushr 8)
  write(value)
}

fun InputStream.readUntil(byteArray: ByteArray) {
  var bytesRead = 0
  while (bytesRead < byteArray.size) {
    val result = read(byteArray, bytesRead, byteArray.size - bytesRead)
    if (result != -1) bytesRead += result
  }
}

fun ByteArray.chunked(chunkSize: Int): List<ByteArray> {
  val chunks = mutableListOf<ByteArray>()
  var currentIndex = 0

  while (currentIndex < this.size) {
    val endIndex = kotlin.math.min(currentIndex + chunkSize, this.size)
    val chunk = this.sliceArray(currentIndex until endIndex)
    chunks.add(chunk)
    currentIndex = endIndex
  }
  return chunks
}

suspend fun onMainThread(code: () -> Unit) {
  withContext(Dispatchers.Main) {
    code()
  }
}