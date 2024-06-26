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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.View;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.R;
import com.pedro.encoder.input.gl.AndroidViewSprite;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.encoder.utils.gl.TranslateTo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by pedro on 4/02/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class AndroidViewFilterRender extends BaseFilterRender {

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
  private int uSamplerViewHandle = -1;

  private int[] viewId = new int[] { -1, -1 };
  private View view;
  //Use 2 surfaces to avoid block render thread
  private SurfaceTexture surfaceTexture, surfaceTexture2;
  private Surface surface, surface2;
  private final Handler mainHandler;
  private boolean running = false;
  private ExecutorService thread = null;
  private boolean hardwareMode = true;
  private final AndroidViewSprite sprite;
  private volatile Status renderingStatus = Status.DONE1;

  private enum Status {
    RENDER1, RENDER2, DONE1, DONE2
  }

  public AndroidViewFilterRender() {
    squareVertex = ByteBuffer.allocateDirect(squareVertexDataFilter.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    squareVertex.put(squareVertexDataFilter).position(0);
    Matrix.setIdentityM(MVPMatrix, 0);
    Matrix.setIdentityM(STMatrix, 0);
    sprite = new AndroidViewSprite();
    mainHandler = new Handler(Looper.getMainLooper());
  }

  @Override
  protected void initGlFilter(Context context) {
    String vertexShader = GlUtil.getStringFromRaw(context, R.raw.simple_vertex);
    String fragmentShader = GlUtil.getStringFromRaw(context, R.raw.android_view_fragment);

    program = GlUtil.createProgram(vertexShader, fragmentShader);
    aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
    aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
    uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");
    uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler");
    uSamplerViewHandle = GLES20.glGetUniformLocation(program, "uSamplerView");

    GlUtil.createExternalTextures(viewId.length, viewId, 0);
    surfaceTexture = new SurfaceTexture(viewId[0]);
    surfaceTexture2 = new SurfaceTexture(viewId[1]);
    surface = new Surface(surfaceTexture);
    surface2 = new Surface(surfaceTexture2);
  }

  @Override
  protected void drawFilter() {
    final Status status = renderingStatus;
    switch (status) {
      case DONE1:
        surfaceTexture.setDefaultBufferSize(getPreviewWidth(), getPreviewHeight());
        surfaceTexture.updateTexImage();
        renderingStatus = Status.RENDER2;
        break;
      case DONE2:
        surfaceTexture2.setDefaultBufferSize(getPreviewWidth(), getPreviewHeight());
        surfaceTexture2.updateTexImage();
        renderingStatus = Status.RENDER1;
        break;
      case RENDER1:
        surfaceTexture2.setDefaultBufferSize(getPreviewWidth(), getPreviewHeight());
        surfaceTexture2.updateTexImage();
        break;
      case RENDER2:
      default:
        surfaceTexture.setDefaultBufferSize(getPreviewWidth(), getPreviewHeight());
        surfaceTexture.updateTexImage();
        break;
    }

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

    GLES20.glUniform1i(uSamplerHandle, 4);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId);
    //android view
    GLES20.glUniform1i(uSamplerViewHandle, 5);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE5);

    switch (status) {
      case DONE2:
      case RENDER1:
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, viewId[1]);
        break;
      case RENDER2:
      case DONE1:
      default:
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, viewId[0]);
        break;
    }
  }

  @Override
  public void release() {
    stopRender();
    GLES20.glDeleteProgram(program);
    viewId = new int[] { -1, -1 };
    surfaceTexture.release();
    surfaceTexture2.release();
  }

  public View getView() {
    return view;
  }

  public void setView(final View view) {
    stopRender();
    this.view = view;
    if (view != null) {
      view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
      sprite.setView(view);
      startRender();
    }
  }

  /**
   *
   * @param x Position in percent
   * @param y Position in percent
   */
  public void setPosition(float x, float y) {
    sprite.translate(x, y);
  }

  public void setPosition(TranslateTo positionTo) {
    sprite.translate(positionTo);
  }

  public void setRotation(int rotation) {
    sprite.setRotation(rotation);
  }

  public void setScale(float scaleX, float scaleY) {
    sprite.scale(scaleX, scaleY);
  }

  public PointF getScale() {
    return sprite.getScale();
  }

  public PointF getPosition() {
    return sprite.getTranslation();
  }

  public int getRotation() {
    return sprite.getRotation();
  }

  public boolean isHardwareMode() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hardwareMode;
  }

  /**
   * Draw in surface using hardware canvas. True by default
   */
  public void setHardwareMode(boolean hardwareMode) {
    this.hardwareMode = hardwareMode;
  }

  private void startRender() {
    running = true;
    thread = Executors.newSingleThreadExecutor();
    thread.execute(() -> {
      while (running) {
        final Status status = renderingStatus;
        if (status == Status.RENDER1 || status == Status.RENDER2) {
          final Canvas canvas;
          try {
            if (isHardwareMode()) {
              canvas = status == Status.RENDER1 ? surface.lockHardwareCanvas() : surface2.lockHardwareCanvas();
            } else {
              canvas = status == Status.RENDER1 ? surface.lockCanvas(null) : surface2.lockCanvas(null);
            }
          } catch (IllegalStateException e) {
            continue;
          }

          sprite.calculateDefaultScale(getPreviewWidth(), getPreviewHeight());
          PointF canvasPosition = sprite.getCanvasPosition(getPreviewWidth(), getPreviewHeight());
          PointF canvasScale = sprite.getCanvasScale(getPreviewWidth(), getPreviewHeight());
          PointF rotationAxis = sprite.getRotationAxis();
          int rotation = sprite.getRotation();

          canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
          canvas.translate(canvasPosition.x, canvasPosition.y);
          canvas.scale(canvasScale.x, canvasScale.y);
          canvas.rotate(rotation, rotationAxis.x, rotationAxis.y);
          try {
            view.draw(canvas);
            if (status == Status.RENDER1) {
              surface.unlockCanvasAndPost(canvas);
              renderingStatus = Status.DONE1;
            } else {
              surface2.unlockCanvasAndPost(canvas);
              renderingStatus = Status.DONE2;
            }
            //Sometimes draw could crash if you don't use main thread. Ensuring you can render always
          } catch (Exception e) {
            mainHandler.post(() -> {
              view.draw(canvas);
              if (status == Status.RENDER1) {
                surface.unlockCanvasAndPost(canvas);
                renderingStatus = Status.DONE1;
              } else {
                surface2.unlockCanvasAndPost(canvas);
                renderingStatus = Status.DONE2;
              }
            });
          }
        }
        else {
          // not rendering, no need to try again immediately
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {

          }
        }
      }
    });
  }

  private void stopRender() {
    running = false;
    if (thread != null) {
      thread.shutdownNow();
      thread = null;
    }
    renderingStatus = Status.DONE1;
  }
}