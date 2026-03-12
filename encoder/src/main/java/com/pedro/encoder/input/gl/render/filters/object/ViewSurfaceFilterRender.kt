/*
 * Copyright (C) 2026 pedroSG94.
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
package com.pedro.encoder.input.gl.render.filters.`object`

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.View
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import com.pedro.encoder.R
import com.pedro.encoder.utils.ViewPresentation
import com.pedro.encoder.utils.gl.GlUtil

/**
 * Created by pedro on 18/07/18.
 */
@RequiresApi(api = Build.VERSION_CODES.KITKAT_WATCH)
class ViewSurfaceFilterRender: BaseObjectFilterRender() {
  private var surfaceTexture: SurfaceTexture? = null
  private var surface: Surface? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var viewPresentation: ViewPresentation? = null
  private var context: Context? = null
  private var view: View? = null
  private var layout: Int? = null

  override fun initGlFilter(context: Context) {
    this.context = context
    fragment = R.raw.surface_fragment
    super.initGlFilter(context)
    GlUtil.createExternalTextures(streamObjectTextureId.size, streamObjectTextureId, 0)
    surfaceTexture = SurfaceTexture(streamObjectTextureId[0])
    surfaceTexture?.setDefaultBufferSize(width, height)
    surface = Surface(surfaceTexture)
    val displayManager: DisplayManager =
      context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    virtualDisplay = displayManager.createVirtualDisplay(
      this.javaClass.name,
      width, height, context.resources.displayMetrics.densityDpi,
      surface,
      DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
    )
    startRender()
  }

  override fun drawFilter() {
    surfaceTexture?.updateTexImage()
    super.drawFilter()
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, streamObjectTextureId[0])
    //Set alpha. 0f if no image loaded.
    GLES20.glUniform1f(uAlphaHandle, if (streamObjectTextureId[0] == -1) 0f else alpha)
  }

  override fun release() {
    super.release()
    viewPresentation?.dismiss()
    viewPresentation = null
    virtualDisplay?.release()
    surfaceTexture?.release()
    surface?.release()
  }

  /**
   * This texture must be renderer using an api called on main thread to avoid possible errors
   */
  fun getSurfaceTexture(): SurfaceTexture? {
    return surfaceTexture
  }

  fun setView(view: View?) {
    if (view == null) return
    layout = null
    this.view = view
    startRender()
  }

  fun setView(@LayoutRes layoutId: Int) {
    if (layoutId == 0) return
    this.view = null
    this.layout = layoutId
    startRender()
  }

  private fun startRender() {
    Handler(Looper.getMainLooper()).post(Runnable {
      if (view == null && layout == 0) return@Runnable
      val context = this.context ?: return@Runnable
      val virtualDisplay = this.virtualDisplay ?: return@Runnable
      viewPresentation?.dismiss()
      viewPresentation = ViewPresentation(context, virtualDisplay.display)
      view?.let { viewPresentation?.setContentView(it) }
      layout?.let { viewPresentation?.setContentView(it) }
      viewPresentation?.show()
    })
  }
}