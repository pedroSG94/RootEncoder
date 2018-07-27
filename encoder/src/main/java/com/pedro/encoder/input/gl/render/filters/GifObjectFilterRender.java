package com.pedro.encoder.input.gl.render.filters;

import android.content.Context;
import android.graphics.PointF;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.support.annotation.RequiresApi;
import com.pedro.encoder.R;
import com.pedro.encoder.input.gl.Sprite;
import com.pedro.encoder.input.gl.TextureLoader;
import com.pedro.encoder.utils.gl.GifStreamObject;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.encoder.utils.gl.TranslateTo;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by pedro on 27/07/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class GifObjectFilterRender extends BaseFilterRender {

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
  private int aTextureCameraHandle = -1;
  private int aTextureWatermarkHandle = -1;
  private int uMVPMatrixHandle = -1;
  private int uSTMatrixHandle = -1;
  private int sCameraHandle = -1;
  private int sWatermarkHandle = -1;
  private int uAlphaHandle = -1;

  private FloatBuffer squareVertexWatermark;

  private int[] streamObjectTextureId = null;
  private TextureLoader textureLoader = new TextureLoader();
  private GifStreamObject gifStreamObject;
  private Sprite sprite;
  private float alpha = 1f;
  private boolean shouldLoad = false;

  public GifObjectFilterRender() {
    squareVertex = ByteBuffer.allocateDirect(squareVertexDataFilter.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    squareVertex.put(squareVertexDataFilter).position(0);
    sprite = new Sprite();
    float[] vertices = sprite.getTransformedVertices();
    squareVertexWatermark = ByteBuffer.allocateDirect(vertices.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    squareVertexWatermark.put(vertices).position(0);
    Matrix.setIdentityM(MVPMatrix, 0);
    Matrix.setIdentityM(STMatrix, 0);
  }

  @Override
  protected void initGlFilter(Context context) {
    String vertexShader = GlUtil.getStringFromRaw(context, R.raw.watermark_vertex);
    String fragmentShader = GlUtil.getStringFromRaw(context, R.raw.watermark_fragment);

    program = GlUtil.createProgram(vertexShader, fragmentShader);
    aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
    aTextureCameraHandle = GLES20.glGetAttribLocation(program, "aTextureCameraCoord");
    aTextureWatermarkHandle = GLES20.glGetAttribLocation(program, "aTextureWatermarkCoord");
    uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");
    sCameraHandle = GLES20.glGetUniformLocation(program, "sCamera");
    sWatermarkHandle = GLES20.glGetUniformLocation(program, "sWatermark");
    uAlphaHandle = GLES20.glGetUniformLocation(program, "uAlpha");
  }

  @Override
  protected void drawFilter() {
    if (shouldLoad) {
      streamObjectTextureId = textureLoader.load();
      shouldLoad = false;
    }

    GLES20.glUseProgram(program);

    squareVertex.position(SQUARE_VERTEX_DATA_POS_OFFSET);
    GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
        SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
    GLES20.glEnableVertexAttribArray(aPositionHandle);

    squareVertex.position(SQUARE_VERTEX_DATA_UV_OFFSET);
    GLES20.glVertexAttribPointer(aTextureCameraHandle, 2, GLES20.GL_FLOAT, false,
        SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
    GLES20.glEnableVertexAttribArray(aTextureCameraHandle);

    squareVertexWatermark.position(SQUARE_VERTEX_DATA_POS_OFFSET);
    GLES20.glVertexAttribPointer(aTextureWatermarkHandle, 2, GLES20.GL_FLOAT, false,
        2 * FLOAT_SIZE_BYTES, squareVertexWatermark);
    GLES20.glEnableVertexAttribArray(aTextureWatermarkHandle);

    GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0);
    GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0);
    //camera
    GLES20.glUniform1i(sCameraHandle, 4);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId);
    // watermark
    GLES20.glUniform1i(sWatermarkHandle, 5);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE5);
    if (streamObjectTextureId != null) {
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
          streamObjectTextureId[gifStreamObject.updateFrame()]);
      //watermark enable, set actual alpha
      GLES20.glUniform1f(uAlphaHandle, alpha);
    } else {
      //no watermark. Set watermark size transparent
      GLES20.glUniform1f(uAlphaHandle, 0.0f);
    }
  }

  @Override
  public void release() {
    if (streamObjectTextureId != null) GLES20.glDeleteTextures(1, streamObjectTextureId, 0);
    streamObjectTextureId = null;
    sprite.reset();
    gifStreamObject.recycle();
  }

  public void setGif(InputStream inputStream) throws IOException, RuntimeException {
    gifStreamObject = new GifStreamObject();
    gifStreamObject.load(inputStream);
    if (streamObjectTextureId != null) GLES20.glDeleteTextures(1, streamObjectTextureId, 0);
    streamObjectTextureId = null;
    textureLoader.setGifStreamObject(gifStreamObject);
    shouldLoad = true;
  }

  public void setAlpha(float alpha) {
    this.alpha = alpha;
  }

  public void setScale(float scaleX, float scaleY) {
    sprite.scale(scaleX, scaleY);
    squareVertexWatermark.put(sprite.getTransformedVertices()).position(0);
  }

  public void setPosition(float x, float y) {
    sprite.translate(x, y);
    squareVertexWatermark.put(sprite.getTransformedVertices()).position(0);
  }

  public void setPosition(TranslateTo positionTo) {
    sprite.translate(positionTo);
    squareVertexWatermark.put(sprite.getTransformedVertices()).position(0);
  }

  public PointF getScale() {
    return sprite.getScale();
  }

  public PointF getPosition() {
    return sprite.getTranslation();
  }

  public void setDefaultScale(int streamWidth, int streamHeight) {
    sprite.scale(gifStreamObject.getWidth() * 100 / streamWidth,
        gifStreamObject.getHeight() * 100 / streamHeight);
    squareVertexWatermark.put(sprite.getTransformedVertices()).position(0);
  }
}
