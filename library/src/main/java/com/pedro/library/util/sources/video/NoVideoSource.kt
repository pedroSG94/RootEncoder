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

package com.pedro.library.util.sources.video

import android.graphics.SurfaceTexture
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Created by pedro on 11/1/24.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class NoVideoSource: VideoSource() {

  private var running = false

  override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
    return true
  }

  override fun start(surfaceTexture: SurfaceTexture) {
    this.surfaceTexture = surfaceTexture
    if (!isRunning()) running = true
  }

  override fun stop() {
    if (isRunning()) running = false
  }

  override fun release() {}

  override fun isRunning(): Boolean = running
}