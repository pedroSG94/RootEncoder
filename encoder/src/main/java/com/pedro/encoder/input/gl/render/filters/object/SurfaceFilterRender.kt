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
package com.pedro.encoder.input.gl.render.filters.`object`

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.annotation.RequiresApi
import com.pedro.encoder.R
import com.pedro.encoder.utils.gl.GlUtil

/**
 * Created by pedro on 18/07/18.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
open class SurfaceFilterRender @JvmOverloads constructor(private val surfaceReadyCallback: SurfaceReadyCallback? = null) : BaseObjectFilterRender() {
  fun interface SurfaceReadyCallback {
    fun surfaceReady(surfaceTexture: SurfaceTexture)
  }

  /**
   * This texture must be renderer using an api called on main thread to avoid possible errors
   */
  var surfaceTexture: SurfaceTexture? = null
    private set

  /**
   * This surface must be renderer using an api called on main thread to avoid possible errors
   */
  var surface: Surface? = null
    private set

  override fun initGlFilter(context: Context) {
    fragment = R.raw.surface_fragment
    super.initGlFilter(context)
    GlUtil.createExternalTextures(streamObjectTextureId.size, streamObjectTextureId, 0)
    val surfaceTexture = SurfaceTexture(streamObjectTextureId[0])
    surfaceTexture.setDefaultBufferSize(width, height)
    this.surfaceTexture = surfaceTexture
    surface = Surface(surfaceTexture)
    surfaceReadyCallback?.let {
      Handler(Looper.getMainLooper()).post {
        surfaceReadyCallback.surfaceReady(surfaceTexture)
      }
    }
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
    surfaceTexture?.release()
    surface?.release()
  }
}