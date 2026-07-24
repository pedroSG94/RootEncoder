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
import android.graphics.PointF
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.encoder.R
import com.pedro.encoder.input.gl.Sprite
import com.pedro.encoder.input.gl.TextureLoader
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import com.pedro.encoder.utils.gl.StreamObjectBase
import com.pedro.encoder.utils.gl.TranslateTo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Created by pedro on 03/08/18.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
abstract class BaseObjectFilterRender : BaseFilterRender() {
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
  private var aTextureObjectHandle = -1
  private var uMVPMatrixHandle = -1
  private var uSTMatrixHandle = -1
  private var uSamplerHandle = -1
  private var uObjectHandle = -1
  protected var uAlphaHandle: Int = -1

  protected var fragment: Int = R.raw.object_fragment
  private val squareVertexObject: FloatBuffer

  protected var streamObjectTextureId: IntArray = intArrayOf(-1)
  protected var textureLoader: TextureLoader = TextureLoader()
  protected var streamObject: StreamObjectBase? = null
  val sprite = Sprite()
  var alpha: Float = 1f
  protected var shouldLoad: Boolean = false

  init {
    squareVertex = ByteBuffer.allocateDirect(squareVertexDataFilter.size * FLOAT_SIZE_BYTES)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
    squareVertex.put(squareVertexDataFilter).position(0)
    val vertices = sprite.getTransformedVertices()
    squareVertexObject = ByteBuffer.allocateDirect(vertices.size * FLOAT_SIZE_BYTES)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
    squareVertexObject.put(vertices).position(0)
    Matrix.setIdentityM(MVPMatrix, 0)
    Matrix.setIdentityM(STMatrix, 0)
  }

  override fun initGlFilter(context: Context) {
    val vertexShader = GlUtil.getStringFromRaw(context, R.raw.object_vertex)
    val fragmentShader = GlUtil.getStringFromRaw(context, fragment)

    program = GlUtil.createProgram(vertexShader, fragmentShader)
    aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
    aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
    aTextureObjectHandle = GLES20.glGetAttribLocation(program, "aTextureObjectCoord")
    uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
    uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler")
    uObjectHandle = GLES20.glGetUniformLocation(program, "uObject")
    uAlphaHandle = GLES20.glGetUniformLocation(program, "uAlpha")
  }

  override fun drawFilter() {
    if (sprite.consumeDirty()) {
      squareVertexObject.put(sprite.getTransformedVertices()).position(0)
    }
    if (shouldLoad) {
      releaseTexture()
      streamObject?.bitmaps?.let { streamObjectTextureId = textureLoader.load(it) }
      shouldLoad = false
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

    squareVertexObject.position(SQUARE_VERTEX_DATA_POS_OFFSET)
    GLES20.glVertexAttribPointer(
      aTextureObjectHandle, 2, GLES20.GL_FLOAT, false,
      2 * FLOAT_SIZE_BYTES, squareVertexObject
    )
    GLES20.glEnableVertexAttribArray(aTextureObjectHandle)

    GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0)
    GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0)
    //Sampler
    GLES20.glUniform1i(uSamplerHandle, 0)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId)
    //Object
    GLES20.glUniform1i(uObjectHandle, 1)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
  }

  override fun disableResources() {
    GlUtil.disableResources(aTextureHandle, aTextureObjectHandle, aPositionHandle)
  }

  override fun release() {
    GLES20.glDeleteProgram(program)
    releaseTexture()
    sprite.reset()
  }

  private fun releaseTexture() {
    GLES20.glDeleteTextures(streamObjectTextureId.size, streamObjectTextureId, 0)
    streamObjectTextureId = intArrayOf(-1)
  }

  fun setScale(scaleX: Float, scaleY: Float) {
    sprite.scale(scaleX, scaleY)
  }

  fun setPosition(x: Float, y: Float) {
    sprite.translate(x, y)
  }

  fun setPosition(positionTo: TranslateTo) {
    sprite.translate(positionTo)
  }

  val scale: PointF
    get() = sprite.scale

  val position: PointF
    get() = sprite.translation

  var rotation: Int
    get() = sprite.rotation
    set(angle) {
      sprite.rotation = angle
    }
}
