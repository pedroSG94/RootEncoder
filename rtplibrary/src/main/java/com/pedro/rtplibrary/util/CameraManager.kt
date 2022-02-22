package com.pedro.rtplibrary.util

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.video.Camera1ApiManager
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.encoder.input.video.CameraHelper

/**
 * Created by pedro on 21/2/22.
 * A class to use camera1 or camera2 with same methods totally transparent for user.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraManager(context: Context, surfaceTexture: SurfaceTexture) {

  enum class Source {
    CAMERA1, CAMERA2
  }

  private val source = Source.CAMERA1
  private var facing = CameraHelper.Facing.BACK
  private val camera1 = Camera1ApiManager(surfaceTexture, context)
  private val camera2 = Camera2ApiManager(context)

  private var surfaceTexture: SurfaceTexture ? = null
  private var width = 640
  private var height = 480
  private var fps = 30

  fun start(surfaceTexture: SurfaceTexture, width: Int, height: Int, fps: Int) {
    this.surfaceTexture = surfaceTexture
    this.width = width
    this.height = height
    this.fps = fps

    if (!isRunning()) {
      when (source) {
        Source.CAMERA1 -> {
          camera1.setSurfaceTexture(surfaceTexture)
          camera1.start(facing, width, height, fps)
        }
        Source.CAMERA2 -> {
          camera2.prepareCamera(surfaceTexture, width, height, fps)
          if (facing == CameraHelper.Facing.BACK) {
            camera2.openCameraBack()
          } else {
            camera2.openCameraFront()
          }
        }
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
      }
    }
  }

  fun switchCamera() {
    facing = if (facing == CameraHelper.Facing.BACK) {
      CameraHelper.Facing.FRONT
    } else {
      CameraHelper.Facing.BACK
    }
    stop()
    surfaceTexture?.let {
      start(it, width, height, fps)
    }
  }

  fun isRunning(): Boolean {
    return when (source) {
      Source.CAMERA1 -> camera1.isRunning
      Source.CAMERA2 -> camera2.isRunning
    }
  }
}