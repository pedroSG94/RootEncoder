package com.pedro.encoder.input.gl.render.filters.object;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import androidx.annotation.RequiresApi;

import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import com.pedro.encoder.R;
import com.pedro.encoder.input.gl.Sprite;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.encoder.utils.gl.TranslateTo;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by pedro on 18/07/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class SurfaceFilterRender extends BaseObjectFilterRender {

  public interface SurfaceReadyCallback {
    void surfaceReady(SurfaceTexture surfaceTexture);
  }

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
  private int uSamplerSurfaceHandle = -1;
  private int aTextureObjectHandle = -1;
  private int uAlphaHandle = -1;

  private int[] surfaceId = new int[] { -1 };
  private Sprite sprite;
  private FloatBuffer squareVertexSurface;
  private SurfaceTexture surfaceTexture;
  private Surface surface;
  private float alpha = 1f;
  private SurfaceReadyCallback surfaceReadyCallback;

  public SurfaceFilterRender() {
    this(null);
  }

  public SurfaceFilterRender(SurfaceReadyCallback surfaceReadyCallback) {
    this.surfaceReadyCallback = surfaceReadyCallback;
    squareVertex = ByteBuffer.allocateDirect(squareVertexDataFilter.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    squareVertex.put(squareVertexDataFilter).position(0);
    sprite = new Sprite();
    float[] vertices = sprite.getTransformedVertices();
    squareVertexSurface = ByteBuffer.allocateDirect(vertices.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    squareVertexSurface.put(vertices).position(0);
    sprite.getTransformedVertices();

    Matrix.setIdentityM(MVPMatrix, 0);
    Matrix.setIdentityM(STMatrix, 0);
  }

  @Override
  protected void initGlFilter(Context context) {
    String vertexShader = GlUtil.getStringFromRaw(context, R.raw.object_vertex);
    String fragmentShader = GlUtil.getStringFromRaw(context, R.raw.surface_fragment);

    program = GlUtil.createProgram(vertexShader, fragmentShader);
    aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
    aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
    aTextureObjectHandle = GLES20.glGetAttribLocation(program, "aTextureObjectCoord");
    uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");
    uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler");
    uSamplerSurfaceHandle = GLES20.glGetUniformLocation(program, "uSamplerSurface");
    uAlphaHandle = GLES20.glGetUniformLocation(program, "uAlpha");

    GlUtil.createExternalTextures(surfaceId.length, surfaceId, 0);
    surfaceTexture = new SurfaceTexture(surfaceId[0]);
    surfaceTexture.setDefaultBufferSize(getWidth(), getHeight());
    surface = new Surface(surfaceTexture);
    if (surfaceReadyCallback != null) {
      new Handler(Looper.getMainLooper()).post(new Runnable() {
        @Override
        public void run() {
          surfaceReadyCallback.surfaceReady(surfaceTexture);
        }
      });
    }
  }

  @Override
  protected void drawFilter() {
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

    squareVertexSurface.position(SQUARE_VERTEX_DATA_POS_OFFSET);
    GLES20.glVertexAttribPointer(aTextureObjectHandle, 2, GLES20.GL_FLOAT, false,
        2 * FLOAT_SIZE_BYTES, squareVertexSurface);
    GLES20.glEnableVertexAttribArray(aTextureObjectHandle);

    GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0);
    GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0);

    GLES20.glUniform1i(uSamplerHandle, 4);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId);
    //Surface
    GLES20.glUniform1i(uSamplerSurfaceHandle, 5);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE5);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, surfaceId[0]);
    //Set alpha. 0f if no image loaded.
    GLES20.glUniform1f(uAlphaHandle, surfaceId[0] == -1 ? 0f : alpha);
  }

  @Override
  public void release() {
    if (surfaceId != null) GLES20.glDeleteTextures(1, surfaceId, 0);
    surfaceId = new int[] { -1 };
    surfaceTexture.release();
    surface.release();
  }

  /**
   * This texture must be renderer using an api called on main thread to avoid possible errors
   */
  public SurfaceTexture getSurfaceTexture() {
    return surfaceTexture;
  }

  /**
   * This surface must be renderer using an api called on main thread to avoid possible errors
   */
  public Surface getSurface() {
    return surface;
  }

  public void setAlpha(float alpha) {
    this.alpha = alpha;
  }

  public void setScale(float scaleX, float scaleY) {
    sprite.scale(scaleX, scaleY);
    squareVertexSurface.put(sprite.getTransformedVertices()).position(0);
  }

  public void setPosition(float x, float y) {
    sprite.translate(x, y);
    squareVertexSurface.put(sprite.getTransformedVertices()).position(0);
  }

  public void setPosition(TranslateTo positionTo) {
    sprite.translate(positionTo);
    squareVertexSurface.put(sprite.getTransformedVertices()).position(0);
  }

  public PointF getScale() {
    return sprite.getScale();
  }

  public PointF getPosition() {
    return sprite.getTranslation();
  }
}
