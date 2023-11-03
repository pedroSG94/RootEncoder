package com.pedro.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Created by pedro on 3/11/23.
 */

suspend fun onMainThread(code: () -> Unit) {
  withContext(Dispatchers.Main) {
    code()
  }
}