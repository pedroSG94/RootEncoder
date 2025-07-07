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
import com.pedro.encoder.input.sources.OrientationConfig
import com.pedro.encoder.input.sources.OrientationForced
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.input.video.FpsLimiter
import com.pedro.encoder.utils.ViewPort
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.encoder.utils.gl.GlUtil
import com.pedro.library.util.Filter
import com.pedro.library.util.SensorRotationManager
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max


/**
 * Created by pedro on 14/3/22.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class GlStreamInterface(private val context: Context): OnFrameAvailableListener, GlInterface {

  private var takePhotoCallback: TakePhotoCallback? = null
  private val running = AtomicBoolean(false)
  private val surfaceManager = SurfaceManager()
  private val surfaceManagerEncoder = SurfaceManager()
  private val surfaceManagerEncoderRecord = SurfaceManager()
  private val surfaceManagerPhoto = SurfaceManager()
  private val surfaceManagerPreview = SurfaceManager()
  private val mainRender = MainRender()
  private var encoderWidth = 0
  private var encoderHeight = 0
  private var encoderRecordWidth = 0
  private var encoderRecordHeight = 0
  private var streamOrientation = 0
  private var previewWidth = 0
  private var previewHeight = 0
  private var isPortrait = false
  private var isPortraitPreview = false
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
  private var shouldHandleOrientation = true
  private var renderErrorCallback: RenderErrorCallback? = null
  private var previewViewPort: ViewPort? = null
  private var streamViewPort: ViewPort? = null

  private val sensorRotationManager = SensorRotationManager(context, true, true) { orientation, isPortrait ->
    if (autoHandleOrientation && shouldHandleOrientation) {
      setCameraOrientation(orientation)
      setIsPortrait(isPortrait)
    }
  }

  override fun setEncoderSize(width: Int, height: Int) {
    encoderWidth = width
    encoderHeight = height
  }

  override fun setEncoderRecordSize(width: Int, height: Int) {
    encoderRecordWidth = width
    encoderRecordHeight = height
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

  override fun setRenderErrorCallback(callback: RenderErrorCallback?) {
    this.renderErrorCallback = callback
  }

  override fun getSurfaceTexture(): SurfaceTexture {
    return mainRender.getSurfaceTexture()
  }

  override fun getSurface(): Surface {
    return mainRender.getSurface()
  }

  override fun addMediaCodecSurface(surface: Surface) {
    if (surfaceManager.isReady) {
      surfaceManagerEncoder.release()
      surfaceManagerEncoder.eglSetup(surface, surfaceManager)
    }
  }

  override fun removeMediaCodecSurface() {
    threadQueue.clear()
    surfaceManagerEncoder.release()
  }

  override fun addMediaCodecRecordSurface(surface: Surface) {
    if (surfaceManager.isReady) {
      surfaceManagerEncoderRecord.release()
      surfaceManagerEncoderRecord.eglSetup(surface, surfaceManager)
    }
  }

  override fun removeMediaCodecRecordSurface() {
    threadQueue.clear()
    surfaceManagerEncoderRecord.release()
  }

  override fun takePhoto(takePhotoCallback: TakePhotoCallback?) {
    this.takePhotoCallback = takePhotoCallback
  }

  override fun start() {
    threadQueue.clear()
    executor?.shutdownNow()
    executor = null
    executor = newSingleThreadExecutor(threadQueue)
    val width = max(encoderWidth, encoderRecordWidth)
    val height = max(encoderHeight, encoderRecordHeight)
    surfaceManager.release()
    surfaceManager.eglSetup()
    surfaceManagerPhoto.release()
    surfaceManagerPhoto.eglSetup(width, height, surfaceManager)
    sensorRotationManager.start()
    executor?.secureSubmit {
      surfaceManager.makeCurrent()
      mainRender.initGl(context, width, height, width, height)
      running.set(true)
      mainRender.getSurfaceTexture().setOnFrameAvailableListener(this)
      forceRender.start {
        executor?.execute {
          try {
            draw(true)
          } catch (e: RuntimeException) {
            renderErrorCallback?.onRenderError(e) ?: throw e
          }
        }
      }
    }
  }

  override fun stop() {
    running.set(false)
    threadQueue.clear()
    executor?.shutdownNow()
    executor = null
    forceRender.stop()
    sensorRotationManager.stop()
    surfaceManagerPhoto.release()
    surfaceManagerEncoder.release()
    surfaceManagerEncoderRecord.release()
    surfaceManager.release()
    mainRender.release()
  }

  private fun draw(forced: Boolean) {
    if (!isRunning) return
    val limitFps = fpsLimiter.limitFPS()
    if (!forced) forceRender.frameAvailable()

    if (!filterQueue.isEmpty() && mainRender.isReady()) {
      try {
        if (surfaceManager.makeCurrent()) {
          val filter = filterQueue.take()
          mainRender.setFilterAction(filter.filterAction, filter.position, filter.baseFilterRender)
        }
      } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        return
      }
    }

    if (surfaceManager.isReady && mainRender.isReady()) {
      if (!surfaceManager.makeCurrent()) return
      mainRender.updateFrame()
      mainRender.drawSource()
      surfaceManager.swapBuffer()
    }

    val orientation = when (orientationForced) {
      OrientationForced.PORTRAIT -> true
      OrientationForced.LANDSCAPE -> false
      OrientationForced.NONE -> isPortrait
    }
    val orientationPreview = when (orientationForced) {
      OrientationForced.PORTRAIT -> true
      OrientationForced.LANDSCAPE -> false
      OrientationForced.NONE -> isPortraitPreview
    }
    if (surfaceManagerEncoder.isReady || surfaceManagerEncoderRecord.isReady || surfaceManagerPhoto.isReady) {
      mainRender.drawFilters(false)
    }
    // render VideoEncoder (stream and record)
    if (surfaceManagerEncoder.isReady && mainRender.isReady() && !limitFps) {
      val w = if (muteVideo) 0 else encoderWidth
      val h = if (muteVideo) 0 else encoderHeight
      if (surfaceManagerEncoder.makeCurrent()) {
        mainRender.drawScreenEncoder(w, h, orientation, streamOrientation,
          isStreamVerticalFlip, isStreamHorizontalFlip, streamViewPort)
        surfaceManagerEncoder.swapBuffer()
      }
    }
    // render VideoEncoder (record if the resolution is different than stream)
    if (surfaceManagerEncoderRecord.isReady && mainRender.isReady() && !limitFps) {
      val w = if (muteVideo) 0 else encoderRecordWidth
      val h = if (muteVideo) 0 else encoderRecordHeight
      if (surfaceManagerEncoderRecord.makeCurrent()) {
        mainRender.drawScreenEncoder(w, h, orientation, streamOrientation,
          isStreamVerticalFlip, isStreamHorizontalFlip, streamViewPort)
        surfaceManagerEncoderRecord.swapBuffer()
      }
    }
    //render surface photo if request photo
    if (takePhotoCallback != null && surfaceManagerPhoto.isReady && mainRender.isReady()) {
      if (surfaceManagerPhoto.makeCurrent()) {
        mainRender.drawScreen(encoderWidth, encoderHeight, AspectRatioMode.NONE,
          streamOrientation, isStreamVerticalFlip, isStreamHorizontalFlip, streamViewPort)
        takePhotoCallback?.onTakePhoto(GlUtil.getBitmap(encoderWidth, encoderHeight))
        takePhotoCallback = null
        surfaceManagerPhoto.swapBuffer()
      }
    }
    // render preview
    if (surfaceManagerPreview.isReady && mainRender.isReady() && !limitFps) {
      val w =  if (previewWidth == 0) encoderWidth else previewWidth
      val h =  if (previewHeight == 0) encoderHeight else previewHeight
      if (surfaceManager.makeCurrent()) {
        mainRender.drawFilters(true)
        surfaceManager.swapBuffer()
      }
      if (surfaceManagerPreview.makeCurrent()) {
        mainRender.drawScreenPreview(w, h, orientationPreview, aspectRatioMode, 0,
          isPreviewVerticalFlip, isPreviewHorizontalFlip, previewViewPort)
        surfaceManagerPreview.swapBuffer()
      }
    }
  }

  override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
    if (!isRunning) return
    executor?.execute {
      try {
        draw(false)
      } catch (e: RuntimeException) {
        renderErrorCallback?.onRenderError(e) ?: throw e
      }
    }
  }

  fun setOrientationConfig(orientationConfig: OrientationConfig) {
    when (orientationConfig.forced) {
      OrientationForced.PORTRAIT, OrientationForced.LANDSCAPE -> {
        forceOrientation(orientationConfig.forced)
      }
      OrientationForced.NONE -> {
        if (orientationConfig.isPortrait == null && orientationConfig.cameraOrientation == null) {
          forceOrientation(orientationConfig.forced)
        } else {
          orientationConfig.isPortrait?.let { setIsPortrait(it) }
          orientationConfig.cameraOrientation?.let { setCameraOrientation(it) }
          shouldHandleOrientation = false
          this.orientationForced = orientationConfig.forced
        }
      }
    }
  }

  fun forceOrientation(forced: OrientationForced) {
    when (forced) {
      OrientationForced.PORTRAIT -> {
        setCameraOrientation(90)
        shouldHandleOrientation = false
      }
      OrientationForced.LANDSCAPE -> {
        setCameraOrientation(0)
        shouldHandleOrientation = false
      }
      OrientationForced.NONE -> {
        val orientation = CameraHelper.getCameraOrientation(context)
        setCameraOrientation(if (orientation == 0) 270 else orientation - 90)
        shouldHandleOrientation = true
      }
    }
    this.orientationForced = forced
  }

  fun attachPreview(surface: Surface) {
    if (surfaceManager.isReady) {
      surfaceManagerPreview.release()
      surfaceManagerPreview.eglSetup(surface, surfaceManager)
    }
  }

  fun deAttachPreview() {
    surfaceManagerPreview.release()
  }

  override fun setStreamRotation(orientation: Int) {
    this.streamOrientation = orientation
  }

  fun setPreviewResolution(width: Int, height: Int) {
    this.previewWidth = width
    this.previewHeight = height
  }

  fun setIsPortrait(isPortrait: Boolean) {
    setPreviewIsPortrait(isPortrait)
    setStreamIsPortrait(isPortrait)
  }

  fun setPreviewIsPortrait(isPortrait: Boolean) {
    this.isPortraitPreview = isPortrait
  }

  fun setStreamIsPortrait(isPortrait: Boolean) {
    this.isPortrait = isPortrait
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
    setCameraOrientation(rotation)
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

  fun setPreviewViewPort(viewPort: ViewPort?) {
    previewViewPort = viewPort
  }

  fun setStreamViewPort(viewPort: ViewPort?) {
    streamViewPort = viewPort
  }
}