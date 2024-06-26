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
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.encoder.R
import com.pedro.encoder.input.gl.render.BaseRenderOffScreen
import com.pedro.encoder.utils.gl.GlUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Created by pedro on 6/3/24.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class CropFilterRender: BaseFilterRender() {

  //rotation matrix
  private val squareVertexDataFilter = floatArrayOf(
    // X, Y, Z, U, V
    -1f, -1f, 0f, 0f, 0f,  //bottom left
    1f, -1f, 0f, 1f, 0f,  //bottom right
    -1f, 1f, 0f, 0f, 1f,  //top left
    1f, 1f, 0f, 1f, 1f
  )

  private var program = -1
  private var aPositionHandle = -1
  private var aTextureHandle = -1
  private var uMVPMatrixHandle = -1
  private var uSTMatrixHandle = -1
  private var uSamplerHandle = -1

  private var positionMatrix = FloatArray(16)

  init {
    squareVertex =
      ByteBuffer.allocateDirect(squareVertexDataFilter.size * BaseRenderOffScreen.FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    squareVertex.put(squareVertexDataFilter).position(0)
    Matrix.setIdentityM(MVPMatrix, 0)
    Matrix.setIdentityM(STMatrix, 0)
    Matrix.setIdentityM(positionMatrix, 0)
  }

  override fun initGlFilter(context: Context?) {
    val vertexShader = GlUtil.getStringFromRaw(context, R.raw.simple_vertex)
    val fragmentShader = GlUtil.getStringFromRaw(context, R.raw.simple_fragment)
    program = GlUtil.createProgram(vertexShader, fragmentShader)
    aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
    aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
    uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
    uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler")
  }

  override fun drawFilter() {
    GLES20.glUseProgram(program)
    squareVertex.position(BaseRenderOffScreen.SQUARE_VERTEX_DATA_POS_OFFSET)
    GLES20.glVertexAttribPointer(
      aPositionHandle, 3, GLES20.GL_FLOAT, false,
      BaseRenderOffScreen.SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex
    )
    GLES20.glEnableVertexAttribArray(aPositionHandle)
    squareVertex.position(BaseRenderOffScreen.SQUARE_VERTEX_DATA_UV_OFFSET)
    GLES20.glVertexAttribPointer(
      aTextureHandle, 2, GLES20.GL_FLOAT, false,
      BaseRenderOffScreen.SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex
    )
    GLES20.glEnableVertexAttribArray(aTextureHandle)
    GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0)
    GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0)
    GLES20.glUniform1i(uSamplerHandle, 4)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE4)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId)
  }

  override fun release() {
    GLES20.glDeleteProgram(program)
  }

  /**
   * Set crop area in percentage. Starting from top left corner.
   * Moved in right bottom
   */
  fun setCropArea(offsetX: Float, offsetY: Float, width: Float, height: Float) {
    val scaleX = 1f / (width / 100f)
    val scaleY = 1f / (height / 100f)

    val initialX = 1f - (1f / scaleX)
    val initialY = -(1f - (1f / scaleY))
    val percentX = initialX / (50f - (width / 2f))
    val percentY = -initialY / (50f - (height / 2f))

    val oX = initialX - (offsetX * percentX)
    val oY = initialY + (offsetY * percentY)

    //reset matrix
    Matrix.setIdentityM(positionMatrix, 0)
    Matrix.setIdentityM(MVPMatrix, 0)

    Matrix.scaleM(positionMatrix, 0, scaleX, scaleY, 0f)
    Matrix.translateM(positionMatrix, 0, oX, oY, 0f)
    Matrix.multiplyMM(MVPMatrix, 0, positionMatrix, 0, MVPMatrix, 0)
  }
}