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

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import com.pedro.encoder.input.decoder.DecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoder

/**
 * Created by pedro on 2/3/24.
 */
class VideoFileSource(
  private val context: Context,
  private val path: Uri
): VideoSource() {

  private var running = false
  private val videoDecoder = VideoDecoder({  }, object: DecoderInterface {
    override fun onLoop() {

    }
  }).apply { isLoopMode = true }

  override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
    val result = videoDecoder.initExtractor(context, path, null)
    if (!result) {
      throw IllegalArgumentException("Video file track not found")
    }
    return true
  }

  override fun start(surfaceTexture: SurfaceTexture) {
    videoDecoder.prepareVideo(Surface(surfaceTexture))
    videoDecoder.start()
    running = true
  }

  override fun stop() {
    running = false
    videoDecoder.stop()
  }

  override fun release() {}

  override fun isRunning(): Boolean = running

  fun moveTo(time: Double) {
    videoDecoder.moveTo(time)
  }
}