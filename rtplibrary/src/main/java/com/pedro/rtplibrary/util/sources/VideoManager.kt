package com.pedro.rtplibrary.util.sources

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.camera2.CameraCharacteristics
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Range
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.video.Camera1ApiManager
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.encoder.input.video.CameraHelper


/**
 * Created by pedro on 21/2/22.
 * A class to use camera1 or camera2 with same methods totally transparent for user.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class VideoManager(private val context: Context, private var source: Source) {

  enum class Source {
    CAMERA1, CAMERA2, SCREEN, DISABLED
  }

  private var facing = CameraHelper.Facing.BACK
  private val camera1 = Camera1ApiManager(null, context)
  private val camera2 = Camera2ApiManager(context)
  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private val noSource = NoSource()

  private var surfaceTexture: SurfaceTexture? = null
  private var width = 0
  private var height = 0
  private var fps = 0

  fun createVideoManager(width: Int, height: Int, fps: Int): Boolean {
    this.width = width
    this.height = height
    this.fps = fps
    return checkResolutionSupported(width, height, source)
  }

  fun changeSourceCamera(source: Source) {
    if (source == Source.SCREEN || source == Source.DISABLED) {
      throw IllegalArgumentException("Invalid ${source.name}. Only ${Source.CAMERA1.name} or ${Source.CAMERA2.name} is accepted.")
    }
    if (this.source != source) {
      if (!checkResolutionSupported(width, height, source)) {
        throw IllegalArgumentException("Resolution ${width}x$height is not supported for ${source.name}.")
      }
      val wasRunning = isRunning()
      stop()
      this.source = source
      mediaProjection?.stop()
      mediaProjection = null
      surfaceTexture?.let {
        if (wasRunning) start(it)
      }
    }
  }

  fun changeSourceScreen(mediaProjection: MediaProjection) {
    if (this.source != Source.SCREEN || this.mediaProjection == null) {
      this.mediaProjection = mediaProjection
      val wasRunning = isRunning()
      stop()
      this.source = Source.SCREEN
      surfaceTexture?.let {
        if (wasRunning) start(it)
      }
    }
  }

  fun changeVideoSourceDisabled() {
    if (this.source != Source.DISABLED) {
      val wasRunning = isRunning()
      stop()
      this.source = Source.DISABLED
      mediaProjection?.stop()
      mediaProjection = null
      surfaceTexture?.let {
        if (wasRunning) start(it)
      }
    }
  }

  fun start(surfaceTexture: SurfaceTexture) {
    this.surfaceTexture = surfaceTexture
    if (!isRunning()) {
      when (source) {
        Source.CAMERA1 -> {
          surfaceTexture.setDefaultBufferSize(width, height)
          camera1.setSurfaceTexture(surfaceTexture)
          camera1.start(facing, width, height, fps)
          camera1.setPreviewOrientation(90) // necessary to use the same orientation than camera2
        }
        Source.CAMERA2 -> {
          surfaceTexture.setDefaultBufferSize(width, height)
          camera2.prepareCamera(surfaceTexture, width, height, fps, facing)
          camera2.openCameraFacing(facing)
        }
        Source.SCREEN -> {
          val dpi = context.resources.displayMetrics.densityDpi
          var flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
          val VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT = 128
          flags += VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT
          //Adapt MediaProjection render to stream resolution
          val shouldRotate = width > height
          val displayWidth = if (shouldRotate) height else width
          val displayHeight = if (shouldRotate) width else height
          if (shouldRotate) {
            surfaceTexture.setDefaultBufferSize(height, width)
          }
          virtualDisplay = mediaProjection?.createVirtualDisplay("VideoManagerScreen",
            displayWidth, displayHeight, dpi, flags,
            Surface(surfaceTexture), null, null)
        }
        Source.DISABLED -> noSource.start()
      }
    }
  }

  fun stop() {
    if (isRunning()) {
      when (source) {
        Source.CAMERA1 -> {
          camera1.stop()
        }
        Source.CAMERA2 -> {
          camera2.closeCamera()
        }
        Source.SCREEN -> {
          virtualDisplay?.release()
          virtualDisplay = null
        }
        Source.DISABLED -> noSource.stop()
      }
    }
  }

  fun setExposure(level: Int) {
    if (isRunning()) {
      when (source) {
        Source.CAMERA1 -> {
          camera1.exposure = level
        }
        Source.CAMERA2 -> {
          camera2.exposure = level
        }
        else -> {}
      }
    }
  }

  fun getExposure(): Int {
    if (isRunning()) {
      return when (source) {
        Source.CAMERA1 -> {
          camera1.exposure
        }
        Source.CAMERA2 -> {
          camera2.exposure
        }
        else -> 0
      }
    }
    return 0
  }

  fun enableLantern() {
    if (isRunning()) {
      when (source) {
        Source.CAMERA1 -> {
          camera1.enableLantern()
        }
        Source.CAMERA2 -> {
          camera2.enableLantern()
        }
        else -> {}
      }
    }
  }

  fun disableLantern() {
    if (isRunning()) {
      when (source) {
        Source.CAMERA1 -> {
          camera1.disableLantern()
        }
        Source.CAMERA2 -> {
          camera2.disableLantern()
        }
        else -> {}
      }
    }
  }

  fun isLanternEnabled(): Boolean {
    if (isRunning()) {
      return when (source) {
        Source.CAMERA1 -> {
          camera1.isLanternEnabled
        }
        Source.CAMERA2 -> {
          camera2.isLanternEnabled
        }
        else -> false
      }
    }
    return false
  }

  fun enableAutoFocus() {
    if (isRunning()) {
      when (source) {
        Source.CAMERA1 -> {
          camera1.enableAutoFocus()
        }
        Source.CAMERA2 -> {
          camera2.enableAutoFocus()
        }
        else -> {}
      }
    }
  }

  fun disableAutoFocus() {
    if (isRunning()) {
      when (source) {
        Source.CAMERA1 -> {
          camera1.disableAutoFocus()
        }
        Source.CAMERA2 -> {
          camera2.disableAutoFocus()
        }
        else -> {}
      }
    }
  }

  fun isAutoFocusEnabled(): Boolean {
    if (isRunning()) {
      return when (source) {
        Source.CAMERA1 -> {
          camera1.isAutoFocusEnabled
        }
        Source.CAMERA2 -> {
          camera2.isAutoFocusEnabled
        }
        else -> false
      }
    }
    return false
  }

  fun setZoom(event: MotionEvent) {
    if (isRunning()) {
      when (source) {
        Source.CAMERA1 -> {
          camera1.setZoom(event)
        }
        Source.CAMERA2 -> {
          camera2.setZoom(event)
        }
        else -> {}
      }
    }
  }

  fun setZoom(level: Float) {
    if (isRunning()) {
      when (source) {
        Source.CAMERA1 -> {
          camera1.zoom = level.toInt()
        }
        Source.CAMERA2 -> {
          camera2.zoom = level
        }
        else -> {}
      }
    }
  }

  fun getZoomRange(): Range<Float> {
    if (isRunning()) {
      return when (source) {
        Source.CAMERA1 -> {
          Range(camera1.minZoom.toFloat(), camera1.maxZoom.toFloat())
        }
        Source.CAMERA2 -> {
          camera2.zoomRange
        }
        else -> Range(0f, 0f)
      }
    }
    return Range(0f, 0f)
  }

  fun getZoom(): Float {
    if (isRunning()) {
      return when (source) {
        Source.CAMERA1 -> {
          camera1.zoom.toFloat()
        }
        Source.CAMERA2 -> {
          camera2.zoom
        }
        else -> 0f
      }
    }
    return 0f
  }

  fun switchCamera() {
    if (source == Source.SCREEN || source == Source.DISABLED) return
    facing = if (facing == CameraHelper.Facing.BACK) {
      CameraHelper.Facing.FRONT
    } else {
      CameraHelper.Facing.BACK
    }
    if (isRunning()) {
      stop()
      surfaceTexture?.let {
        start(it)
      }
    }
  }

  fun getCameraFacing(): CameraHelper.Facing = facing

  fun isRunning(): Boolean {
    return when (source) {
      Source.CAMERA1 -> camera1.isRunning
      Source.CAMERA2 -> camera2.isRunning
      Source.SCREEN -> virtualDisplay != null
      Source.DISABLED -> noSource.isRunning()
    }
  }

  fun getCameraResolutions(source: Source, facing: CameraHelper.Facing): List<Size> {
      return when (source) {
        Source.CAMERA1 -> {
          val resolutions = if (facing == CameraHelper.Facing.FRONT) {
            camera1.previewSizeFront
          } else {
            camera1.previewSizeBack
          }
          mapCamera1Resolutions(resolutions)
        }
        Source.CAMERA2 -> {
          val resolutions = if (facing == CameraHelper.Facing.FRONT) {
            camera2.cameraResolutionsFront
          } else {
            camera2.cameraResolutionsBack
          }
          resolutions.toList()
        }
        else -> emptyList()
      }
  }

  private fun checkResolutionSupported(width: Int, height: Int, source: Source): Boolean {
    if (width % 2 != 0 || height % 2 != 0) {
      throw IllegalArgumentException("width and height values must be divisible by 2")
    }
    when (source) {
      Source.CAMERA1 -> {
        val shouldRotate = width > height
        val w = if (shouldRotate) height else width
        val h = if (shouldRotate) width else height
        val size = Size(w, h)
        val resolutions = if (facing == CameraHelper.Facing.BACK) {
          camera1.previewSizeBack
        } else camera1.previewSizeFront
        return mapCamera1Resolutions(resolutions).contains(size)
      }
      Source.CAMERA2 -> {
        val size = Size(width, height)
        val resolutions = if (facing == CameraHelper.Facing.BACK) {
          camera2.cameraResolutionsBack
        } else camera2.cameraResolutionsFront
        return if (camera2.levelSupported == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
          //this is a wrapper of camera1 api. Only listed resolutions are supported
          resolutions.contains(size)
        } else {
          val widthList = resolutions.map { size.width }
          val heightList = resolutions.map { size.height }
          val maxWidth = widthList.maxOrNull() ?: 0
          val maxHeight = heightList.maxOrNull() ?: 0
          val minWidth = widthList.minOrNull() ?: 0
          val minHeight = heightList.minOrNull() ?: 0
          size.width in minWidth..maxWidth && size.height in minHeight..maxHeight
        }
      }
      Source.SCREEN, Source.DISABLED -> {
        return true
      }
    }
  }

  @Suppress("DEPRECATION")
  private fun mapCamera1Resolutions(resolutions: List<Camera.Size>) = resolutions.map { Size(it.width, it.height) }
}