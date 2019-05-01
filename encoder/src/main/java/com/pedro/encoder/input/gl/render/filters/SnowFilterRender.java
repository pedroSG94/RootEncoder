package com.pedro.encoder.input.gl.render.filters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import androidx.annotation.RequiresApi;
import com.pedro.encoder.R;
import com.pedro.encoder.input.gl.TextureLoader;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.StreamObjectBase;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by pedro on 19/12/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class SnowFilterRender extends BaseFilterRender {

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
  private int uTimeHandle = -1;
  private int uSnowHandle = -1;

  private long START_TIME = System.currentTimeMillis();
  private TextureLoader textureLoader = new TextureLoader();
  private StreamObjectBase streamObjectBase;
  private int[] snowTextureId = new int[] { -1 };

  public SnowFilterRender() {
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
    String fragmentShader = GlUtil.getStringFromRaw(context, R.raw.snow_fragment);

    program = GlUtil.createProgram(vertexShader, fragmentShader);
    aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
    aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
    uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");
    uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler");
    uTimeHandle = GLES20.glGetUniformLocation(program, "uTime");
    uSnowHandle = GLES20.glGetUniformLocation(program, "uSnow");
    Bitmap snowBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.snow_flakes);
    streamObjectBase = new ImageStreamObject();
    ((ImageStreamObject) streamObjectBase).load(snowBitmap);
    textureLoader.setImageStreamObject((ImageStreamObject) streamObjectBase);
    snowTextureId = textureLoader.load();
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
    float time = ((float) (System.currentTimeMillis() - START_TIME)) / 1000f;
    if (time >= 2) START_TIME += 2000;
    GLES20.glUniform1f(uTimeHandle, time);

    GLES20.glUniform1i(uSamplerHandle, 4);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId);

    GLES20.glUniform1i(uSnowHandle, 5);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE5);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, snowTextureId[0]);
  }

  @Override
  public void release() {
    GLES20.glDeleteProgram(program);
    releaseTextureId();
    if (streamObjectBase != null) streamObjectBase.recycle();
  }

  private void releaseTextureId() {
    if (snowTextureId != null) GLES20.glDeleteTextures(1, snowTextureId, 0);
    snowTextureId = new int[] { -1 };
  }
}