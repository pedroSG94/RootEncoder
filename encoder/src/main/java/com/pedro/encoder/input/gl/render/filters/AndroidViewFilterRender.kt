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
package com.pedro.encoder.input.gl.render.filters

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Build
import android.view.Surface
import android.view.View
import androidx.annotation.RequiresApi
import com.pedro.common.TimeUtils
import com.pedro.encoder.R
import com.pedro.encoder.input.gl.AndroidViewSprite
import com.pedro.encoder.utils.gl.GlUtil
import com.pedro.encoder.utils.gl.TranslateTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * Created by pedro on 4/02/18.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class AndroidViewFilterRender : BaseFilterRender() {
  //rotation matrix
  private val squareVertexDataFilter = floatArrayOf(
    // X, Y, Z, U, V
    -1f, -1f, 0f, 0f, 0f,  //bottom left
    1f, -1f, 0f, 1f, 0f,  //bottom right
    -1f, 1f, 0f, 0f, 1f,  //top left
    1f, 1f, 0f, 1f, 1f,  //top right
  )

  private var program = -1
  private var aPositionHandle = -1
  private var aTextureHandle = -1
  private var uMVPMatrixHandle = -1
  private var uSTMatrixHandle = -1
  private var uSamplerHandle = -1
  private var uSamplerViewHandle = -1

  private var viewId = intArrayOf(-1)
  var view: View? = null
    set(value) {
      stopRender()
      field = value
      value?.let {
        it.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        sprite.setView(it)
        startRender()
      }
    }
  private var surfaceTexture: SurfaceTexture? = null
  private var surface: Surface? = null
  private var job: Job? = null

  /**
   * Draw in surface using hardware canvas. True by default.
   */
  var isHardwareMode: Boolean = true
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && field
  private val sprite: AndroidViewSprite
  private val frameAvailable = AtomicBoolean(false)
  @Volatile
  var targetFps = 30
    set(value) {
      if (value <= 0) throw IllegalArgumentException("The targetFps must be at least 1")
      field = value
    }

  init {
    squareVertex = ByteBuffer.allocateDirect(squareVertexDataFilter.size * FLOAT_SIZE_BYTES)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
    squareVertex.put(squareVertexDataFilter).position(0)
    Matrix.setIdentityM(MVPMatrix, 0)
    Matrix.setIdentityM(STMatrix, 0)
    sprite = AndroidViewSprite()
  }

  override fun initGlFilter(context: Context) {
    val vertexShader = GlUtil.getStringFromRaw(context, R.raw.simple_vertex)
    val fragmentShader = GlUtil.getStringFromRaw(context, R.raw.android_view_fragment)

    program = GlUtil.createProgram(vertexShader, fragmentShader)
    aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
    aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
    uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
    uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler")
    uSamplerViewHandle = GLES20.glGetUniformLocation(program, "uSamplerView")

    GlUtil.createExternalTextures(viewId.size, viewId, 0)
    surfaceTexture = SurfaceTexture(viewId[0])
    surfaceTexture?.setOnFrameAvailableListener { frameAvailable.set(true) }
    surface = Surface(surfaceTexture)
  }

  override fun drawFilter() {
    surfaceTexture?.let {
      it.setDefaultBufferSize(previewWidth, previewHeight)
      if (frameAvailable.getAndSet(false)) it.updateTexImage()
    }
    GLES20.glUseProgram(program)

    squareVertex.position(SQUARE_VERTEX_DATA_POS_OFFSET)
    GLES20.glVertexAttribPointer(
      aPositionHandle, 3, GLES20.GL_FLOAT, false,
      SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex
    )
    GLES20.glEnableVertexAttribArray(aPositionHandle)

    squareVertex.position(SQUARE_VERTEX_DATA_UV_OFFSET)
    GLES20.glVertexAttribPointer(
      aTextureHandle, 2, GLES20.GL_FLOAT, false,
      SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex
    )
    GLES20.glEnableVertexAttribArray(aTextureHandle)

    GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0)
    GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0)

    GLES20.glUniform1i(uSamplerHandle, 0)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId)
    //android view
    GLES20.glUniform1i(uSamplerViewHandle, 1)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, viewId[0])
  }

  override fun disableResources() {
    GlUtil.disableResources(aTextureHandle, aPositionHandle)
  }

  override fun release() {
    stopRender()
    GLES20.glDeleteProgram(program)
    viewId = intArrayOf(-1)
    surface?.release()
    surfaceTexture?.release()
  }

  /**
   * @param x Position in percent
   * @param y Position in percent
   */
  fun setPosition(x: Float, y: Float) {
    sprite.translate(x, y)
  }

  fun setPosition(positionTo: TranslateTo) {
    sprite.translate(positionTo)
  }

  fun setScale(scaleX: Float, scaleY: Float) {
    sprite.scale(scaleX, scaleY)
  }

  val scale: PointF
    get() = sprite.scale

  val position: PointF
    get() = sprite.translation

  var rotation: Int
    get() = sprite.rotation
    set(rotation) {
      sprite.setRotation(rotation)
    }

  private fun startRender() {
    job = CoroutineScope(Dispatchers.IO).launch {
      while (isActive) {
        val sleepRate = 1000 / targetFps
        val startTimestamp = TimeUtils.getCurrentTimeMillis()

        val surface = this@AndroidViewFilterRender.surface
        val view = this@AndroidViewFilterRender.view
        if (surface == null || view == null) {
          val sleep = sleepRate - (TimeUtils.getCurrentTimeMillis() - startTimestamp)
          delay(sleep.milliseconds)
          continue
        }

        val canvas: Canvas
        try {
          canvas = if (isHardwareMode) surface.lockHardwareCanvas() else surface.lockCanvas(null)
        } catch (_: Exception) {
          val sleep = sleepRate - (TimeUtils.getCurrentTimeMillis() - startTimestamp)
          delay(sleep.milliseconds)
          continue
        }

        sprite.calculateDefaultScale(previewWidth.toFloat(), previewHeight.toFloat())
        val canvasPosition = sprite.getCanvasPosition(previewWidth.toFloat(), previewHeight.toFloat())
        val canvasScale = sprite.getCanvasScale(previewWidth.toFloat(), previewHeight.toFloat())
        val rotationAxis = sprite.rotationAxis
        val rotation = sprite.rotation

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.translate(canvasPosition.x, canvasPosition.y)
        canvas.scale(canvasScale.x, canvasScale.y)
        canvas.rotate(rotation.toFloat(), rotationAxis.x, rotationAxis.y)

        try {
          view.draw(canvas)
        } catch (_: Exception) {
          withContext(Dispatchers.Main) {
            runCatching { view.draw(canvas) }
          }
        } finally {
          runCatching { surface.unlockCanvasAndPost(canvas) }
          val sleep = sleepRate - (TimeUtils.getCurrentTimeMillis() - startTimestamp)
          delay(sleep.milliseconds)
        }
      }
    }
  }

  private fun stopRender() {
    job?.cancel()
    job = null
    frameAvailable.set(false)
  }
}
