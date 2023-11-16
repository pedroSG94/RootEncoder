package com.pedro.common

import android.media.MediaCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.BlockingQueue

/**
 * Created by pedro on 3/11/23.
 */

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