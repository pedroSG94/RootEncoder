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
import com.pedro.encoder.input.decoder.VideoDecoderInterface


/**
 * Created by pedro on 19/6/24.
 */
class MultiVideoFileSource(
  private val context: Context,
  private val path: List<Uri>,
  private var loopMode: Boolean = false
): VideoSource() {

  private var running = false
  private var currentDecoder = 0
  private val decoders = mutableListOf<VideoDecoder>()

  private val videoDecoderInterface = VideoDecoderInterface {
    decoders[currentDecoder].stop()
    if (!loopMode && currentDecoder == decoders.size - 1) {
      return@VideoDecoderInterface
    }
    currentDecoder = if (currentDecoder == decoders.size - 1) 0 else currentDecoder + 1
    decoders[currentDecoder].initExtractor(context, path[currentDecoder], null)
    decoders[currentDecoder].prepareVideo(Surface(surfaceTexture))
    decoders[currentDecoder].start()
  }
  private val decoderInterface: DecoderInterface = object: DecoderInterface {
    override fun onLoop() { }
  }

  override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
    if (path.isEmpty()) throw IllegalArgumentException("empty list of files is not allowed")
    path.forEach {
      val decoder = VideoDecoder(videoDecoderInterface, decoderInterface)
      val result = decoder.initExtractor(context, it, null)
      if (!result) {
        throw IllegalArgumentException("Video file track not found")
      }
      decoders.add(decoder)
    }
    val w = decoders[0].width
    val h = decoders[0].height
    decoders.forEach {
      if (w != it.width || h != it.height) {
        throw IllegalArgumentException("All video files must contain the same resolution")
      }
    }
    return true
  }

  override fun start(surfaceTexture: SurfaceTexture) {
    this.surfaceTexture = surfaceTexture
    decoders[currentDecoder].initExtractor(context, path[currentDecoder], null)
    decoders[currentDecoder].prepareVideo(Surface(surfaceTexture))
    decoders[currentDecoder].start()
    running = true
  }

  override fun stop() {
    decoders[currentDecoder].stop()
    running = false
    currentDecoder = 0
  }

  override fun release() {
    if (running) stop()
  }

  override fun isRunning(): Boolean = running

  fun moveTo(time: Double, fileIndex: Int = currentDecoder) {
    decoders[fileIndex].moveTo(time)
  }

  fun getCurrentUsedFile() = currentDecoder

  fun getDuration(fileIndex: Int = currentDecoder) = decoders[fileIndex].duration

  fun getTime(fileIndex: Int = currentDecoder) = decoders[fileIndex].time

  fun setLoopMode(enabled: Boolean) {
    this.loopMode = enabled
  }

  fun pauseRender() {
    decoders[currentDecoder].pauseRender()
  }

  fun resumeRender() {
    decoders[currentDecoder].resumeRender()
  }
}