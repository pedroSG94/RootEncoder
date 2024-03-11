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

package com.pedro.library.view

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Created by pedro on 2/3/24.
 *
 * This class force GlInterface to render in order to avoid no fps.
 * This is mainly for ScreenSource
 */
class ForceRenderer {

  private var enabled = false
  private var job: Job? = null
  private var fps = 5L
  @Volatile
  private var running = false
  @Volatile
  private var renderer = false

  fun setEnabled(enabled: Boolean, fps: Int) {
    this.enabled = enabled
    if (fps <= 0) this.enabled = false
    this.fps = fps.toLong()
  }

  fun start(callback: () -> Unit) {
    if (!enabled) return
    running = true
    job = CoroutineScope(Dispatchers.IO).launch {
      while (running) {
        delay(1000 / fps)
        if (renderer) renderer = false
        else callback()
      }
    }
  }

  fun stop() {
    running = false
    job?.cancel()
    renderer = false
  }

  fun isRunning(): Boolean = running

  fun frameAvailable() {
    renderer = true
  }
}