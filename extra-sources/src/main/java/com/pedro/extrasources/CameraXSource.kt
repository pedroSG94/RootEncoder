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

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Range
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.VideoSource
import com.pedro.encoder.input.video.Camera2ApiManager.ImageCallback
import com.pedro.encoder.input.video.Camera2ResolutionCalculator.getOptimalResolution
import com.pedro.encoder.input.video.CameraHelper
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * Created by pedro on 16/2/24.
 */
class CameraXSource(
  private val context: Context,
): VideoSource(), LifecycleOwner {

  private val lifecycleRegistry = LifecycleRegistry(this)
  private val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
  private val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
  private var camera: Camera? = null
  private var preview = Preview.Builder().build()
  private var facing = CameraSelector.LENS_FACING_BACK
  private var surface: Surface? = null
  private var autoFocusEnabled = false
  private var autoExposureEnabled = false
  private var autoWhiteBalanceEnabled = false
  private var fingerSpacing = 0f
  private var requiredSize: Size? = null

  override val lifecycle: Lifecycle = lifecycleRegistry

  override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
    val facing = if (facing == CameraSelector.LENS_FACING_BACK) CameraHelper.Facing.BACK else CameraHelper.Facing.FRONT
    val optimalResolution = requiredSize ?: getOptimalResolution(Size(width, height), getCameraResolutions(facing).toTypedArray())
    preview = Preview.Builder()
      .setTargetFrameRate(Range(fps, fps))
      .setResolutionSelector(
        ResolutionSelector.Builder()
          .setResolutionStrategy(
            ResolutionStrategy(
              optimalResolution,
              ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            )
          ).build()
      ).build()

    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    return true
  }

  override fun start(surfaceTexture: SurfaceTexture) {
    val facing = if (facing == CameraSelector.LENS_FACING_BACK) CameraHelper.Facing.BACK else CameraHelper.Facing.FRONT
    val optimalResolution = requiredSize ?: getOptimalResolution(Size(width, height), getCameraResolutions(facing).toTypedArray())
    surfaceTexture.setDefaultBufferSize(optimalResolution.width, optimalResolution.height)
    this.surfaceTexture = surfaceTexture
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    cameraProviderFuture.addListener({
      try {
        val cameraSelector = CameraSelector.Builder()
          .requireLensFacing(this.facing)
          .build()

        preview.setSurfaceProvider {
          val surface = Surface(surfaceTexture)
          it.provideSurface(surface, Executors.newSingleThreadExecutor()) {
          }
          this.surface = surface
        }
        camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview)
      } catch (e: ExecutionException) {
        // No errors need to be handled for this Future.
        // This should never be reached.
      } catch (ignored: InterruptedException) { }
    }, ContextCompat.getMainExecutor(context))
  }

  override fun stop() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    camera?.let {
      cameraProvider.unbindAll()
      camera = null
      surface?.release()
      surface = null
    }
  }

  override fun release() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
  }

  override fun isRunning() = camera != null

  fun switchCamera() {
    surfaceTexture?.let {
      stop()
      facing = if (facing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
      start(it)
    }
  }

  fun getCameraFacing() = facing

  fun getCameraResolutions(facing: CameraHelper.Facing): List<Size> {
    val camera2 = Camera2Source(context)
    return camera2.getCameraResolutions(facing)
  }

  fun setExposure(level: Int) {
    camera?.cameraControl?.setExposureCompensationIndex(level)
  }

  fun getExposure(): Int {
    return if (isRunning()) camera?.cameraInfo?.exposureState?.exposureCompensationIndex ?: 0 else 0
  }

  fun enableLantern() {
    if (isRunning()) camera?.cameraControl?.enableTorch(true)
  }

  fun disableLantern() {
    if (isRunning()) camera?.cameraControl?.enableTorch(false)
  }

  fun isLanternEnabled(): Boolean {
    return if (isRunning()) camera?.cameraInfo?.torchState?.value == TorchState.ON else false
  }

  fun enableAutoFocus(): Boolean {
    if (isRunning()) {
      autoFocusEnabled = true
      val point = SurfaceOrientedMeteringPointFactory(1f, 1f)
        .createPoint(.5f, .5f)
      val autoFocusAction = FocusMeteringAction.Builder(point, getMeteringFlag()).build()
      camera?.cameraControl?.startFocusAndMetering(autoFocusAction)
      return true
    }
    autoFocusEnabled = false
    return false
  }

  fun disableAutoFocus(): Boolean {
    if (isRunning()) {
      autoFocusEnabled = false
      val flag = getMeteringFlag()
      if (flag != 0) {
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f)
          .createPoint(.5f, .5f)
        val autoFocusAction = FocusMeteringAction.Builder(point, flag).build()
        camera?.cameraControl?.startFocusAndMetering(autoFocusAction)
      } else camera?.cameraControl?.cancelFocusAndMetering()
      return true
    }
    return false
  }

  fun isAutoFocusEnabled() = autoFocusEnabled

  fun tapToFocus(view: View, event: MotionEvent): Boolean {
    if (isRunning()) {
      autoFocusEnabled = true
      val factory = SurfaceOrientedMeteringPointFactory(view.width.toFloat(), view.height.toFloat())
      val autoFocusPoint = factory.createPoint(event.x, event.y)
      val autoFocusAction = FocusMeteringAction.Builder(
        autoFocusPoint,
        getMeteringFlag()
      ).build()
      camera?.cameraControl?.startFocusAndMetering(autoFocusAction)
      return true
    }
    autoFocusEnabled = false
    return false
  }

  @JvmOverloads
  fun setZoom(event: MotionEvent, delta: Float = 0.1f) {
    if (isRunning()) {
      if (event.pointerCount < 2 || event.action != MotionEvent.ACTION_MOVE) return
      val currentFingerSpacing = CameraHelper.getFingerSpacing(event)
      if (currentFingerSpacing > fingerSpacing) {
        val zoom = getZoom() + delta
        camera?.cameraControl?.setZoomRatio(zoom)
      } else if (currentFingerSpacing < fingerSpacing) {
        val zoom = getZoom() - delta
        camera?.cameraControl?.setZoomRatio(zoom)
      }
      fingerSpacing = currentFingerSpacing

    }
  }

  fun setZoom(level: Float) {
    if (isRunning()) camera?.cameraControl?.setZoomRatio(level)
  }

  fun getZoomRange(): Range<Float> {
    val zoomState = camera?.cameraInfo?.zoomState?.value ?: return Range(0f, 0f)
    return Range(zoomState.minZoomRatio, zoomState.maxZoomRatio)
  }

  fun getZoom(): Float = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 0f

  fun enableAutoExposure(): Boolean {
    return if (isRunning()) {
      autoExposureEnabled = true
      val point = SurfaceOrientedMeteringPointFactory(1f, 1f)
        .createPoint(.5f, .5f)
      val autoFocusAction = FocusMeteringAction.Builder(point, getMeteringFlag()).build()
      camera?.cameraControl?.startFocusAndMetering(autoFocusAction)
      true
    } else {
      autoExposureEnabled = false
      false
    }
  }

  fun disableAutoExposure() {
    if (isRunning()) {
      autoExposureEnabled = false
      val flag = getMeteringFlag()
      if (flag != 0) {
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f)
          .createPoint(.5f, .5f)
        val autoFocusAction = FocusMeteringAction.Builder(point, flag).build()
        camera?.cameraControl?.startFocusAndMetering(autoFocusAction)
      } else camera?.cameraControl?.cancelFocusAndMetering()
    }
  }

  fun isAutoExposureEnabled() = autoExposureEnabled

  /**
   * @param format 1 YUV420, 2 RGBA
   */
  @OptIn(ExperimentalGetImage::class)
  fun addImageListener(width: Int, height: Int, format: Int, autoClose: Boolean, listener: ImageCallback) {
    val facing = if (facing == CameraSelector.LENS_FACING_BACK) CameraHelper.Facing.BACK else CameraHelper.Facing.FRONT
    val optimalResolution = getOptimalResolution(Size(width, height), getCameraResolutions(facing).toTypedArray())
    val imageAnalysis = ImageAnalysis.Builder()
      .setResolutionSelector(
        ResolutionSelector.Builder()
          .setResolutionStrategy(
            ResolutionStrategy(
              optimalResolution,
              ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            )
          ).build())
      .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
      .setOutputImageFormat(format)
      .build()
    val cameraSelector = CameraSelector.Builder()
      .requireLensFacing(this.facing)
      .build()
    cameraProvider.unbindAll()
    camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview)
    imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy ->
      proxy.image?.let {
        listener.onImageAvailable(it)
        if (autoClose) proxy.close()
      }
    }
  }

  fun removeImageListener() {
    cameraProvider.unbindAll()
    val cameraSelector = CameraSelector.Builder()
      .requireLensFacing(this.facing)
      .build()
    camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview)
  }

  fun enableAutoWhiteBalance(): Boolean {
    return if (isRunning()) {
      autoWhiteBalanceEnabled = true
      val point = SurfaceOrientedMeteringPointFactory(1f, 1f)
        .createPoint(.5f, .5f)
      val autoFocusAction = FocusMeteringAction.Builder(point, getMeteringFlag()).build()
      camera?.cameraControl?.startFocusAndMetering(autoFocusAction)
      true
    } else {
      autoWhiteBalanceEnabled = false
      false
    }
  }

  fun disableAutoWhiteBalance() {
    if (isRunning()) {
      autoWhiteBalanceEnabled = false
      val flag = getMeteringFlag()
      if (flag != 0) {
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f)
          .createPoint(.5f, .5f)
        val autoFocusAction = FocusMeteringAction.Builder(point, flag).build()
        camera?.cameraControl?.startFocusAndMetering(autoFocusAction)
      } else camera?.cameraControl?.cancelFocusAndMetering()
    }
  }

  fun isAutoWhiteBalanceEnabled() = autoWhiteBalanceEnabled

  private fun getMeteringFlag(): Int {
    var flags = 0
    if (autoFocusEnabled) flags = flags or FocusMeteringAction.FLAG_AF
    if (autoExposureEnabled) flags = flags or FocusMeteringAction.FLAG_AE
    if (autoWhiteBalanceEnabled) flags = flags or FocusMeteringAction.FLAG_AWB
    return flags
  }

  /**
   * Set the required resolution for the camera.
   * Must be called before prepareVideo or changeVideoSource. Otherwise it will be ignored.
   */
  fun setRequiredResolution(size: Size?) {
    requiredSize = size
  }
}