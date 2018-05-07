package com.pedro.encoder.input.gl.render;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.Surface;
import com.pedro.encoder.R;
import com.pedro.encoder.utils.gl.GlUtil;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by pedro on 21/02/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class SimpleCameraRender {
  public final static String TAG = "SimpleCameraRender";

  private static final int FLOAT_SIZE_BYTES = 4;
  private static final int SQUARE_VERTEX_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
  private static final int SQUARE_VERTEX_DATA_POS_OFFSET = 0;
  private static final int SQUARE_VERTEX_DATA_UV_OFFSET = 3;

  //rotation matrix
  private final float[] squareVertexDataCamera = {
      // X, Y, Z, U, V
      -1f, -1f, 0f, 0f, 0f, //bottom left
      1f, -1f, 0f, 1f, 0f, //bottom right
      -1f, 1f, 0f, 0f, 1f, //top left
      1f, 1f, 0f, 1f, 1f, //top right
  };

  //fix orientation in camera2 landscape
  private final float[] squareVertexDataCamera2LandScape = {
      // X, Y, Z, U, V
      -1f, -1f, 0f, 0f, 1f, //bottom left
      1f, -1f, 0f, 0f, 0f, //bottom right
      -1f, 1f, 0f, 1f, 1f, //top left
      1f, 1f, 0f, 1f, 0f, //top right
  };

  private FloatBuffer squareVertex;

  private float[] MVPMatrix = new float[16];
  private float[] STMatrix = new float[16];

  private int[] texturesID = new int[1];

  private int program = -1;
  private int textureID = -1;
  private int uMVPMatrixHandle = -1;
  private int uSTMatrixHandle = -1;
  private int aPositionHandle = -1;
  private int aTextureCoordHandle = -1;
  private int uIsFrontCameraHandle = -1;

  private SurfaceTexture surfaceTexture;
  private Surface surface;
  private boolean isFrontCamera = false;
  private int streamWidth;
  private int streamHeight;
  private boolean isLandscape;

  public SimpleCameraRender() {
    Matrix.setIdentityM(MVPMatrix, 0);
    Matrix.setIdentityM(STMatrix, 0);
  }

  public void isCamera2LandScape(boolean isCamera2Landscape) {
    squareVertex = ByteBuffer.allocateDirect(squareVertexDataCamera.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    if (isCamera2Landscape) {
      squareVertex.put(squareVertexDataCamera2LandScape).position(0);
    } else {
      squareVertex.put(squareVertexDataCamera).position(0);
    }
  }

  public int getTextureId() {
    return textureID;
  }

  public SurfaceTexture getSurfaceTexture() {
    return surfaceTexture;
  }

  public Surface getSurface() {
    return surface;
  }

  public void updateFrame() {
    surfaceTexture.updateTexImage();
  }

  public void drawFrame(int width, int height, boolean keepAspectRatio) {
    GlUtil.checkGlError("drawFrame start");
    surfaceTexture.getTransformMatrix(STMatrix);

    if (keepAspectRatio) {
      if (width > height) { //landscape
        int realWidth = height * streamWidth / streamHeight;
        GLES20.glViewport((width - realWidth) / 2, 0, realWidth, height);
      } else { //portrait
        int realHeight = width * streamHeight / streamWidth;
        GLES20.glViewport(0, (height - realHeight) / 2, width, realHeight);
      }
    } else {
      GLES20.glViewport(0, 0, width, height);
    }
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

    GLES20.glUseProgram(program);

    squareVertex.position(SQUARE_VERTEX_DATA_POS_OFFSET);
    GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
        SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
    GLES20.glEnableVertexAttribArray(aPositionHandle);

    squareVertex.position(SQUARE_VERTEX_DATA_UV_OFFSET);
    GLES20.glVertexAttribPointer(aTextureCoordHandle, 2, GLES20.GL_FLOAT, false,
        SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
    GLES20.glEnableVertexAttribArray(aTextureCoordHandle);

    GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0);
    GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0);
    GLES20.glUniform1f(uIsFrontCameraHandle, isFrontCamera && isLandscape ? 1f : 0f);
    //camera
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID);
    //draw
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    GlUtil.checkGlError("drawFrame end");
  }

  /**
   * Initializes GL state.  Call this after the EGL surface has been created and made current.
   */
  public void initGl(Context context) {
    isLandscape = context.getResources().getConfiguration().orientation != 1;
    GlUtil.checkGlError("initGl start");
    String vertexShader = GlUtil.getStringFromRaw(context, R.raw.simple_vertex);
    String fragmentShader = GlUtil.getStringFromRaw(context, R.raw.camera_fragment);

    program = GlUtil.createProgram(vertexShader, fragmentShader);
    aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
    aTextureCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
    uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");
    uIsFrontCameraHandle = GLES20.glGetUniformLocation(program, "uIsFrontCamera");

    //camera texture
    GlUtil.createExternalTextures(1, texturesID, 0);
    textureID = texturesID[0];

    surfaceTexture = new SurfaceTexture(textureID);
    surface = new Surface(surfaceTexture);
    GlUtil.checkGlError("initGl end");
  }

  public void release() {
    surfaceTexture = null;
    surface = null;
  }

  public void faceChanged(boolean isFrontCamera) {
    this.isFrontCamera = isFrontCamera;
  }

  public void setStreamSize(int streamWidth, int streamHeight) {
    this.streamWidth = streamWidth;
    this.streamHeight = streamHeight;
  }
}
