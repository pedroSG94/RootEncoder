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
import java.util.concurrent.BlockingQueue

inline infix fun <reified T: Any> BlockingQueue<T>.trySend(item: T): Boolean {
  return try {
    this.add(item)
    true
  } catch (e: IllegalStateException) {
    false
  }
}

fun ByteBuffer.toByteArray(): ByteArray {
  return if (this.hasArray() && !isDirect) {
    this.array()
  } else {
    this.rewind()
    val byteArray = ByteArray(this.remaining())
    this.get(byteArray)
    byteArray
  }
}

fun ByteBuffer.startWith(byteArray: ByteArray): Boolean {
  val startData = ByteArray(byteArray.size)
  this.rewind()
  this.get(startData)
  return startData.contentEquals(byteArray)
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

suspend fun onMainThread(code: () -> Unit) {
  withContext(Dispatchers.Main) {
    code()
  }
}