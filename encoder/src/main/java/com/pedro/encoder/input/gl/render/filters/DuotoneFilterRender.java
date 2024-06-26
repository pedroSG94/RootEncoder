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
import android.content.res.Resources;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.R;
import com.pedro.encoder.utils.gl.GlUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by pedro on 1/02/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class DuotoneFilterRender extends BaseFilterRender {

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
  private int uColorHandle = -1;
  private int uColor2Handle = -1;

  private static final String HEX_PATTERN = "^#([A-Fa-f0-9]{6})$";

  //by default tint with green and blue
  private float red = 0f;
  private float green = 1f;
  private float blue = 0f;
  private float red2 = 0f;
  private float green2 = 0f;
  private float blue2 = 1f;

  public DuotoneFilterRender() {
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
    String fragmentShader = GlUtil.getStringFromRaw(context, R.raw.duotone_fragment);

    program = GlUtil.createProgram(vertexShader, fragmentShader);
    aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
    aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
    uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");
    uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler");
    uColorHandle = GLES20.glGetUniformLocation(program, "uColor");
    uColor2Handle = GLES20.glGetUniformLocation(program, "uColor2");
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
    GLES20.glUniform3f(uColorHandle, red, green, blue);
    GLES20.glUniform3f(uColor2Handle, red2, green2, blue2);

    GLES20.glUniform1i(uSamplerHandle, 4);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId);
  }

  @Override
  public void release() {
    GLES20.glDeleteProgram(program);
  }

  public float getRed() {
    return red;
  }

  public float getGreen() {
    return green;
  }

  public float getBlue() {
    return blue;
  }

  public float getRed2() {
    return red2;
  }

  public float getGreen2() {
    return green2;
  }

  public float getBlue2() {
    return blue2;
  }

  /**
   * @param rgbHexColor color represented with 7 characters (1 to start with #, 2 for red, 2 for
   * green and 2 for blue)
   */
  public void setRGBColor(String rgbHexColor, String rgbHexColor2) {
    Pattern pattern = Pattern.compile(HEX_PATTERN);
    Matcher matcher = pattern.matcher(rgbHexColor);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Invalid hexColor pattern (Should be: " + HEX_PATTERN + ")");
    }
    int r = Integer.valueOf(rgbHexColor.substring(1, 3), 16);
    int g = Integer.valueOf(rgbHexColor.substring(3, 5), 16);
    int b = Integer.valueOf(rgbHexColor.substring(5, 7), 16);
    red = (float) r / 255.0f;
    green = (float) g / 255.0f;
    blue = (float) b / 255.0f;

    matcher = pattern.matcher(rgbHexColor2);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Invalid hexColor pattern (Should be: " + HEX_PATTERN + ")");
    }
    r = Integer.valueOf(rgbHexColor2.substring(1, 3), 16);
    g = Integer.valueOf(rgbHexColor2.substring(3, 5), 16);
    b = Integer.valueOf(rgbHexColor2.substring(5, 7), 16);
    red2 = (float) r / 255.0f;
    green2 = (float) g / 255.0f;
    blue2 = (float) b / 255.0f;
  }

  /**
   * Values range 0 to 255
   */
  public void setRGBColor(int r, int g, int b, int r2, int g2, int b2) {
    red = (float) r / 255.0f;
    green = (float) g / 255.0f;
    blue = (float) b / 255.0f;

    red2 = (float) r2 / 255.0f;
    green2 = (float) g2 / 255.0f;
    blue2 = (float) b2 / 255.0f;
  }

  /**
   * Get string color from color file resource and strip alpha values (alpha values is always auto
   * completed)
   */
  public void setColor(Resources resources, int colorResource, int colorResource2) {
    String color = resources.getString(colorResource);
    String color2 = resources.getString(colorResource2);
    setRGBColor("#" + color.substring(3), "#" + color2.substring(3));
  }

  /**
   * @param colorResource int from color class with Color.parse or Color.NAME_COLOR (Ex:
   * Color.BLUE)
   * @param colorResource2 int from color class with Color.parse or Color.NAME_COLOR (Ex:
   * Color.BLUE)
   */
  public void setColor(int colorResource, int colorResource2) {
    red = Color.red(colorResource) / 255f;
    green = Color.green(colorResource) / 255f;
    blue = Color.blue(colorResource) / 255f;

    red2 = Color.red(colorResource2) / 255f;
    green2 = Color.green(colorResource2) / 255f;
    blue2 = Color.blue(colorResource2) / 255f;
  }
}
