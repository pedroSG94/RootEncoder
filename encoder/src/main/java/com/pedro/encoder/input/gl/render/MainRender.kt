package com.pedro.encoder.input.gl.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.Surface
import androidx.annotation.RequiresApi

/**
 * Created by pedro on 20/3/22.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class MainRender {
  private val cameraRender = CameraRender()
  private val screenRender = ScreenRender()
  private var width = 0
  private var height = 0
  private var previewWidth = 0
  private var previewHeight = 0
  private var context: Context? = null

  fun initGl(context: Context, encoderWidth: Int, encoderHeight: Int, previewWidth: Int, previewHeight: Int) {
    this.context = context
    width = encoderWidth
    height = encoderHeight
    this.previewWidth = previewWidth
    this.previewHeight = previewHeight
    cameraRender.initGl(width, height, context, previewWidth, previewHeight)
    screenRender.setStreamSize(encoderWidth, encoderHeight)
    screenRender.setTexId(cameraRender.texId)
    screenRender.initGl(context)
  }

  fun drawOffScreen() {
    cameraRender.draw()
  }

  fun drawScreen(width: Int, height: Int, keepAspectRatio: Boolean, mode: Int, rotation: Int,
    isPreview: Boolean, flipStreamVertical: Boolean, flipStreamHorizontal: Boolean) {
    screenRender.draw(width, height, keepAspectRatio, mode, rotation, isPreview, flipStreamVertical,
      flipStreamHorizontal)
  }

  fun drawScreenEncoder(width: Int, height: Int, isPortrait: Boolean, keepAspectRatio: Boolean,
    mode: Int, rotation: Int, isPreview: Boolean, flipStreamVertical: Boolean,
    flipStreamHorizontal: Boolean) {
    screenRender.drawEncoder(width, height, isPortrait, keepAspectRatio, mode, rotation, isPreview,
      flipStreamVertical, flipStreamHorizontal)
  }

  fun drawScreenPreview(width: Int, height: Int, streamWidth: Int, streamHeight: Int,
    keepAspectRatio: Boolean, mode: Int, rotation: Int, isPreview: Boolean,
    flipStreamVertical: Boolean, flipStreamHorizontal: Boolean) {
    screenRender.drawPreview(width, height, streamWidth, streamHeight, keepAspectRatio, mode, rotation,
      isPreview, flipStreamVertical, flipStreamHorizontal)
  }

  fun release() {
    cameraRender.release()
    screenRender.release()
  }

  fun enableAA(AAEnabled: Boolean) {
    screenRender.isAAEnabled = AAEnabled
  }

  fun isAAEnabled(): Boolean {
    return screenRender.isAAEnabled
  }

  fun updateFrame() {
    cameraRender.updateTexImage()
  }

  fun getSurfaceTexture(): SurfaceTexture {
    return cameraRender.surfaceTexture
  }

  fun getSurface(): Surface {
    return cameraRender.surface
  }

  fun setCameraRotation(rotation: Int) {
    cameraRender.setRotation(rotation)
  }

  fun setCameraFlip(isFlipHorizontal: Boolean, isFlipVertical: Boolean) {
    cameraRender.setFlip(isFlipHorizontal, isFlipVertical)
  }
}
