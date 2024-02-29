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

import android.content.Context
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.os.Build
import android.view.Surface
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.gl.FilterAction
import com.pedro.encoder.input.gl.SurfaceManager
import com.pedro.encoder.input.gl.render.MainRender
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.input.video.FpsLimiter
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.encoder.utils.gl.GlUtil
import com.pedro.library.util.Filter
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore

/**
 * Created by pedro on 14/3/22.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class GlStreamInterface @JvmOverloads constructor(
  private val context: Context,
  private val readSurface: Surface? = null
): Runnable, OnFrameAvailableListener, GlInterface {

  private var thread: Thread? = null
  private var frameAvailable = false
  private var takePhotoCallback: TakePhotoCallback? = null
  var running = false
    private set
  private val surfaceManager = SurfaceManager()
  private val surfaceManagerEncoder = SurfaceManager()
  private val surfaceManagerPhoto = SurfaceManager()
  private val surfaceManagerPreview = SurfaceManager()
  private val managerRender = MainRender()
  private val semaphore = Semaphore(0)
  private val sync = Object()
  private var encoderWidth = 0
  private var encoderHeight = 0
  private var streamOrientation = 0
  private var previewWidth = 0
  private var previewHeight = 0
  private var previewOrientation = 0
  private var isPortrait = false
  private var orientationForced = OrientationForced.NONE
  private val fpsLimiter = FpsLimiter()
  private val filterQueue: BlockingQueue<Filter> = LinkedBlockingQueue()
  private var forceRender = false
  private var muteVideo = false
  private var isPreviewHorizontalFlip: Boolean = false
  private var isPreviewVerticalFlip = false
  private var isStreamHorizontalFlip = false
  private var isStreamVerticalFlip = false
  private var aspectRatioMode = AspectRatioMode.Adjust

  override fun setEncoderSize(width: Int, height: Int) {
    encoderWidth = width
    encoderHeight = height
  }

  override fun getEncoderSize(): Point {
    return Point(encoderWidth, encoderHeight)
  }

  override fun setFps(fps: Int) {
    fpsLimiter.setFPS(fps)
  }

  override fun muteVideo() {
    muteVideo = true
  }

  override fun unMuteVideo() {
    muteVideo = false
  }

  override fun isVideoMuted(): Boolean = muteVideo

  override fun setForceRender(forceRender: Boolean) {
    this.forceRender = forceRender
  }

  override fun getSurfaceTexture(): SurfaceTexture {
    return managerRender.getSurfaceTexture()
  }

  override fun getSurface(): Surface {
    return managerRender.getSurface()
  }

  override fun addMediaCodecSurface(surface: Surface) {
    synchronized(sync) {
      if (surfaceManager.isReady) {
        surfaceManagerEncoder.release()
        surfaceManagerEncoder.eglSetup(surface, surfaceManager)
      }
    }
  }

  override fun removeMediaCodecSurface() {
    synchronized(sync) {
      surfaceManagerEncoder.release()
    }
  }

  override fun takePhoto(takePhotoCallback: TakePhotoCallback?) {
    this.takePhotoCallback = takePhotoCallback
  }

  override fun start() {
    synchronized(sync) {
      thread = Thread(this)
      running = true
      thread?.start()
      semaphore.acquireUninterruptibly()
    }
  }

  override fun stop() {
    running = false
    thread?.interrupt()
    try {
      thread?.join(100)
    } catch (e: InterruptedException) {
      thread?.interrupt()
    }
    thread = null
    synchronized(sync) {
      surfaceManagerPhoto.release()
      surfaceManagerEncoder.release()
      surfaceManager.release()
    }
  }

  override fun run() {
    surfaceManager.release()
    surfaceManager.eglSetup(readSurface)
    surfaceManager.makeCurrent()
    managerRender.initGl(context, encoderWidth, encoderHeight, encoderWidth, encoderHeight)
    managerRender.getSurfaceTexture().setOnFrameAvailableListener(this)
    surfaceManagerPhoto.release()
    surfaceManagerPhoto.eglSetup(encoderWidth, encoderHeight, surfaceManager)
    semaphore.release()
    try {
      while (running) {
        fpsLimiter.setFrameStartTs()
        if (frameAvailable || forceRender) {
          frameAvailable = false
          synchronized(sync) {
            if (surfaceManager.isReady && managerRender.isReady) {
              surfaceManager.makeCurrent()
              managerRender.updateFrame()
              managerRender.drawOffScreen()
              managerRender.drawScreen(encoderWidth, encoderHeight, AspectRatioMode.NONE, 0,
                flipStreamVertical = false, flipStreamHorizontal = false)
              surfaceManager.swapBuffer()
            }
            if (!filterQueue.isEmpty() && managerRender.isReady) {
              val filter = filterQueue.take()
              managerRender.setFilterAction(filter.filterAction, filter.position, filter.baseFilterRender)
            }
          }

          synchronized(sync) {
            val limitFps = fpsLimiter.limitFPS()
            val orientation = when (orientationForced) {
              OrientationForced.PORTRAIT -> true
              OrientationForced.LANDSCAPE -> false
              OrientationForced.NONE -> isPortrait
            }
            // render VideoEncoder (stream and record)
            if (surfaceManagerEncoder.isReady && managerRender.isReady && !limitFps) {
              val w = if (muteVideo) 0 else encoderWidth
              val h = if (muteVideo) 0 else encoderHeight
              surfaceManagerEncoder.makeCurrent()
              managerRender.drawScreenEncoder(w, h, orientation, streamOrientation,
                isStreamVerticalFlip, isStreamHorizontalFlip)
              surfaceManagerEncoder.swapBuffer()
            }
            //render surface photo if request photo
            if (takePhotoCallback != null && surfaceManagerPhoto.isReady && managerRender.isReady) {
              surfaceManagerPhoto.makeCurrent()
              managerRender.drawScreen(encoderWidth, encoderHeight, AspectRatioMode.NONE,
                streamOrientation, isStreamVerticalFlip, isStreamHorizontalFlip)
              takePhotoCallback?.onTakePhoto(GlUtil.getBitmap(encoderWidth, encoderHeight))
              takePhotoCallback = null
              surfaceManagerPhoto.swapBuffer()
            }
            // render preview
            if (surfaceManagerPreview.isReady && managerRender.isReady && !limitFps) {
              val w =  if (previewWidth == 0) encoderWidth else previewWidth
              val h =  if (previewHeight == 0) encoderHeight else previewHeight
              surfaceManagerPreview.makeCurrent()
              managerRender.drawScreenPreview(w, h, orientation, aspectRatioMode, previewOrientation,
                isPreviewVerticalFlip, isPreviewHorizontalFlip)
              surfaceManagerPreview.swapBuffer()
            }
          }
        }
        synchronized(sync) {
          val sleep = fpsLimiter.sleepTime
          if (sleep > 0) sync.wait(sleep)
        }
      }
    } catch (ignore: InterruptedException) {
      Thread.currentThread().interrupt()
    } finally {
      managerRender.release()
      surfaceManagerPhoto.release()
      surfaceManagerEncoder.release()
      surfaceManager.release()
    }
  }

  override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
    synchronized(sync) {
      frameAvailable = true
      sync.notifyAll()
    }
  }

  fun forceOrientation(forced: OrientationForced) {
    this.orientationForced = forced
  }

  fun attachPreview(surface: Surface) {
    synchronized(sync) {
      if (surfaceManager.isReady) {
        surfaceManagerPreview.release()
        surfaceManagerPreview.eglSetup(surface, surfaceManager)
      }
    }
  }

  fun deAttachPreview() {
    synchronized(sync) {
      surfaceManagerPreview.release()
    }
  }

  override fun setStreamRotation(orientation: Int) {
    this.streamOrientation = orientation
  }

  @JvmOverloads
  fun setPreviewResolution(width: Int, height: Int, isPortrait: Boolean = CameraHelper.isPortrait(context)) {
    this.isPortrait = isPortrait
    this.previewWidth = width
    this.previewHeight = height
  }

  fun setPreviewRotation(orientation: Int) {
    this.previewOrientation = orientation
  }

  fun setCameraOrientation(orientation: Int) {
    managerRender.setCameraRotation(orientation)
  }

  override fun setFilter(filterPosition: Int, baseFilterRender: BaseFilterRender?) {
    filterQueue.add(Filter(FilterAction.SET_INDEX, filterPosition, baseFilterRender))
  }

  override fun addFilter(baseFilterRender: BaseFilterRender?) {
    filterQueue.add(Filter(FilterAction.ADD, 0, baseFilterRender))
  }

  override fun addFilter(filterPosition: Int, baseFilterRender: BaseFilterRender?) {
    filterQueue.add(Filter(FilterAction.ADD_INDEX, filterPosition, baseFilterRender))
  }

  override fun clearFilters() {
    filterQueue.add(Filter(FilterAction.CLEAR, 0, null))
  }

  override fun removeFilter(filterPosition: Int) {
    filterQueue.add(Filter(FilterAction.REMOVE_INDEX, filterPosition, null))
  }

  override fun removeFilter(baseFilterRender: BaseFilterRender?) {
    filterQueue.add(Filter(FilterAction.REMOVE, 0, baseFilterRender))
  }

  override fun filtersCount(): Int {
    return managerRender.filtersCount()
  }

  override fun enableAA(aaEnabled: Boolean) {
    managerRender.enableAA(aaEnabled)
  }

  override fun setRotation(rotation: Int) {
    managerRender.setCameraRotation(rotation);
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

  override fun isAAEnabled(): Boolean = managerRender.isAAEnabled();

  override fun setFilter(baseFilterRender: BaseFilterRender?) {
    filterQueue.add(Filter(FilterAction.SET, 0, baseFilterRender))
  }

  fun setAspectRatioMode(aspectRatioMode: AspectRatioMode) {
    this.aspectRatioMode = aspectRatioMode
  }
}
