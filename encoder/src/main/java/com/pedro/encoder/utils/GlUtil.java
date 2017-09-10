package com.pedro.encoder.utils;

import android.content.Context;
import android.content.res.Resources;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by pedro on 9/09/17.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class GlUtil {

  private static final String TAG = "GlUtil";

  public static int loadShader(int shaderType, String source) {
    int shader = GLES20.glCreateShader(shaderType);
    checkGlError("glCreateShader type=" + shaderType);
    GLES20.glShaderSource(shader, source);
    GLES20.glCompileShader(shader);
    int[] compiled = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
    if (compiled[0] == 0) {
      Log.e(TAG, "Could not compile shader " + shaderType + ":");
      Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
      GLES20.glDeleteShader(shader);
      shader = 0;
    }
    return shader;
  }

  public static int createProgram(String vertexSource, String fragmentSource) {
    int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
    if (vertexShader == 0) {
      return 0;
    }
    int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
    if (pixelShader == 0) {
      return 0;
    }

    int program = GLES20.glCreateProgram();
    checkGlError("glCreateProgram");
    if (program == 0) {
      Log.e(TAG, "Could not create program");
    }
    GLES20.glAttachShader(program, vertexShader);
    checkGlError("glAttachShader");
    GLES20.glAttachShader(program, pixelShader);
    checkGlError("glAttachShader");
    GLES20.glLinkProgram(program);
    int[] linkStatus = new int[1];
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
    if (linkStatus[0] != GLES20.GL_TRUE) {
      Log.e(TAG, "Could not link program: ");
      Log.e(TAG, GLES20.glGetProgramInfoLog(program));
      GLES20.glDeleteProgram(program);
      program = 0;
    }
    return program;
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
      str = "";
    }
    return str;
  }

  public static void checkGlError(String op) {
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      Log.e(TAG, op + ": glError " + error);
      throw new RuntimeException(op + ": glError " + error);
    }
  }

  public static void checkEglError(String msg) {
    int error;
    if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
      throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
    }
  }
}
