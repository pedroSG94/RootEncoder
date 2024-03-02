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

package com.pedro.common

import android.media.MediaCodec
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService

/**
 * Created by pedro on 3/11/23.
 */

fun MediaCodec.BufferInfo.isKeyframe(): Boolean {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    this.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME
  } else {
    this.flags == MediaCodec.BUFFER_FLAG_SYNC_FRAME
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

fun ByteBuffer.removeInfo(info: MediaCodec.BufferInfo): ByteBuffer {
  try {
    position(info.offset)
    limit(info.size)
  } catch (ignored: Exception) { }
  return slice()
}

inline infix fun <reified T: Any> BlockingQueue<T>.trySend(item: T): Boolean {
  return try {
    this.add(item)
    true
  } catch (e: IllegalStateException) {
    false
  }
}

suspend fun onMainThread(code: () -> Unit) {
  withContext(Dispatchers.Main) {
    code()
  }
}

fun onMainThreadHandler(code: () -> Unit) {
  Handler(Looper.getMainLooper()).post(code)
}

fun ByteArray.bytesToHex(): String {
  return joinToString("") { "%02x".format(it) }
}

fun ExecutorService.secureSubmit(code: () -> Unit) {
  try { submit { code() }.get() } catch (ignored: Exception) {}
}