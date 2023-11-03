package com.pedro.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.BlockingQueue

/**
 * Created by pedro on 3/11/23.
 */

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