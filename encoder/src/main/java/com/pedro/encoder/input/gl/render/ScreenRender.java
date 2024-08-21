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

package com.pedro.encoder.input.gl.render;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.R;
import com.pedro.encoder.utils.ViewPort;
import com.pedro.encoder.utils.gl.AspectRatioMode;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.encoder.utils.gl.SizeCalculator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import kotlin.Pair;

/**
 * Created by pedro on 29/01/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class ScreenRender {

  //rotation matrix
  private final float[] squareVertexData = {
      // X, Y, Z, U, V
      -1f, -1f, 0f, 0f, 0f, //bottom left
      1f, -1f, 0f, 1f, 0f, //bottom right
      -1f, 1f, 0f, 0f, 1f, //top left
      1f, 1f, 0f, 1f, 1f, //top right
  };

  private final FloatBuffer squareVertex;

  private final float[] MVPMatrix = new float[16];
  private final float[] STMatrix = new float[16];

  private int texId;

  private int program = -1;
  private int uMVPMatrixHandle = -1;
  private int uSTMatrixHandle = -1;
  private int aPositionHandle = -1;
  private int aTextureHandle = -1;
  private int uSamplerHandle = -1;
  private int streamWidth;
  private int streamHeight;

  public ScreenRender() {
    squareVertex =
        ByteBuffer.allocateDirect(squareVertexData.length * BaseRenderOffScreen.FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
    squareVertex.put(squareVertexData).position(0);
    Matrix.setIdentityM(MVPMatrix, 0);
    Matrix.setIdentityM(STMatrix, 0);
  }

  public void initGl(Context context) {
    GlUtil.checkGlError("initGl start");
    String vertexShader = GlUtil.getStringFromRaw(context, R.raw.simple_vertex);
    String fragmentShader = GlUtil.getStringFromRaw(context, R.raw.simple_fragment);

    program = GlUtil.createProgram(vertexShader, fragmentShader);
    aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
    aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
    uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");
    uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler");
    GlUtil.checkGlError("initGl end");
  }

  public void draw(int width, int height, AspectRatioMode mode, int rotation,
      boolean flipStreamVertical, boolean flipStreamHorizontal) {
    GlUtil.checkGlError("drawScreen start");

    updateMatrix(rotation, SizeCalculator.calculateFlip(flipStreamHorizontal, flipStreamVertical), MVPMatrix);
    ViewPort viewport = SizeCalculator.calculateViewPort(mode, width, height, streamWidth, streamHeight);
    GLES20.glViewport(viewport.getX(), viewport.getY(), viewport.getWidth(), viewport.getHeight());

    draw();
  }

  public void drawEncoder(int width, int height, boolean isPortrait, int rotation,
      boolean flipStreamVertical, boolean flipStreamHorizontal) {
    GlUtil.checkGlError("drawScreen start");

    updateMatrix(rotation, SizeCalculator.calculateFlip(flipStreamHorizontal, flipStreamVertical), MVPMatrix);
    ViewPort viewport = SizeCalculator.calculateViewPortEncoder(width, height, isPortrait);
    GLES20.glViewport(viewport.getX(), viewport.getY(), viewport.getWidth(), viewport.getHeight());

    draw();
  }

  public void drawPreview(int width, int height, boolean isPortrait,
      AspectRatioMode mode, int rotation, boolean flipStreamVertical, boolean flipStreamHorizontal) {
    GlUtil.checkGlError("drawScreen start");

    updateMatrix(rotation, SizeCalculator.calculateFlip(flipStreamHorizontal, flipStreamVertical), MVPMatrix);
    float factor = (float) streamWidth / (float) streamHeight;
    int w;
    int h;
    if (factor >= 1f) {
      w = isPortrait ? streamHeight : streamWidth;
      h = isPortrait ? streamWidth : streamHeight;
    } else {
      w = isPortrait ? streamWidth : streamHeight;
      h = isPortrait ? streamHeight : streamWidth;
    }
    ViewPort viewport = SizeCalculator.calculateViewPort(mode, width, height, w, h);
    GLES20.glViewport(viewport.getX(), viewport.getY(), viewport.getWidth(), viewport.getHeight());

    draw();
  }

  private void draw() {
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

    GLES20.glUseProgram(program);

    squareVertex.position(BaseRenderOffScreen.SQUARE_VERTEX_DATA_POS_OFFSET);
    GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
        BaseRenderOffScreen.SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
    GLES20.glEnableVertexAttribArray(aPositionHandle);

    squareVertex.position(BaseRenderOffScreen.SQUARE_VERTEX_DATA_UV_OFFSET);
    GLES20.glVertexAttribPointer(aTextureHandle, 2, GLES20.GL_FLOAT, false,
        BaseRenderOffScreen.SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
    GLES20.glEnableVertexAttribArray(aTextureHandle);

    GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0);
    GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0);

    GLES20.glUniform1i(uSamplerHandle, 5);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE5);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
    //draw
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

    GlUtil.checkGlError("drawScreen end");
  }

  public void release() {
    GLES20.glDeleteProgram(program);
  }

  public void setTexId(int texId) {
    this.texId = texId;
  }

  public void setStreamSize(int streamWidth, int streamHeight) {
    this.streamWidth = streamWidth;
    this.streamHeight = streamHeight;
  }

  private void updateMatrix(int rotation, Pair<Float, Float> scale, float[] MVPMatrix) {
    Matrix.setIdentityM(MVPMatrix, 0);
    Matrix.scaleM(MVPMatrix, 0, scale.getFirst(), scale.getSecond(), 1f);
    Matrix.rotateM(MVPMatrix, 0, rotation, 0f, 0f, -1f);
  }
}
