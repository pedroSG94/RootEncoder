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

package com.pedro.library.util.sources.video

import android.graphics.SurfaceTexture

/**
 * Created by pedro on 11/1/24.
 */
abstract class VideoSource {

  var surfaceTexture: SurfaceTexture? = null
  var created = false
  var width = 0
  var height = 0
  var fps = 0
  var rotation = 0

  fun init(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
    this.width = width
    this.height = height
    this.fps = fps
    this.rotation = rotation
    created = create(width, height, fps, rotation)
    return created
  }
  
  protected abstract fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean
  abstract fun start(surfaceTexture: SurfaceTexture)
  abstract fun stop()
  abstract fun release()
  abstract fun isRunning(): Boolean
}