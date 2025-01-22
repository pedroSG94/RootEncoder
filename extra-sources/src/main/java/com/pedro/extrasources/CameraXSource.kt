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
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.VideoSource
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

  override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
    val facing = if (facing == CameraSelector.LENS_FACING_BACK) CameraHelper.Facing.BACK else CameraHelper.Facing.FRONT
    val optimalResolution = getOptimalResolution(Size(width, height), getCameraResolutions(facing).toTypedArray())
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
    val optimalResolution = getOptimalResolution(Size(width, height), getCameraResolutions(facing).toTypedArray())
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

  override fun isRunning(): Boolean {
    return camera != null
  }

  fun switchCamera() {
    surfaceTexture?.let {
      stop()
      facing = if (facing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
      start(it)
    }
  }

  fun getCameraResolutions(facing: CameraHelper.Facing): List<Size> {
    val camera2 = Camera2Source(context)
    return camera2.getCameraResolutions(facing)
  }

  override val lifecycle: Lifecycle = lifecycleRegistry
}