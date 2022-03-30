package com.pedro.rtplibrary.util.sources

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Build
import android.util.DisplayMetrics
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.video.Camera1ApiManager
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.encoder.input.video.CameraHelper


/**
 * Created by pedro on 21/2/22.
 * A class to use camera1 or camera2 with same methods totally transparent for user.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class VideoManager(private val context: Context) {

  enum class Source {
    CAMERA1, CAMERA2, SCREEN, DISABLED
  }

  private var source = Source.CAMERA2
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
    if (this.source != Source.SCREEN) {
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
          camera1.setSurfaceTexture(surfaceTexture)
          camera1.start(facing, width, height, fps)
          camera1.setPreviewOrientation(90) // necessary to use the same orientation than camera2
        }
        Source.CAMERA2 -> {
          camera2.prepareCamera(surfaceTexture, width, height, fps)
          camera2.openCameraFacing(facing)
        }
        Source.SCREEN -> {
          val screenHelper = ScreenHelper(context)
          val resolution = screenHelper.calculateMediaProjectionResolution(width, height)
          val dpi = context.resources.displayMetrics.densityDpi
          var flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
          val VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT = 128
          flags += VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT
          virtualDisplay = mediaProjection?.createVirtualDisplay("VideoManagerScreen",
            resolution.width, resolution.height, screenHelper.getScreenDpi(), flags,
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

  private fun checkResolutionSupported(width: Int, height: Int, source: Source): Boolean {
    if (width % 2 != 0 || height % 2 != 0) {
      throw IllegalArgumentException("width and height values must be divisible by 2")
    }
    when (source) {
      Source.CAMERA1 -> {
        val size = camera1.getCameraSize(width, height)
        val resultBack = camera1.previewSizeBack.contains(size)
        val resultFront = camera1.previewSizeFront.contains(size)
        return resultBack && resultFront
      }
      Source.CAMERA2 -> {
        val size = Size(width, height)
        val widthList = camera2.cameraResolutionsBack.map { size.width }
        val heightList = camera2.cameraResolutionsBack.map { size.height }
        val maxWidth = widthList.maxOrNull() ?: 0
        val maxHeight = heightList.maxOrNull() ?: 0
        val minWidth = widthList.minOrNull() ?: 0
        val minHeight = heightList.minOrNull() ?: 0
        return size.width in minWidth..maxWidth && size.height in minHeight..maxHeight
      }
      Source.SCREEN, Source.DISABLED -> {
        return true
      }
    }
  }
}