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
import com.pedro.encoder.utils.gl.GlUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Created by pedro on 4/02/18.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class NoiseFilterRender : BaseFilterRender() {
  //rotation matrix
  private val squareVertexDataFilter = floatArrayOf( // X, Y, Z, U, V
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
  private var uTimeHandle = -1
  private var uStrengthHandle = -1
  private val startTime = System.currentTimeMillis()

  var strength = 16f

  init {
    squareVertex = ByteBuffer.allocateDirect(squareVertexDataFilter.size * FLOAT_SIZE_BYTES)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
    squareVertex.put(squareVertexDataFilter).position(0)
    Matrix.setIdentityM(MVPMatrix, 0)
    Matrix.setIdentityM(STMatrix, 0)
  }

  override fun initGlFilter(context: Context) {
    val vertexShader = GlUtil.getStringFromRaw(context, R.raw.simple_vertex)
    val fragmentShader = GlUtil.getStringFromRaw(context, R.raw.noise_fragment)
    program = GlUtil.createProgram(vertexShader, fragmentShader)
    aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
    aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
    uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
    uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler")
    uTimeHandle = GLES20.glGetUniformLocation(program, "uTime")
    uStrengthHandle = GLES20.glGetUniformLocation(program, "uStrength")
  }

  override fun drawFilter() {
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
    val time = (System.currentTimeMillis() - startTime).toFloat() / 1000f
    GLES20.glUniform1f(uTimeHandle, time)
    GLES20.glUniform1f(uStrengthHandle, strength)
    GLES20.glUniform1i(uSamplerHandle, 4)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE4)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId)
  }

  override fun release() {
    GLES20.glDeleteProgram(program)
  }
}
