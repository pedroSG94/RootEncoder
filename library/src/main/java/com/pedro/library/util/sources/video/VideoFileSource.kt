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

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import com.pedro.encoder.input.decoder.DecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoder
import java.io.IOException

/**
 * Created by pedro on 2/3/24.
 */
class VideoFileSource(
  private val context: Context,
  private val path: Uri,
  loopMode: Boolean = true,
  onFinish: (isLoop: Boolean) -> Unit = {}
): VideoSource() {

  private val videoDecoderInterface: () -> Unit = {
    onFinish(false)
  }
  private val decoderInterface = object: DecoderInterface {
    override fun onLoop() {
      onFinish(true)
    }
  }
  private var running = false
  private var videoDecoder = VideoDecoder(videoDecoderInterface, decoderInterface)

  init {
    setLoopMode(loopMode)
  }

  override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
    val result = videoDecoder.initExtractor(context, path, null)
    if (!result) {
      throw IllegalArgumentException("Video file track not found")
    }
    return true
  }

  override fun start(surfaceTexture: SurfaceTexture) {
    this.surfaceTexture = surfaceTexture
    videoDecoder.prepareVideo(Surface(surfaceTexture))
    videoDecoder.start()
    running = true
  }

  override fun stop() {
    running = false
    videoDecoder.stop()
  }

  override fun release() {
    if (running) stop()
  }

  override fun isRunning(): Boolean = running

  fun moveTo(time: Double) {
    videoDecoder.moveTo(time)
  }

  fun getDuration() = videoDecoder.duration

  fun getTime() = videoDecoder.time

  fun setLoopMode(enabled: Boolean) {
    videoDecoder.isLoopMode = enabled
  }

  @Throws(IOException::class)
  fun replaceFile(context: Context, uri: Uri) {
    val width = videoDecoder.width
    val height = videoDecoder.height
    val wasRunning = videoDecoder.isRunning
    val videoDecoder = VideoDecoder(videoDecoderInterface, decoderInterface)
    if (!videoDecoder.initExtractor(context, uri, null)) throw IOException("Extraction failed")
    if (width != videoDecoder.width || height != videoDecoder.height) throw IOException("Resolution must be the same that the previous file")
    this.videoDecoder.stop()
    this.videoDecoder = videoDecoder
    if (wasRunning) {
      videoDecoder.prepareVideo(Surface(surfaceTexture))
      videoDecoder.start()
    }
  }
}