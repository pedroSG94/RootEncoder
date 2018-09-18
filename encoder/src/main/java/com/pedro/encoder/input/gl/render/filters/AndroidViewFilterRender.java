package com.pedro.encoder.input.gl.render.filters;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.RequiresApi;
import android.view.Surface;
import android.view.View;
import com.pedro.encoder.R;
import com.pedro.encoder.utils.gl.GlUtil;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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

  private int[] viewId = new int[1];
  private View view;
  private SurfaceTexture surfaceTexture;
  private Surface surface;
  private Handler mainHandler;

  public AndroidViewFilterRender() {
    squareVertex = ByteBuffer.allocateDirect(squareVertexDataFilter.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    squareVertex.put(squareVertexDataFilter).position(0);
    Matrix.setIdentityM(MVPMatrix, 0);
    Matrix.setIdentityM(STMatrix, 0);
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

    GlUtil.createExternalTextures(1, viewId, 0);
    surfaceTexture = new SurfaceTexture(viewId[0]);
    surface = new Surface(surfaceTexture);
  }

  @Override
  protected void drawFilter() {
    surfaceTexture.setDefaultBufferSize(getWidth(), getHeight());
    if (view != null) {
      mainHandler.post(new Runnable() {
        @Override
        public void run() {
          Canvas canvas = surface.lockCanvas(null);
          canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
          view.draw(canvas);
          surface.unlockCanvasAndPost(canvas);
        }
      });
    }
    surfaceTexture.updateTexImage();

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
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, viewId[0]);
  }

  @Override
  public void release() {

  }

  public View getView() {
    return view;
  }

  public void setView(final View view) {
    this.view = view;
  }
}
