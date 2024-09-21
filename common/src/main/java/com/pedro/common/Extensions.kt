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

package com.pedro.common

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodec
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation

/**
 * Created by pedro on 3/11/23.
 */

@Suppress("DEPRECATION")
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

@JvmOverloads
fun ExecutorService.secureSubmit(timeout: Long = 1000, code: () -> Unit) {
  try {
    if (isTerminated || isShutdown) return
    submit { code() }.get(timeout, TimeUnit.MILLISECONDS)
  } catch (ignored: Exception) {}
}

fun String.getMd5Hash(): String {
  val md: MessageDigest
  try {
    md = MessageDigest.getInstance("MD5")
    return md.digest(toByteArray()).bytesToHex()
  } catch (ignore: NoSuchAlgorithmException) {
  } catch (ignore: UnsupportedEncodingException) {
  }
  return ""
}

fun newSingleThreadExecutor(queue: LinkedBlockingQueue<Runnable>): ExecutorService {
  return ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue)
}

fun getSuspendContext(): Continuation<Unit> {
  return object : Continuation<Unit> {
    override val context = Dispatchers.IO
    override fun resumeWith(result: Result<Unit>) {}
  }
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun <T> CameraCharacteristics.secureGet(key: CameraCharacteristics.Key<T>): T? {
  return try { get(key) } catch (e: IllegalArgumentException) { null }
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun <T> CaptureRequest.Builder.secureGet(key: CaptureRequest.Key<T>): T? {
  return try { get(key) } catch (e: IllegalArgumentException) { null }
}

fun String.getIndexes(char: Char): Array<Int> {
  val indexes = mutableListOf<Int>()
  forEachIndexed { index, c -> if (c == char) indexes.add(index) }
  return indexes.toTypedArray()
}