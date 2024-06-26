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

package com.pedro.encoder.input.gl.render.filters;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.R;
import com.pedro.encoder.utils.gl.GlUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by pedro on 1/02/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class SaturationFilterRender extends BaseFilterRender {

  //rotation matrix
  private final float[] squareVertexDataFilter = {
      // X, Y, Z, U, V
      -1f, -1f, 0f, 0f, 0f, //bottom left
      1f, -1f, 0f, 1f, 0f, //bottom right
      -1f, 1f, 0f, 0f, 1f, //top left
      1f, 1f, 0f, 1f, 1f, //top right
  };

  private int program = -1;
  private int aPositionHandle = -1;
  private int aTextureHandle = -1;
  private int uMVPMatrixHandle = -1;
  private int uSTMatrixHandle = -1;
  private int uSamplerHandle = -1;
  private int uShiftHandle = -1;
  private int uWeightsHandle = -1;
  private int uExponentsHandle = -1;
  private int uSaturationHandle = -1;

  private float saturation = -0.5f;
  private final float shift = 1.0f / 255.0f;
  private final float weights[] = { 2f / 8f, 5f / 8f, 1f / 8f };
  private float exponents[] = new float[3];

  public SaturationFilterRender() {
    squareVertex = ByteBuffer.allocateDirect(squareVertexDataFilter.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    squareVertex.put(squareVertexDataFilter).position(0);
    Matrix.setIdentityM(MVPMatrix, 0);
    Matrix.setIdentityM(STMatrix, 0);
  }

  @Override
  protected void initGlFilter(Context context) {
    String vertexShader = GlUtil.getStringFromRaw(context, R.raw.simple_vertex);
    String fragmentShader = GlUtil.getStringFromRaw(context, R.raw.saturation_fragment);

    program = GlUtil.createProgram(vertexShader, fragmentShader);
    aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
    aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
    uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");
    uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler");
    uShiftHandle = GLES20.glGetUniformLocation(program, "uShift");
    uWeightsHandle = GLES20.glGetUniformLocation(program, "uWeights");
    uExponentsHandle = GLES20.glGetUniformLocation(program, "uExponents");
    uSaturationHandle = GLES20.glGetUniformLocation(program, "uSaturation");
  }

  @Override
  protected void drawFilter() {
    GLES20.glUseProgram(program);

    squareVertex.position(SQUARE_VERTEX_DATA_POS_OFFSET);
    GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
        SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
    GLES20.glEnableVertexAttribArray(aPositionHandle);

    squareVertex.position(SQUARE_VERTEX_DATA_UV_OFFSET);
    GLES20.glVertexAttribPointer(aTextureHandle, 2, GLES20.GL_FLOAT, false,
        SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
    GLES20.glEnableVertexAttribArray(aTextureHandle);

    GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0);
    GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0);
    GLES20.glUniform1f(uShiftHandle, shift);
    GLES20.glUniform3f(uWeightsHandle, weights[0], weights[1], weights[2]);
    GLES20.glUniform3f(uExponentsHandle, exponents[0], exponents[1], exponents[2]);
    GLES20.glUniform1f(uSaturationHandle, saturation);

    GLES20.glUniform1i(uSamplerHandle, 4);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId);
  }

  @Override
  public void release() {
    GLES20.glDeleteProgram(program);
  }

  public float getSaturation() {
    return saturation;
  }

  /**
   * @param saturation between -1.0f and 1.0f means no change, while -1.0f indicates full desaturation,
   * i.e. grayscale.
   */
  public void setSaturation(float saturation) {
    if (saturation > 0.0f) {
      exponents[0] = (0.9f * saturation) + 1.0f;
      exponents[1] = (2.1f * saturation) + 1.0f;
      exponents[2] = (2.7f * saturation) + 1.0f;
      this.saturation = saturation;
    } else {
      this.saturation = saturation + 1.0f;
    }
  }
}
