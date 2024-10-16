/*
 *
 *  * Copyright (C) 2024 pedroSG94.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.pedro.encoder.input.video.test

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresApi
import com.pedro.common.secureGet
import com.pedro.encoder.input.video.Camera2ResolutionCalculator.getOptimalResolution
import com.pedro.encoder.input.video.CameraCallbacks
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.input.video.CameraHelper.Facing
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.encoder.input.video.facedetector.FaceDetectorCallback
import com.pedro.encoder.input.video.facedetector.mapCamera2Faces
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import kotlin.math.abs

/**
 * Created by pedro on 16/10/24.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class Camera2Manager(context: Context): CameraDevice.StateCallback() {
  private val TAG = "Camera2ApiManager"

  private var cameraDevice: CameraDevice? = null
  private var surfaceEncoder: Surface? = null //input surfaceEncoder from videoEncoder
  private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
  private var cameraHandler: Handler? = null
  private var cameraCaptureSession: CameraCaptureSession? = null
  var isPrepared: Boolean = false
    private set
  private var cameraId = "0"
  private var facing = Facing.BACK
  private var builderInputSurface: CaptureRequest.Builder? = null
  private var fingerSpacing = 0f
  private var zoomLevel = 0f
  var isLanternEnabled: Boolean = false
    private set
  var isVideoStabilizationEnabled: Boolean = false
    private set
  var isOpticalStabilizationEnabled: Boolean = false
    private set
  var isAutoFocusEnabled: Boolean = true
    private set
  var isRunning: Boolean = false
    private set
  private var fps = 30
  private val semaphore = Semaphore(0)
  private var cameraCallbacks: CameraCallbacks? = null

  interface ImageCallback {
    fun onImageAvailable(image: Image)
  }

  private var sensorOrientation = 0
  private var faceSensorScale: Rect? = null
  private var faceDetectorCallback: FaceDetectorCallback? = null
  private var faceDetectionEnabled = false
  private var faceDetectionMode = 0
  private var imageReader: ImageReader? = null

  fun prepareCamera(surface: Surface?, fps: Int) {
    this.surfaceEncoder = surface
    this.fps = fps
    isPrepared = true
  }

  fun prepareCamera(surfaceTexture: SurfaceTexture, width: Int, height: Int, fps: Int) {
    val optimalResolution = getOptimalResolution(Size(width, height), getCameraResolutions(facing))
    Log.i(TAG, "optimal resolution set to: " + optimalResolution.width + "x" + optimalResolution.height)
    surfaceTexture.setDefaultBufferSize(optimalResolution.width, optimalResolution.height)
    this.surfaceEncoder = Surface(surfaceTexture)
    this.fps = fps
    isPrepared = true
  }

  fun prepareCamera(surfaceTexture: SurfaceTexture, width: Int, height: Int, fps: Int, facing: Facing) {
    this.facing = facing
    prepareCamera(surfaceTexture, width, height, fps)
  }

  val cameraResolutionsBack: Array<Size>
    get() = getCameraResolutions(Facing.BACK)

  val cameraResolutionsFront: Array<Size>
    get() = getCameraResolutions(Facing.FRONT)

  @JvmOverloads
  fun getCameraIdForFacing(facing: Facing, cameraManager: CameraManager = this.cameraManager): String {
    val selectedFacing = if (facing == Facing.BACK) CameraMetadata.LENS_FACING_BACK else CameraMetadata.LENS_FACING_FRONT
    val ids = cameraManager.cameraIdList
    for (cameraId in ids) {
      val cameraFacing = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING)
      if (cameraFacing != null && cameraFacing == selectedFacing) {
        return cameraId
      }
    }
    if (ids.isEmpty()) throw CameraOpenException("Camera no detected")
    return ids[0]
  }

  fun getCameraResolutions(facing: Facing): Array<Size> = getCameraResolutions(getCameraIdForFacing(facing))

  fun getCameraResolutions(cameraId: String): Array<Size> {
    try {
      val characteristics = cameraManager.getCameraCharacteristics(cameraId)
      val streamConfigurationMap = characteristics.secureGet(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return arrayOf()
      val outputSizes = streamConfigurationMap.getOutputSizes(SurfaceTexture::class.java)
      return outputSizes ?: arrayOf()
    } catch (e: Exception) {
      Log.e(TAG, "Error", e)
      return arrayOf()
    }
  }

  override fun onOpened(cameraDevice: CameraDevice) {
    this.cameraDevice = cameraDevice
    startPreview(cameraDevice)
    semaphore.release()
    cameraCallbacks?.onCameraOpened()
    Log.i(TAG, "Camera opened")
  }

  override fun onDisconnected(cameraDevice: CameraDevice) {
    cameraDevice.close()
    semaphore.release()
    cameraCallbacks?.onCameraDisconnected()
    Log.i(TAG, "Camera disconnected")
  }

  override fun onError(cameraDevice: CameraDevice, i: Int) {
    cameraDevice.close()
    semaphore.release()
    cameraCallbacks?.onCameraError("Open camera failed: $i")
    Log.e(TAG, "Open failed: $i")
  }

  fun reOpenCamera(cameraId: String) {
    if (cameraDevice != null) {
      closeCamera(false)
      prepareCamera(surfaceEncoder, fps)
      openCameraId(cameraId)
    }
  }

  @SuppressLint("MissingPermission")
  fun openCameraId(cameraId: String) {
    this.cameraId = cameraId
    if (isPrepared) {
      val cameraHandlerThread = HandlerThread("$TAG Id = $cameraId")
      cameraHandlerThread.start()
      cameraHandler = Handler(cameraHandlerThread.looper)
      try {
        cameraManager.openCamera(cameraId, this, cameraHandler)
        semaphore.acquireUninterruptibly()
        val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        isRunning = true
        val facing = cameraCharacteristics.secureGet(CameraCharacteristics.LENS_FACING) ?: return
        this.facing = if (CameraMetadata.LENS_FACING_FRONT == facing) Facing.FRONT else Facing.BACK
        cameraCallbacks?.onCameraChanged(this.facing)
      } catch (e: Exception) {
        cameraCallbacks?.onCameraError("Open camera $cameraId failed")
        Log.e(TAG, "Error", e)
      }
    } else {
      Log.e(TAG, "Camera2ApiManager need be prepared, Camera2ApiManager not enabled")
    }
  }

  @JvmOverloads
  fun closeCamera(resetSurface: Boolean = true) {
    isLanternEnabled = false
    zoomLevel = 1.0f
    cameraCaptureSession?.close()
    cameraCaptureSession = null
    cameraDevice?.close()
    cameraDevice = null
    cameraHandler?.looper?.quitSafely()
    cameraHandler = null
    if (resetSurface) {
      surfaceEncoder = null
      builderInputSurface = null
    }
    isPrepared = false
    isRunning = false
  }

  @Throws(IllegalStateException::class, Exception::class)
  private fun drawSurface(cameraDevice: CameraDevice, surfaces: List<Surface>): CaptureRequest {
    val builderInputSurface = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
    for (surface in surfaces) builderInputSurface.addTarget(surface)
    setModeAuto(builderInputSurface)
    adaptFpsRange(fps, builderInputSurface)
    this.builderInputSurface = builderInputSurface
    return builderInputSurface.build()
  }

  private fun setModeAuto(builderInputSurface: CaptureRequest.Builder) {
    try {
      builderInputSurface.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    } catch (ignored: Exception) {}
  }

  fun openCameraFacing(selectedCameraFacing: Facing) {
    try {
      val cameraId = getCameraIdForFacing(selectedCameraFacing)
      openCameraId(cameraId)
    } catch (e: Exception) {
      Log.e(TAG, "Error", e)
    }
  }

  val levelSupported: Int
    get() {
      val characteristics = cameraCharacteristics ?: return -1
      return characteristics.secureGet(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1
    }

  val cameraCharacteristics: CameraCharacteristics?
    get() {
      try {
        return cameraManager.getCameraCharacteristics(cameraId)
      } catch (e: Exception) {
        Log.e(TAG, "Error", e)
        return null
      }
    }

  private fun adaptFpsRange(expectedFps: Int, builderInputSurface: CaptureRequest.Builder) {
    val fpsRanges = getSupportedFps(null, Facing.BACK)
    if (fpsRanges.isNotEmpty()) {
      var closestRange = fpsRanges[0]
      var measure = (abs((closestRange.lower - expectedFps).toDouble()) + abs(
        (closestRange.upper - expectedFps).toDouble()
      )).toInt()
      for (range in fpsRanges) {
        if (CameraHelper.discardCamera2Fps(range, facing)) continue
        if (range.lower <= expectedFps && range.upper >= expectedFps) {
          val curMeasure = abs((((range.lower + range.upper) / 2) - expectedFps).toDouble()).toInt()
          if (curMeasure < measure) {
            closestRange = range
            measure = curMeasure
          } else if (curMeasure == measure) {
            if (abs((range.upper - expectedFps).toDouble()) < abs((closestRange.upper - expectedFps).toDouble())) {
              closestRange = range
              measure = curMeasure
            }
          }
        }
      }
      Log.i(TAG, "fps: " + closestRange.lower + " - " + closestRange.upper)
      builderInputSurface.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, closestRange)
    }
  }

  fun getSupportedFps(size: Size?, facing: Facing): List<Range<Int>> {
    try {
      val characteristics = cameraManager.getCameraCharacteristics(getCameraIdForFacing(facing))
      val fpsSupported = characteristics.secureGet(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: return emptyList()
      val streamConfigurationMap = characteristics.secureGet(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return emptyList()
      val fd = streamConfigurationMap.getOutputMinFrameDuration(SurfaceTexture::class.java, size)
      val maxFPS = (10f / "0.$fd".toFloat()).toInt()
      return fpsSupported.filter { it.upper <= maxFPS }
    } catch (e: Exception) {
      return emptyList()
    }
  }

  private val cb: CameraCaptureSession.CaptureCallback = object : CameraCaptureSession.CaptureCallback() {
    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
      val faces = result.get(CaptureResult.STATISTICS_FACES) ?: return
      faceDetectorCallback?.onGetFaces(mapCamera2Faces(faces), faceSensorScale, sensorOrientation)
    }
  }

  private fun startPreview(cameraDevice: CameraDevice) {
    try {
      val listSurfaces = mutableListOf<Surface>()
      surfaceEncoder?.let { listSurfaces.add(it) }
      imageReader?.let { listSurfaces.add(it.surface) }
      createCaptureSession(
        cameraDevice,
        listSurfaces,
        onConfigured = {
          cameraCaptureSession = it
          try {
            val captureRequest = drawSurface(cameraDevice, listSurfaces)
            it.setRepeatingRequest(
              captureRequest,
              if (faceDetectionEnabled) cb else null,
              cameraHandler
            )
          } catch (e: IllegalStateException) {
            reOpenCamera(cameraId)
          } catch (e: Exception) {
            cameraCallbacks?.onCameraError("Create capture session failed: " + e.message)
            Log.e(TAG, "Error", e)
          }
        },
        onConfiguredFailed = {
          it.close()
          cameraCallbacks?.onCameraError("Configuration failed")
          Log.e(TAG, "Configuration failed")
        },
        cameraHandler
      )
    } catch (e: IllegalStateException) {
      reOpenCamera(cameraId)
    } catch (e: Exception) {
      cameraCallbacks?.onCameraError("Create capture session failed: " + e.message)
      Log.e(TAG, "Error", e)
    }
  }

  @Suppress("DEPRECATION")
  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  private fun createCaptureSession(
    cameraDevice: CameraDevice,
    surfaces: List<Surface>,
    onConfigured: (CameraCaptureSession) -> Unit,
    onConfiguredFailed: (CameraCaptureSession) -> Unit,
    handler: Handler?
  ) {
    val callback = object: CameraCaptureSession.StateCallback() {
      override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
        onConfigured(cameraCaptureSession)
      }

      override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
        onConfiguredFailed(cameraCaptureSession)
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val config = SessionConfiguration(
        SessionConfiguration.SESSION_REGULAR,
        surfaces.map { OutputConfiguration(it) },
        Executors.newSingleThreadExecutor(),
        callback
      )
      cameraDevice.createCaptureSession(config)
    } else {
      cameraDevice.createCaptureSession(surfaces, callback, handler)
    }
  }
}