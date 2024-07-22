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

package com.pedro.encoder.utils.gl;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 9/09/17.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class GlUtil {

  public static int loadShader(int shaderType, String source) {
    int shader = GLES20.glCreateShader(shaderType);
    checkGlError("glCreateShader type=" + shaderType);
    GLES20.glShaderSource(shader, source);
    GLES20.glCompileShader(shader);
    int[] compiled = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
    if (compiled[0] == GLES20.GL_FALSE) {
      String message = "Could not compile shader " + shaderType + ": " + GLES20.glGetShaderInfoLog(shader);
      GLES20.glDeleteShader(shader);
      throw new RuntimeException(message);
    }
    return shader;
  }

  public static int createProgram(String vertexSource, String fragmentSource) {
    int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
    int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);

    int program = GLES20.glCreateProgram();
    checkGlError("glCreateProgram");
    if (program == 0) throw new RuntimeException("Could not create program");
    GLES20.glAttachShader(program, vertexShader);
    checkGlError("glAttachShader");
    GLES20.glAttachShader(program, pixelShader);
    checkGlError("glAttachShader");
    GLES20.glLinkProgram(program);
    int[] linkStatus = new int[1];
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
    if (linkStatus[0] != GLES20.GL_TRUE) {
      String message = "Could not link program: " + GLES20.glGetProgramInfoLog(program);
      GLES20.glDeleteProgram(program);
      throw new RuntimeException(message);
    }
    return program;
  }

  public static void createTextures(int quantity, int[] texturesId, int offset) {
    GLES20.glGenTextures(quantity, texturesId, offset);
    for (int i = offset; i < quantity; i++) {
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texturesId[i]);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
          GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
          GLES20.GL_CLAMP_TO_EDGE);
    }
  }

  public static void createExternalTextures(int quantity, int[] texturesId, int offset) {
    GLES20.glGenTextures(quantity, texturesId, offset);
    for (int i = offset; i < quantity; i++) {
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texturesId[i]);
      GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
          GLES20.GL_LINEAR);
      GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
          GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
          GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
          GLES20.GL_CLAMP_TO_EDGE);
    }
  }

  public static String getStringFromRaw(Context context, int id) {
    String str;
    try {
      Resources r = context.getResources();
      InputStream is = r.openRawResource(id);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      int i = is.read();
      while (i != -1) {
        baos.write(i);
        i = is.read();
      }
      str = baos.toString();
      is.close();
    } catch (IOException e) {
      throw new RuntimeException("Read shader from disk failed: " + e.getMessage());
    }
    return str;
  }

  public static void checkGlError(String op) {
    int error = GLES20.glGetError();
    if (error != GLES20.GL_NO_ERROR) {
      throw new RuntimeException(op + ". GL error: " + error);
    }
  }

  public static void checkEglError(String msg) {
    int error = EGL14.eglGetError();
    if (error != EGL14.EGL_SUCCESS) {
      throw new RuntimeException(msg + ". EGL error: " + error);
    }
  }

  public static Bitmap getBitmap(int streamWidth, int streamHeight) {
    //Get opengl buffer
    ByteBuffer buffer = ByteBuffer.allocateDirect(streamWidth * streamHeight * 4);
    GLES20.glReadPixels(0, 0, streamWidth, streamHeight, GLES20.GL_RGBA,
        GLES20.GL_UNSIGNED_BYTE, buffer);
    //Create bitmap preview resolution
    Bitmap bitmap = Bitmap.createBitmap(streamWidth, streamHeight, Bitmap.Config.ARGB_8888);
    //Set buffer to bitmap
    bitmap.copyPixelsFromBuffer(buffer);
    //Scale to stream resolution
    //Flip vertical
    return flipVerticalBitmap(bitmap, streamWidth, streamHeight);
  }

  private static Bitmap flipVerticalBitmap(Bitmap bitmap, int width, int height) {
    float cx = width / 2f;
    float cy = height / 2f;
    Matrix matrix = new Matrix();
    matrix.postScale(1f, -1f, cx, cy);
    return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
  }
}
