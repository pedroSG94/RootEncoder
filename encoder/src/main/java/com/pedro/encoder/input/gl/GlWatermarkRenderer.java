package com.pedro.encoder.input.gl;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.Surface;
import com.pedro.encoder.R;
import com.pedro.encoder.utils.gl.GifStreamObject;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.StreamObjectBase;
import com.pedro.encoder.utils.gl.TextStreamObject;
import com.pedro.encoder.utils.gl.TranslateTo;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by pedro on 21/09/17.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class GlWatermarkRenderer {

  public final static String TAG = "TextureManager";

  private Context context;

  private static final int FLOAT_SIZE_BYTES = 4;
  private static final int SQUARE_VERTEX_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
  private static final int SQUARE_VERTEX_DATA_POS_OFFSET = 0;
  private static final int SQUARE_VERTEX_DATA_UV_OFFSET = 3;

  //rotation matrix
  private final float[] squareVertexData = {
      // X, Y, Z, U, V
      -1f, -1f, 0f, 0f, 0f, //bottom left
      1f, -1f, 0f, 1f, 0f, //bottom right
      -1f, 1f, 0f, 0f, 1f, //top left
      1f, 1f, 0f, 1f, 1f, //top right
  };

  private FloatBuffer squareVertex;
  private FloatBuffer squareVertexWatermark;

  private float[] MVPMatrix = new float[16];
  private float[] STMatrix = new float[16];

  private int[] texturesID = new int[1];

  private int program = -1;
  private int textureID = -1;
  private int uMVPMatrixHandle = -1;
  private int uSTMatrixHandle = -1;
  private int aPositionHandle = -1;
  private int aTextureCameraHandle = -1;
  private int aTextureWatermarkHandle = -1;
  private int sWaterMarkHandle = -1;
  private int uAlphaHandle = -1;

  private SurfaceTexture surfaceTexture;
  private Surface surface;

  private int[] streamObjectTextureId = null;
  private StreamObjectBase streamObjectBase = null;
  private TextureLoader textureLoader = new TextureLoader();
  private Sprite sprite;
  private float alpha = 1f;
  private int encoderWidth;
  private int encoderHeight;

  public GlWatermarkRenderer(Context context) {
    this.context = context;
    squareVertex = ByteBuffer.allocateDirect(squareVertexData.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    squareVertex.put(squareVertexData).position(0);

    sprite = new Sprite();
    float[] vertices = sprite.getTransformedVertices();
    squareVertexWatermark = ByteBuffer.allocateDirect(vertices.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    squareVertexWatermark.put(vertices).position(0);

    Matrix.setIdentityM(MVPMatrix, 0);
    Matrix.setIdentityM(STMatrix, 0);
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

  public void drawFrame(int width, int height) {
    GlUtil.checkGlError("drawFrame start");
    surfaceTexture.getTransformMatrix(STMatrix);

    GLES20.glViewport(0, 0, width, height);
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

    GLES20.glUseProgram(program);

    squareVertex.position(SQUARE_VERTEX_DATA_POS_OFFSET);
    GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
        SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
    GLES20.glEnableVertexAttribArray(aPositionHandle);

    squareVertex.position(SQUARE_VERTEX_DATA_UV_OFFSET);
    GLES20.glVertexAttribPointer(aTextureCameraHandle, 2, GLES20.GL_FLOAT, false,
        SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
    GLES20.glEnableVertexAttribArray(aTextureCameraHandle);

    squareVertexWatermark.position(0);
    GLES20.glVertexAttribPointer(aTextureWatermarkHandle, 2, GLES20.GL_FLOAT, false,
        2 * FLOAT_SIZE_BYTES, squareVertexWatermark);
    GLES20.glEnableVertexAttribArray(aTextureWatermarkHandle);

    GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0);
    GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0);
    //camera
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID);
    // watermark
    GLES20.glUniform1i(sWaterMarkHandle, 1);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE1);

    if (streamObjectTextureId != null) {
      if (streamObjectTextureId[0] == -1) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, streamObjectTextureId[0]);
        streamObjectTextureId = null;
        streamObjectBase = null;
      } else {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
            streamObjectTextureId[streamObjectBase.updateFrame()]);
      }
      //watermark enable, set actual alpha
      GLES20.glUniform1f(uAlphaHandle, alpha);
    } else {
      //no watermark. Set watermark size transparent
      GLES20.glUniform1f(uAlphaHandle, 0.0f);
    }
    //draw
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    GlUtil.checkGlError("drawFrame end");
  }

  /**
   * Initializes GL state.  Call this after the EGL surface has been created and made current.
   */
  public void initGl() {
    GlUtil.checkGlError("initGl start");
    String vertexShader = GlUtil.getStringFromRaw(context, R.raw.watermark_vertex);
    String fragmentShader = GlUtil.getStringFromRaw(context, R.raw.watermark_fragment);

    program = GlUtil.createProgram(vertexShader, fragmentShader);
    aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
    aTextureCameraHandle = GLES20.glGetAttribLocation(program, "aTextureCameraCoord");
    aTextureWatermarkHandle = GLES20.glGetAttribLocation(program, "aTextureWatermarkCoord");
    uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");
    uAlphaHandle = GLES20.glGetUniformLocation(program, "uAlpha");
    sWaterMarkHandle = GLES20.glGetUniformLocation(program, "sWatermark");

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
    streamObjectTextureId = null;
    streamObjectBase = null;
    sprite.reset();
  }

  public void setImage(ImageStreamObject imageStreamObject) {
    streamObjectTextureId = null;
    streamObjectBase = imageStreamObject;
    textureLoader.setImageStreamObject(imageStreamObject);
    streamObjectTextureId = textureLoader.load();
    prepareDefaultSpriteValues();
  }

  public void setText(TextStreamObject textStreamObject) {
    streamObjectTextureId = null;
    streamObjectBase = textStreamObject;
    textureLoader.setTextStreamObject(textStreamObject);
    streamObjectTextureId = textureLoader.load();
    prepareDefaultSpriteValues();
  }

  public void setGif(GifStreamObject gifStreamObject) {
    streamObjectTextureId = null;
    streamObjectBase = gifStreamObject;
    textureLoader.setGifStreamObject(gifStreamObject);
    streamObjectTextureId = textureLoader.load();
    prepareDefaultSpriteValues();
  }

  public void clear() {
    streamObjectTextureId = new int[] { -1 };
    sprite.reset();
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

  //set scale of the sprite depend of bitmap size
  private void prepareDefaultSpriteValues() {
    sprite.scale(streamObjectBase.getWidth() * 100 / encoderWidth,
        streamObjectBase.getHeight() * 100 / encoderHeight);
    squareVertexWatermark.put(sprite.getTransformedVertices()).position(0);
  }

  public void setStreamSize(int encoderWidth, int encoderHeight) {
    this.encoderWidth = encoderWidth;
    this.encoderHeight = encoderHeight;
  }
}
