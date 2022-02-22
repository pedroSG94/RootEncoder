package com.pedro.rtplibrary.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.gl.FilterAction
import com.pedro.encoder.input.gl.render.ManagerRender
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import com.pedro.rtplibrary.R
import com.pedro.rtplibrary.util.Filter

/**
 * Created by pedro on 21/2/22.
 * OpenGlView but supporting rotation and filters only for preview/stream
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class SgOpenGlView: OpenGlViewBase {

  private var managerRender: ManagerRender? = null
  private var loadAA = false

  private var AAEnabled = false
  private var keepAspectRatio = false
  private var aspectRatioMode = AspectRatioMode.Adjust
  private var isFlipHorizontal = false
  private  var isFlipVertical = false

  constructor(context: Context) : super(context)

  constructor(context: Context, attrs: AttributeSet?): super(context, attrs) {
    val typedArray = context.obtainStyledAttributes(attrs, R.styleable.OpenGlView)
    try {
      keepAspectRatio = typedArray.getBoolean(R.styleable.OpenGlView_keepAspectRatio, false)
      aspectRatioMode =
        AspectRatioMode.fromId(typedArray.getInt(R.styleable.OpenGlView_aspectRatioMode, 0))
      AAEnabled = typedArray.getBoolean(R.styleable.OpenGlView_AAEnabled, false)
      ManagerRender.numFilters = typedArray.getInt(R.styleable.OpenGlView_numFilters, 0)
      isFlipHorizontal = typedArray.getBoolean(R.styleable.OpenGlView_isFlipHorizontal, false)
      isFlipVertical = typedArray.getBoolean(R.styleable.OpenGlView_isFlipVertical, false)
    } finally {
      typedArray.recycle()
    }
  }


  override fun init() {
    if (!initialized) managerRender = ManagerRender()
    managerRender?.setCameraFlip(isFlipHorizontal, isFlipVertical)
    initialized = true
  }

  override fun getSurfaceTexture(): SurfaceTexture? {
    return managerRender?.surfaceTexture
  }

  override fun getSurface(): Surface? {
    return managerRender?.surface
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
    return managerRender?.filtersCount() ?: 0
  }

  override fun setFilter(baseFilterRender: BaseFilterRender?) {
    filterQueue.add(Filter(FilterAction.SET, 0, baseFilterRender))
  }

  override fun enableAA(AAEnabled: Boolean) {
    this.AAEnabled = AAEnabled
    loadAA = true
  }

  override fun setRotation(rotation: Int) {
    managerRender?.setCameraRotation(rotation)
  }

  fun isKeepAspectRatio(): Boolean {
    return keepAspectRatio
  }

  fun setAspectRatioMode(aspectRatioMode: AspectRatioMode) {
    this.aspectRatioMode = aspectRatioMode
  }

  fun setKeepAspectRatio(keepAspectRatio: Boolean) {
    this.keepAspectRatio = keepAspectRatio
  }

  fun setCameraFlip(isFlipHorizontal: Boolean, isFlipVertical: Boolean) {
    managerRender?.setCameraFlip(isFlipHorizontal, isFlipVertical)
  }

  override fun isAAEnabled(): Boolean {
    return managerRender != null && managerRender!!.isAAEnabled
  }

  override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    Log.i(TAG, "size: " + width + "x" + height)
    this.previewWidth = width
    this.previewHeight = height
    if (managerRender != null) managerRender!!.setPreviewSize(previewWidth, previewHeight)
  }

  override fun run() {
    surfaceManager.release()
    surfaceManager.eglSetup(holder.surface)
    surfaceManager.makeCurrent()
    managerRender?.initGl(context, encoderWidth, encoderHeight, previewWidth, previewHeight)
    managerRender?.surfaceTexture?.setOnFrameAvailableListener(this)
    surfaceManagerPhoto.release()
    surfaceManagerPhoto.eglSetup(encoderWidth, encoderHeight, surfaceManager)
    semaphore.release()
    try {
      while (running) {
        if (frameAvailable || forceRender) {
          frameAvailable = false
          surfaceManager.makeCurrent()
          managerRender?.updateFrame()
          managerRender?.drawOffScreen()
          managerRender?.drawScreen(previewWidth, previewHeight, keepAspectRatio, aspectRatioMode.id,
            0, true, isPreviewVerticalFlip, isPreviewHorizontalFlip)
          surfaceManager.swapBuffer()
          if (!filterQueue.isEmpty()) {
            val filter: Filter = filterQueue.take()
            managerRender?.setFilterAction(filter.filterAction, filter.position, filter.baseFilterRender)
          } else if (loadAA) {
            managerRender?.enableAA(AAEnabled)
            loadAA = false
          }
          synchronized(sync) {
            if (surfaceManagerEncoder.isReady && !fpsLimiter.limitFPS()) {
              val w = if (muteVideo) 0 else encoderWidth
              val h = if (muteVideo) 0 else encoderHeight
              surfaceManagerEncoder.makeCurrent()
              managerRender?.drawScreen(w, h, false, aspectRatioMode.id, streamRotation,
                false, isStreamVerticalFlip, isStreamHorizontalFlip)
              surfaceManagerEncoder.swapBuffer()
            }
            if (takePhotoCallback != null && surfaceManagerPhoto.isReady) {
              surfaceManagerPhoto.makeCurrent()
              managerRender?.drawScreen(encoderWidth, encoderHeight, false, aspectRatioMode.id,
                streamRotation, false, isStreamVerticalFlip, isStreamHorizontalFlip)
              takePhotoCallback.onTakePhoto(GlUtil.getBitmap(encoderWidth, encoderHeight))
              takePhotoCallback = null
              surfaceManagerPhoto.swapBuffer()
            }
          }
        }
      }
    } catch (ignore: InterruptedException) {
      Thread.currentThread().interrupt()
    } finally {
      managerRender?.release()
      surfaceManagerPhoto.release()
      surfaceManagerEncoder.release()
      surfaceManager.release()
    }
  }
}