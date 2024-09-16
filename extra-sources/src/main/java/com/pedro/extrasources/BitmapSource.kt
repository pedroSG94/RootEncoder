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

package com.pedro.extrasources

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.core.graphics.scale
import com.pedro.encoder.input.sources.video.VideoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Created by pedro on 19/3/24.
 */
class BitmapSource(private val bitmap: Bitmap): VideoSource() {

  @Volatile
  private var running = false
  private val scope = CoroutineScope(Dispatchers.IO)
  private var surface: Surface? = null
  private val paint = Paint()

  override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
    return true
  }

  override fun start(surfaceTexture: SurfaceTexture) {
    val scaledBitmap = bitmap.scale(width, height)
    surfaceTexture.setDefaultBufferSize(width, height)
    surface = Surface(surfaceTexture)
    running = true
    scope.launch {
      while (running) {
        try {
          val canvas = surface?.lockCanvas(null)
          canvas?.drawBitmap(scaledBitmap, 0f, 0f, paint)
          surface?.unlockCanvasAndPost(canvas)
          //sleep to emulate fps
          delay(1000 / fps.toLong())
        } catch (ignored: Exception) { }
      }
    }
  }

  override fun stop() {
    running = false
    scope.cancel()
    surface?.release()
    surface = null
  }

  override fun release() {
    if (!bitmap.isRecycled) bitmap.recycle()
  }

  override fun isRunning(): Boolean = running
}