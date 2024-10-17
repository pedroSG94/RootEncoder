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
package com.pedro.encoder.input.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.RequiresApi
import com.pedro.common.secureGet
import com.pedro.encoder.input.video.Camera2ResolutionCalculator.getOptimalResolution
import com.pedro.encoder.input.video.CameraHelper.Facing
import com.pedro.encoder.input.video.facedetector.FaceDetectorCallback
import com.pedro.encoder.input.video.facedetector.mapCamera2Faces
import java.util.Arrays
import java.util.Collections
import java.util.concurrent.Semaphore
import kotlin.math.abs
import kotlin.math.max

/**
 * Created by pedro on 4/03/17.
 *
 *
 *
 * Class for use surfaceEncoder to buffer encoder.
 * Advantage = you can use all resolutions.
 * Disadvantages = you cant control fps of the stream, because you cant know when the inputSurface
 * was renderer.
 *
 *
 * Note: you can use opengl for surfaceEncoder to buffer encoder on devices 21 < API > 16:
 * https://github.com/google/grafika
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class Camera2ApiManager(context: Context) : CameraDevice.StateCallback() {
    private val TAG = "Camera2ApiManager"

    private var cameraDevice: CameraDevice? = null
    private var surfaceView: SurfaceView? = null
    private var textureView: TextureView? = null
    private var surfaceEncoder: Surface? = null //input surfaceEncoder from videoEncoder
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraHandler: Handler? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    var isPrepared: Boolean = false
        private set
    private var cameraId: String = "0"
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
        fun onImageAvailable(image: Image?)
    }

    private var sensorOrientation = 0
    private var faceSensorScale: Rect? = null
    private var faceDetectorCallback: FaceDetectorCallback? = null
    private var faceDetectionEnabled = false
    private var faceDetectionMode = 0
    private var imageReader: ImageReader? = null

    fun prepareCamera(surfaceView: SurfaceView?, surface: Surface?, fps: Int) {
        this.surfaceView = surfaceView
        this.surfaceEncoder = surface
        this.fps = fps
        isPrepared = true
    }

    fun prepareCamera(textureView: TextureView?, surface: Surface?, fps: Int) {
        this.textureView = textureView
        this.surfaceEncoder = surface
        this.fps = fps
        isPrepared = true
    }

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

    fun prepareCamera(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
        fps: Int,
        cameraId: String
    ) {
        this.facing = getFacingByCameraId(cameraManager, cameraId)
        prepareCamera(surfaceTexture, width, height, fps)
    }

    private fun startPreview(cameraDevice: CameraDevice) {
        try {
            val listSurfaces: MutableList<Surface> = ArrayList()
            val preview = addPreviewSurface()
            if (preview != null) listSurfaces.add(preview)
            if (surfaceEncoder !== preview && surfaceEncoder != null) listSurfaces.add(
                surfaceEncoder!!
            )
            if (imageReader != null) listSurfaces.add(imageReader!!.surface)
            cameraDevice.createCaptureSession(
                listSurfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        this@Camera2ApiManager.cameraCaptureSession = cameraCaptureSession
                        try {
                            val captureRequest = drawSurface(cameraDevice, listSurfaces)
                            if (captureRequest != null) {
                                cameraCaptureSession.setRepeatingRequest(
                                    captureRequest,
                                    if (faceDetectionEnabled) cb else null, cameraHandler
                                )
                                Log.i(TAG, "Camera configured")
                            } else {
                                Log.e(TAG, "Error, captureRequest is null")
                            }
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Error", e)
                        } catch (e: NullPointerException) {
                            Log.e(TAG, "Error", e)
                        } catch (e: IllegalStateException) {
                            reOpenCamera((if (cameraId != null) cameraId else "0")!!)
                        }
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        cameraCaptureSession.close()
                        if (cameraCallbacks != null) cameraCallbacks!!.onCameraError("Configuration failed")
                        Log.e(TAG, "Configuration failed")
                    }
                },
                cameraHandler
            )
        } catch (e: CameraAccessException) {
            if (cameraCallbacks != null) {
                cameraCallbacks!!.onCameraError("Create capture session failed: " + e.message)
            }
            Log.e(TAG, "Error", e)
        } catch (e: IllegalArgumentException) {
            if (cameraCallbacks != null) {
                cameraCallbacks!!.onCameraError("Create capture session failed: " + e.message)
            }
            Log.e(TAG, "Error", e)
        } catch (e: IllegalStateException) {
            reOpenCamera((if (cameraId != null) cameraId else "0")!!)
        }
    }

    private fun addPreviewSurface(): Surface? {
        var surface: Surface? = null
        if (surfaceView != null) {
            surface = surfaceView!!.holder.surface
        } else if (textureView != null) {
            val texture = textureView!!.surfaceTexture
            surface = Surface(texture)
        }
        return surface
    }

    @Throws(IllegalStateException::class, Exception::class)
    private fun drawSurface(cameraDevice: CameraDevice, surfaces: List<Surface>): CaptureRequest {
        val builderInputSurface = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        for (surface in surfaces) builderInputSurface.addTarget(surface)
        builderInputSurface.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        adaptFpsRange(fps, builderInputSurface)
        this.builderInputSurface = builderInputSurface
        return builderInputSurface.build()
    }

    private fun setModeAuto(builderInputSurface: CaptureRequest.Builder) {
        try {
            builderInputSurface.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        } catch (ignored: Exception) {
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
            val characteristics = cameraManager.getCameraCharacteristics(getCameraIdForFacing(facing) ?: return listOf())
            val fpsSupported = characteristics.secureGet(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: return emptyList()
            val streamConfigurationMap = characteristics.secureGet(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return emptyList()
            val fd = streamConfigurationMap.getOutputMinFrameDuration(SurfaceTexture::class.java, size)
            val maxFPS = (10f / "0.$fd".toFloat()).toInt()
            return fpsSupported.filter { it.upper <= maxFPS }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    val levelSupported: Int
        get() {
            val characteristics = cameraCharacteristics ?: return -1
            return characteristics.secureGet(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1
        }

    fun openCamera() {
        openCameraBack()
    }

    fun openCameraBack() {
        openCameraFacing(Facing.BACK)
    }

    fun openCameraFront() {
        openCameraFacing(Facing.FRONT)
    }

    fun openLastCamera() {
        openCameraId(cameraId)
    }

    fun setCameraId(cameraId: String) {
        this.cameraId = cameraId
    }

    fun getCurrentCameraId()  = cameraId

    var cameraFacing: Facing
        get() = facing
        set(cameraFacing) {
            try {
                val cameraId = getCameraIdForFacing(cameraManager, cameraFacing)
                if (cameraId != null) {
                    facing = cameraFacing
                    this.cameraId = cameraId
                }
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Error", e)
            }
        }

    val cameraResolutionsBack: Array<Size>
        get() = getCameraResolutions(Facing.BACK)

    val cameraResolutionsFront: Array<Size>
        get() = getCameraResolutions(Facing.FRONT)

    fun getCameraResolutions(facing: Facing): Array<Size> {
        return getCameraResolutions(getCameraIdForFacing(facing) ?: return arrayOf())
    }

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

    val cameraCharacteristics: CameraCharacteristics?
        get() {
            try {
                return if (cameraId != null) cameraManager.getCameraCharacteristics(cameraId!!) else null
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Error", e)
                return null
            }
        }

    fun enableVideoStabilization(): Boolean {
        val characteristics = cameraCharacteristics ?: return false
        val builderInputSurface = this.builderInputSurface ?: return false
        val modes = characteristics.secureGet(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: return false
        if (!modes.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)) return false
        builderInputSurface.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
        isVideoStabilizationEnabled = true
        return isVideoStabilizationEnabled
    }

    fun disableVideoStabilization() {
        val characteristics = cameraCharacteristics ?: return
        val builderInputSurface = this.builderInputSurface ?: return
        val modes = characteristics.secureGet(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: return
        if (!modes.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)) return
        builderInputSurface.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
        isVideoStabilizationEnabled = false
    }

    fun enableOpticalVideoStabilization(): Boolean {
        val characteristics = cameraCharacteristics ?: return false
        val builderInputSurface = this.builderInputSurface ?: return false
        val opticalStabilizationModes = characteristics.secureGet(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: return false
        if (!opticalStabilizationModes.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)) return false
        builderInputSurface.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
        isOpticalStabilizationEnabled = true
        return isOpticalStabilizationEnabled
    }

    fun disableOpticalVideoStabilization() {
        val characteristics = cameraCharacteristics ?: return
        val builderInputSurface = this.builderInputSurface ?: return
        val modes = characteristics.secureGet(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: return
        if (!modes.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)) return
        builderInputSurface.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF)
        isOpticalStabilizationEnabled = false
    }

    fun setFocusDistance(distance: Float) {
        val builderInputSurface = this.builderInputSurface ?: return
        val cameraCaptureSession = this.cameraCaptureSession ?: return
        try {
            builderInputSurface.set(CaptureRequest.LENS_FOCUS_DISTANCE, max(0f, distance))
            cameraCaptureSession.setRepeatingRequest(
                builderInputSurface.build(),
                if (faceDetectionEnabled) cb else null, null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }
    }

    var exposure: Int
        get() {
            val builderInputSurface = this.builderInputSurface ?: return 0
            return builderInputSurface.secureGet(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) ?: 0
        }
        set(value) {
            val characteristics = cameraCharacteristics ?: return
            val builderInputSurface = this.builderInputSurface ?: return
            val cameraCaptureSession = this.cameraCaptureSession ?: return
            val supportedExposure = characteristics.secureGet(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: return
            val v = value.coerceIn(supportedExposure.lower, supportedExposure.upper)
            try {
                builderInputSurface.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, v)
                cameraCaptureSession.setRepeatingRequest(
                    builderInputSurface.build(),
                    if (faceDetectionEnabled) cb else null, null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error", e)
            }
        }


    val maxExposure: Int
        get() {
            val characteristics = cameraCharacteristics ?: return 0
            val supportedExposure = characteristics.secureGet(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.upper ?: 0
            return supportedExposure
        }

    val minExposure: Int
        get() {
            val characteristics = cameraCharacteristics ?: return 0
            val supportedExposure = characteristics.secureGet(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.lower ?: 0
            return supportedExposure
        }

    fun tapToFocus(event: MotionEvent): Boolean {
        val builderInputSurface = this.builderInputSurface ?: return false
        val cameraCaptureSession = this.cameraCaptureSession ?: return false
        var result = false
        val pointerId = event.getPointerId(0)
        val pointerIndex = event.findPointerIndex(pointerId)
        // Get the pointer's current position
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        if (x < 100 || y < 100) return false

        val touchRect = Rect(
            (x - 100).toInt(), (y - 100).toInt(),
            (x + 100).toInt(), (y + 100).toInt()
        )
        val focusArea = MeteringRectangle(touchRect, MeteringRectangle.METERING_WEIGHT_DONT_CARE)
        try {
            //cancel any existing AF trigger (repeated touches, etc.)
            builderInputSurface.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            cameraCaptureSession.setRepeatingRequest(
                builderInputSurface.build(),
                if (faceDetectionEnabled) cb else null, null
            )
            builderInputSurface.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(focusArea))
            builderInputSurface.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            builderInputSurface.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            cameraCaptureSession.setRepeatingRequest(
                builderInputSurface.build(),
                if (faceDetectionEnabled) cb else null, null
            )
            isAutoFocusEnabled = true
            result = true
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }
        return result
    }

    /**
     * Select camera facing
     *
     * @param selectedCameraFacing - CameraCharacteristics.LENS_FACING_FRONT,
     * CameraCharacteristics.LENS_FACING_BACK,
     * CameraCharacteristics.LENS_FACING_EXTERNAL
     */
    fun openCameraFacing(selectedCameraFacing: Facing) {
        try {
            val cameraId = getCameraIdForFacing(cameraManager, selectedCameraFacing)
            if (cameraId != null) {
                openCameraId(cameraId)
            } else {
                Log.e(
                    TAG,
                    "Camera not supported"
                ) // TODO maybe we want to throw some exception here?
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error", e)
        }
    }

    val isLanternSupported: Boolean
        get() {
            val characteristics = cameraCharacteristics ?: return false
            val available = characteristics.secureGet(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: return false
            return available
        }

    /**
     * @required: <uses-permission android:name="android.permission.FLASHLIGHT"></uses-permission>
     */
    @Throws(Exception::class)
    fun enableLantern() {
        val builderInputSurface = this.builderInputSurface ?: return
        val cameraCaptureSession = this.cameraCaptureSession ?: return
        if (isLanternSupported) {
            try {
                builderInputSurface.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
                cameraCaptureSession.setRepeatingRequest(
                    builderInputSurface.build(),
                    if (faceDetectionEnabled) cb else null, null
                )
                isLanternEnabled = true
            } catch (e: Exception) {
                Log.e(TAG, "Error", e)
            }
        } else {
            Log.e(TAG, "Lantern unsupported")
            throw Exception("Lantern unsupported")
        }
    }

    /**
     * @required: <uses-permission android:name="android.permission.FLASHLIGHT"></uses-permission>
     */
    fun disableLantern() {
        val characteristics = cameraCharacteristics ?: return
        val builderInputSurface = this.builderInputSurface ?: return
        val cameraCaptureSession = this.cameraCaptureSession ?: return
        val available = characteristics.secureGet(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: return
        if (available) {
            try {
                builderInputSurface.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
                cameraCaptureSession.setRepeatingRequest(
                    builderInputSurface.build(),
                    if (faceDetectionEnabled) cb else null, null
                )
                isLanternEnabled = false
            } catch (e: Exception) {
                Log.e(TAG, "Error", e)
            }
        }
    }

    fun enableAutoFocus(): Boolean {
        var result = false
        val characteristics = cameraCharacteristics ?: return false
        val supportedFocusModes = characteristics.secureGet(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: return false
        val builderInputSurface = this.builderInputSurface ?: return false
        val cameraCaptureSession = this.cameraCaptureSession ?: return false

        try {
            if (supportedFocusModes.isNotEmpty()) {
                //cancel any existing AF trigger
                builderInputSurface.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
                builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                cameraCaptureSession.setRepeatingRequest(
                    builderInputSurface.build(),
                    if (faceDetectionEnabled) cb else null, null
                )
                if (supportedFocusModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                    builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    isAutoFocusEnabled = true
                } else if (supportedFocusModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO)) {
                    builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                    isAutoFocusEnabled = true
                } else {
                    builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE, supportedFocusModes[0])
                    isAutoFocusEnabled = false
                }
                cameraCaptureSession.setRepeatingRequest(
                    builderInputSurface.build(),
                    if (faceDetectionEnabled) cb else null, null
                )
            }
            result = isAutoFocusEnabled
        } catch (e: Exception) {
            isAutoFocusEnabled = false
            Log.e(TAG, "Error", e)
        }
        return result
    }

    fun disableAutoFocus(): Boolean {
        val result = false
        val characteristics = cameraCharacteristics ?: return false
        val builderInputSurface = this.builderInputSurface ?: return false
        val cameraCaptureSession = this.cameraCaptureSession ?: return false
        val supportedFocusModes = characteristics.secureGet(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: return false
        for (mode in supportedFocusModes) {
            try {
                if (mode == CaptureRequest.CONTROL_AF_MODE_OFF) {
                    builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                    cameraCaptureSession.setRepeatingRequest(
                        builderInputSurface.build(),
                        if (faceDetectionEnabled) cb else null, null
                    )
                    isAutoFocusEnabled = false
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error", e)
            }
        }
        return result
    }

    fun enableFaceDetection(faceDetectorCallback: FaceDetectorCallback?): Boolean {
        val characteristics = cameraCharacteristics ?: return false
        val builderInputSurface = this.builderInputSurface ?: return false
        faceSensorScale = characteristics.secureGet(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        sensorOrientation = characteristics.secureGet(CameraCharacteristics.SENSOR_ORIENTATION) ?: return false
        val fd = characteristics.secureGet(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES) ?: return false
        val maxFD = characteristics.secureGet(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT) ?: return false
        if (fd.isEmpty() || maxFD <= 0) return false
        this.faceDetectorCallback = faceDetectorCallback
        faceDetectionEnabled = true
        faceDetectionMode = fd.toList().max()
        if (faceDetectionEnabled) {
            builderInputSurface.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, faceDetectionMode)
        }
        prepareFaceDetectionCallback()
        return true
    }

    fun disableFaceDetection() {
        if (faceDetectionEnabled) {
            faceDetectorCallback = null
            faceDetectionEnabled = false
            faceDetectionMode = 0
            prepareFaceDetectionCallback()
        }
    }

    fun isFaceDetectionEnabled() = faceDetectorCallback != null

    private fun setFaceDetect(requestBuilder: CaptureRequest.Builder?, faceDetectMode: Int) {
        if (faceDetectionEnabled) {
            requestBuilder!!.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, faceDetectMode)
        }
    }

    fun setCameraCallbacks(cameraCallbacks: CameraCallbacks?) {
        this.cameraCallbacks = cameraCallbacks
    }

    private fun prepareFaceDetectionCallback() {
        try {
            cameraCaptureSession!!.stopRepeating()
            cameraCaptureSession!!.setRepeatingRequest(
                builderInputSurface!!.build(),
                if (faceDetectionEnabled) cb else null, null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error", e)
        }
    }

    private val cb: CameraCaptureSession.CaptureCallback =
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest, result: TotalCaptureResult
            ) {
                val faces = result.get(CaptureResult.STATISTICS_FACES)
                if (faceDetectorCallback != null && faces != null) {
                    faceDetectorCallback!!.onGetFaces(
                        mapCamera2Faces(faces),
                        faceSensorScale,
                        sensorOrientation
                    )
                }
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
                val facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ?: return
                this.facing =
                    if (CameraMetadata.LENS_FACING_FRONT == facing) Facing.FRONT else Facing.BACK
                if (cameraCallbacks != null) {
                    cameraCallbacks!!.onCameraChanged(this.facing)
                }
            } catch (e: CameraAccessException) {
                if (cameraCallbacks != null) {
                    cameraCallbacks!!.onCameraError("Open camera $cameraId failed")
                }
                Log.e(TAG, "Error", e)
            } catch (e: SecurityException) {
                if (cameraCallbacks != null) {
                    cameraCallbacks!!.onCameraError("Open camera $cameraId failed")
                }
                Log.e(TAG, "Error", e)
            }
        } else {
            Log.e(TAG, "Camera2ApiManager need be prepared, Camera2ApiManager not enabled")
        }
    }

    val camerasAvailable: Array<String>?
        get() = try {
            cameraManager.cameraIdList
        } catch (e: CameraAccessException) {
            null
        }

    fun switchCamera() {
        try {
            var cameraId: String?
            cameraId = if (cameraDevice == null || facing == Facing.FRONT) {
                getCameraIdForFacing(cameraManager, Facing.BACK)
            } else {
                getCameraIdForFacing(cameraManager, Facing.FRONT)
            }
            if (cameraId == null) cameraId = "0"
            reOpenCamera(cameraId)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error", e)
        }
    }

    fun reOpenCamera(cameraId: String) {
        if (cameraDevice != null) {
            closeCamera(false)
            if (textureView != null) {
                prepareCamera(textureView, surfaceEncoder, fps)
            } else if (surfaceView != null) {
                prepareCamera(surfaceView, surfaceEncoder, fps)
            } else {
                prepareCamera(surfaceEncoder, fps)
            }
            openCameraId(cameraId)
        }
    }

    val zoomRange: Range<Float>
        get() {
            val characteristics = cameraCharacteristics
                ?: return Range(1f, 1f)
            var zoomRanges: Range<Float>? = null
            //only camera limited or better support this feature.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                levelSupported != CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
            ) {
                zoomRanges = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            }
            if (zoomRanges == null) {
                var maxZoom =
                    characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                if (maxZoom == null) maxZoom = 1f
                zoomRanges = Range(1f, maxZoom)
            }
            return zoomRanges
        }

    var zoom: Float
        get() = zoomLevel
        set(level) {
            val characteristics = cameraCharacteristics ?: return
            val builderInputSurface = this.builderInputSurface ?: return
            val cameraCaptureSession = this.cameraCaptureSession ?: return
            val l = level.coerceIn(zoomRange.lower, zoomRange.upper)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && levelSupported != CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    builderInputSurface.set(CaptureRequest.CONTROL_ZOOM_RATIO, l)
                } else {
                    val rect = characteristics.secureGet(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
                    //This ratio is the ratio of cropped Rect to Camera's original(Maximum) Rect
                    val ratio = 1f / l
                    //croppedWidth and croppedHeight are the pixels cropped away, not pixels after cropped
                    val croppedWidth = rect.width() - Math.round(rect.width().toFloat() * ratio)
                    val croppedHeight = rect.height() - Math.round(rect.height().toFloat() * ratio)
                    //Finally, zoom represents the zoomed visible area
                    val zoom = Rect(
                        croppedWidth / 2, croppedHeight / 2, rect.width() - croppedWidth / 2,
                        rect.height() - croppedHeight / 2
                    )
                    builderInputSurface.set(CaptureRequest.SCALER_CROP_REGION, zoom)
                }
                cameraCaptureSession.setRepeatingRequest(
                    builderInputSurface.build(),
                    if (faceDetectionEnabled) cb else null, null
                )
                zoomLevel = l
            } catch (e: Exception) {
                Log.e(TAG, "Error", e)
            }
        }

    fun getOpticalZooms(): Array<Float> {
        val characteristics = cameraCharacteristics ?: return arrayOf()
        return characteristics.secureGet(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toTypedArray() ?: arrayOf()
    }

    fun setOpticalZoom(level: Float) {
        val builderInputSurface = this.builderInputSurface ?: return
        val cameraCaptureSession = this.cameraCaptureSession ?: return
        try {
            builderInputSurface.set(CaptureRequest.LENS_FOCAL_LENGTH, level)
            cameraCaptureSession.setRepeatingRequest(
                builderInputSurface.build(),
                if (faceDetectionEnabled) cb else null, null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }
    }

    @JvmOverloads
    fun setZoom(event: MotionEvent, delta: Float = 0.1f) {
        if (event.pointerCount < 2 || event.action != MotionEvent.ACTION_MOVE) return
        val currentFingerSpacing = CameraHelper.getFingerSpacing(event)
        if (currentFingerSpacing > fingerSpacing) {
            zoom += delta
        } else if (currentFingerSpacing < fingerSpacing) {
            zoom -= delta
        }
        fingerSpacing = currentFingerSpacing
    }

    private fun resetCameraValues() {
        isLanternEnabled = false
        zoomLevel = 1.0f
    }

    @JvmOverloads
    fun closeCamera(resetSurface: Boolean = true) {
        resetCameraValues()
        if (cameraCaptureSession != null) {
            cameraCaptureSession!!.close()
            cameraCaptureSession = null
        }
        if (cameraDevice != null) {
            cameraDevice!!.close()
            cameraDevice = null
        }
        if (cameraHandler != null) {
            cameraHandler!!.looper.quitSafely()
            cameraHandler = null
        }
        if (resetSurface) {
            surfaceEncoder = null
            builderInputSurface = null
        }
        isPrepared = false
        isRunning = false
    }

    fun addImageListener(
        width: Int,
        height: Int,
        format: Int,
        maxImages: Int,
        autoClose: Boolean,
        listener: ImageCallback
    ) {
        val wasRunning = isRunning
        closeCamera(false)
        if (wasRunning) closeCamera(false)
        if (imageReader != null) removeImageListener()
        val imageThread = HandlerThread("$TAG imageThread")
        imageThread.start()
        imageReader = ImageReader.newInstance(width, height, format, maxImages)
        imageReader!!.setOnImageAvailableListener({ reader: ImageReader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                listener.onImageAvailable(image)
                if (autoClose) image.close()
            }
        }, Handler(imageThread.looper))
        if (wasRunning) {
            if (textureView != null) {
                prepareCamera(textureView, surfaceEncoder, fps)
            } else if (surfaceView != null) {
                prepareCamera(surfaceView, surfaceEncoder, fps)
            } else {
                prepareCamera(surfaceEncoder, fps)
            }
            openLastCamera()
        }
    }

    fun removeImageListener() {
        val wasRunning = isRunning
        if (wasRunning) closeCamera(false)
        if (imageReader != null) {
            imageReader!!.close()
            imageReader = null
        }
        if (wasRunning) {
            if (textureView != null) {
                prepareCamera(textureView, surfaceEncoder, fps)
            } else if (surfaceView != null) {
                prepareCamera(surfaceView, surfaceEncoder, fps)
            } else {
                prepareCamera(surfaceEncoder, fps)
            }
            openLastCamera()
        }
    }

    override fun onOpened(cameraDevice: CameraDevice) {
        this.cameraDevice = cameraDevice
        startPreview(cameraDevice)
        semaphore.release()
        if (cameraCallbacks != null) cameraCallbacks!!.onCameraOpened()
        Log.i(TAG, "Camera opened")
    }

    override fun onDisconnected(cameraDevice: CameraDevice) {
        cameraDevice.close()
        semaphore.release()
        if (cameraCallbacks != null) cameraCallbacks!!.onCameraDisconnected()
        Log.i(TAG, "Camera disconnected")
    }

    override fun onError(cameraDevice: CameraDevice, i: Int) {
        cameraDevice.close()
        semaphore.release()
        if (cameraCallbacks != null) cameraCallbacks!!.onCameraError("Open camera failed: $i")
        Log.e(TAG, "Open failed: $i")
    }

    @Throws(CameraAccessException::class)
    private fun getCameraIdForFacing(cameraManager: CameraManager, facing: Facing): String? {
        val selectedFacing = getFacing(facing)
        for (cameraId in cameraManager.cameraIdList) {
            val cameraFacing =
                cameraManager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.LENS_FACING)
            if (cameraFacing != null && cameraFacing == selectedFacing) {
                return cameraId
            }
        }
        return null
    }

    private fun getFacingByCameraId(cameraManager: CameraManager, cameraId: String): Facing {
        try {
            for (id in cameraManager.cameraIdList) {
                if (id == cameraId) {
                    val cameraFacing = cameraManager.getCameraCharacteristics(cameraId)
                        .get(CameraCharacteristics.LENS_FACING)
                    return if (cameraFacing == CameraMetadata.LENS_FACING_BACK) Facing.BACK
                    else Facing.FRONT
                }
            }
            return Facing.BACK
        } catch (e: CameraAccessException) {
            return Facing.BACK
        }
    }

    fun getCameraIdForFacing(facing: Facing): String? {
        return try {
            getCameraIdForFacing(cameraManager, facing)
        } catch (e: Exception) {
            null
        }
    }

    @Throws(CameraAccessException::class)
    private fun getCharacteristicsForFacing(
        cameraManager: CameraManager,
        facing: Facing
    ): CameraCharacteristics? {
        val cameraId = getCameraIdForFacing(cameraManager, facing)
        return getCharacteristicsForId(cameraManager, cameraId)
    }

    @Throws(CameraAccessException::class)
    private fun getCharacteristicsForId(
        cameraManager: CameraManager,
        cameraId: String?
    ): CameraCharacteristics? {
        return if (cameraId != null) cameraManager.getCameraCharacteristics(cameraId) else null
    }

    companion object {
        private fun getFacing(facing: Facing): Int {
            return if (facing == Facing.BACK) CameraMetadata.LENS_FACING_BACK
            else CameraMetadata.LENS_FACING_FRONT
        }
    }
}