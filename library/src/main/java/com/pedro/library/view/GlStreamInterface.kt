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

package com.pedro.library.view

import android.content.Context
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.os.Build
import android.view.Surface
import androidx.annotation.RequiresApi
import com.pedro.common.newSingleThreadExecutor
import com.pedro.common.secureSubmit
import com.pedro.encoder.input.gl.FilterAction
import com.pedro.encoder.input.gl.SurfaceManager
import com.pedro.encoder.input.gl.render.MainRender
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.input.gl.render.filters.NoFilterRender
import com.pedro.encoder.input.video.FpsLimiter
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.encoder.utils.gl.GlUtil
import com.pedro.library.util.Filter
import com.pedro.library.util.SensorRotationManager
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean


/**
 * Created by pedro on 14/3/22.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class GlStreamInterface(private val context: Context): OnFrameAvailableListener, GlInterface {

  private var takePhotoCallback: TakePhotoCallback? = null
  private val running = AtomicBoolean(false)
  private val surfaceManager = SurfaceManager()
  private val surfaceManagerEncoder = SurfaceManager()
  private val surfaceManagerPhoto = SurfaceManager()
  private val surfaceManagerPreview = SurfaceManager()
  private val mainRender = MainRender()
  private var encoderWidth = 0
  private var encoderHeight = 0
  private var streamOrientation = 0
  private var previewWidth = 0
  private var previewHeight = 0
  private var previewOrientation = 0
  private var isPortrait = false
  private var orientationForced = OrientationForced.NONE
  private val filterQueue: BlockingQueue<Filter> = LinkedBlockingQueue()
  private val threadQueue = LinkedBlockingQueue<Runnable>()
  private var muteVideo = false
  private var isPreviewHorizontalFlip: Boolean = false
  private var isPreviewVerticalFlip = false
  private var isStreamHorizontalFlip = false
  private var isStreamVerticalFlip = false
  private var aspectRatioMode = AspectRatioMode.Adjust
  private var executor: ExecutorService? = null
  private val fpsLimiter = FpsLimiter()
  private val forceRender = ForceRenderer()
  var autoHandleOrientation = false
  private val sensorRotationManager = SensorRotationManager(context, true, true) { orientation, isPortrait ->
    if (autoHandleOrientation) {
      setCameraOrientation(orientation)
      this.isPortrait = isPortrait
    }
  }

  override fun setEncoderSize(width: Int, height: Int) {
    encoderWidth = width
    encoderHeight = height
  }

  override fun getEncoderSize(): Point {
    return Point(encoderWidth, encoderHeight)
  }

  override fun muteVideo() {
    muteVideo = true
  }

  override fun unMuteVideo() {
    muteVideo = false
  }

  override fun isVideoMuted(): Boolean = muteVideo

  override fun setForceRender(enabled: Boolean, fps: Int) {
    forceRender.setEnabled(enabled, fps)
  }

  override fun setForceRender(enabled: Boolean) {
    setForceRender(enabled, 5)
  }

  override fun isRunning(): Boolean = running.get()

  override fun getSurfaceTexture(): SurfaceTexture {
    return mainRender.getSurfaceTexture()
  }

  override fun getSurface(): Surface {
    return mainRender.getSurface()
  }

  override fun addMediaCodecSurface(surface: Surface) {
    executor?.secureSubmit {
      if (surfaceManager.isReady) {
        surfaceManagerEncoder.release()
        surfaceManagerEncoder.eglSetup(surface, surfaceManager)
      }
    }
  }

  override fun removeMediaCodecSurface() {
    threadQueue.clear()
    executor?.secureSubmit {
      surfaceManagerEncoder.release()
    }
  }

  override fun takePhoto(takePhotoCallback: TakePhotoCallback?) {
    this.takePhotoCallback = takePhotoCallback
  }

  override fun start() {
    executor = newSingleThreadExecutor(threadQueue)
    executor?.secureSubmit {
      surfaceManager.release()
      surfaceManager.eglSetup()
      surfaceManager.makeCurrent()
      mainRender.initGl(context, encoderWidth, encoderHeight, encoderWidth, encoderHeight)
      surfaceManagerPhoto.release()
      surfaceManagerPhoto.eglSetup(encoderWidth, encoderHeight, surfaceManager)
      running.set(true)
      mainRender.getSurfaceTexture().setOnFrameAvailableListener(this)
      forceRender.start { executor?.execute { draw(true) } }
      if (autoHandleOrientation) sensorRotationManager.start()
    }
  }

  override fun stop() {
    running.set(false)
    threadQueue.clear()
    executor?.secureSubmit {
      forceRender.stop()
      sensorRotationManager.stop()
      surfaceManagerPhoto.release()
      surfaceManagerEncoder.release()
      surfaceManager.release()
      mainRender.release()
    }
    executor?.shutdownNow()
    executor = null
  }

  private fun draw(forced: Boolean) {
    if (!isRunning || fpsLimiter.limitFPS()) return
    if (!forced) forceRender.frameAvailable()

    if (surfaceManager.isReady && mainRender.isReady()) {
      surfaceManager.makeCurrent()
      mainRender.updateFrame()
      mainRender.drawOffScreen()
      surfaceManager.swapBuffer()
    }

    if (!filterQueue.isEmpty() && mainRender.isReady()) {
      try {
        val filter = filterQueue.take()
        mainRender.setFilterAction(filter.filterAction, filter.position, filter.baseFilterRender)
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        return
      }
    }

    val orientation = when (orientationForced) {
      OrientationForced.PORTRAIT -> true
      OrientationForced.LANDSCAPE -> false
      OrientationForced.NONE -> isPortrait
    }
    // render VideoEncoder (stream and record)
    if (surfaceManagerEncoder.isReady && mainRender.isReady()) {
      val w = if (muteVideo) 0 else encoderWidth
      val h = if (muteVideo) 0 else encoderHeight
      surfaceManagerEncoder.makeCurrent()
      mainRender.drawScreenEncoder(w, h, orientation, streamOrientation,
        isStreamVerticalFlip, isStreamHorizontalFlip)
      surfaceManagerEncoder.swapBuffer()
    }
    //render surface photo if request photo
    if (takePhotoCallback != null && surfaceManagerPhoto.isReady && mainRender.isReady()) {
      surfaceManagerPhoto.makeCurrent()
      mainRender.drawScreen(encoderWidth, encoderHeight, AspectRatioMode.NONE,
        streamOrientation, isStreamVerticalFlip, isStreamHorizontalFlip)
      takePhotoCallback?.onTakePhoto(GlUtil.getBitmap(encoderWidth, encoderHeight))
      takePhotoCallback = null
      surfaceManagerPhoto.swapBuffer()
    }
    // render preview
    if (surfaceManagerPreview.isReady && mainRender.isReady()) {
      val w =  if (previewWidth == 0) encoderWidth else previewWidth
      val h =  if (previewHeight == 0) encoderHeight else previewHeight
      surfaceManagerPreview.makeCurrent()
      mainRender.drawScreenPreview(w, h, orientation, aspectRatioMode, previewOrientation,
        isPreviewVerticalFlip, isPreviewHorizontalFlip)
      surfaceManagerPreview.swapBuffer()
    }
  }

  override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
    if (!isRunning) return
    executor?.execute { draw(false) }
  }

  fun forceOrientation(forced: OrientationForced) {
    this.orientationForced = forced
  }

  fun attachPreview(surface: Surface) {
    executor?.secureSubmit {
      if (surfaceManager.isReady) {
        surfaceManagerPreview.release()
        surfaceManagerPreview.eglSetup(surface, surfaceManager)
      }
    }
  }

  fun deAttachPreview() {
    executor?.secureSubmit {
      surfaceManagerPreview.release()
    }
  }

  override fun setStreamRotation(orientation: Int) {
    this.streamOrientation = orientation
  }

  fun setPreviewResolution(width: Int, height: Int) {
    this.previewWidth = width
    this.previewHeight = height
  }

  fun setIsPortrait(isPortrait: Boolean) {
    this.isPortrait = isPortrait
  }

  fun setPreviewRotation(orientation: Int) {
    this.previewOrientation = orientation
  }

  fun setCameraOrientation(orientation: Int) {
    mainRender.setCameraRotation(orientation)
  }

  override fun setFilter(filterPosition: Int, baseFilterRender: BaseFilterRender) {
    filterQueue.add(Filter(FilterAction.SET_INDEX, filterPosition, baseFilterRender))
  }

  override fun addFilter(baseFilterRender: BaseFilterRender) {
    filterQueue.add(Filter(FilterAction.ADD, 0, baseFilterRender))
  }

  override fun addFilter(filterPosition: Int, baseFilterRender: BaseFilterRender) {
    filterQueue.add(Filter(FilterAction.ADD_INDEX, filterPosition, baseFilterRender))
  }

  override fun clearFilters() {
    filterQueue.add(Filter(FilterAction.CLEAR, 0, NoFilterRender()))
  }

  override fun removeFilter(filterPosition: Int) {
    filterQueue.add(Filter(FilterAction.REMOVE_INDEX, filterPosition, NoFilterRender()))
  }

  override fun removeFilter(baseFilterRender: BaseFilterRender) {
    filterQueue.add(Filter(FilterAction.REMOVE, 0, baseFilterRender))
  }

  override fun filtersCount(): Int {
    return mainRender.filtersCount()
  }

  override fun setRotation(rotation: Int) {
    mainRender.setCameraRotation(rotation)
  }

  override fun forceFpsLimit(fps: Int) {
    fpsLimiter.setFPS(fps)
  }

  override fun setIsStreamHorizontalFlip(flip: Boolean) {
    isStreamHorizontalFlip = flip
  }

  override fun setIsStreamVerticalFlip(flip: Boolean) {
    isStreamVerticalFlip = flip
  }

  override fun setIsPreviewHorizontalFlip(flip: Boolean) {
    isPreviewHorizontalFlip = flip
  }

  override fun setIsPreviewVerticalFlip(flip: Boolean) {
    isPreviewVerticalFlip = flip
  }

  override fun setFilter(baseFilterRender: BaseFilterRender) {
    filterQueue.add(Filter(FilterAction.SET, 0, baseFilterRender))
  }

  fun setAspectRatioMode(aspectRatioMode: AspectRatioMode) {
    this.aspectRatioMode = aspectRatioMode
  }
}