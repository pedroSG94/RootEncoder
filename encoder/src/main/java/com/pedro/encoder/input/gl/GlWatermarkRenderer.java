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

  private final float[] squareVertexData2 = {
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
  private FloatBuffer squareVertex2;
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

  //initGl2
  private int program2 = -1;
  private int aTextureHandle = -1;
  private int uMVPMatrixHandle2 = -1;
  private int uSTMatrixHandle2 = -1;
  private int aPositionHandle2 = -1;
  private int uSamplerHandle = -1;

  private SurfaceTexture surfaceTexture;
  private Surface surface;

  private int[] streamObjectTextureId = null;
  private StreamObjectBase streamObjectBase = null;
  private TextureLoader textureLoader = new TextureLoader();
  private Sprite sprite;
  private float alpha = 1f;
  private int encoderWidth;
  private int encoderHeight;

  private final int[] fboId = new int[] { 0 };
  private final int[] rboId = new int[] { 0 };
  private final int[] texId = new int[] { 0 };

  public GlWatermarkRenderer(Context context, boolean isCamera2Landscape) {
    this.context = context;
    squareVertex = ByteBuffer.allocateDirect(squareVertexData.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    if (isCamera2Landscape) {
      squareVertex.put(squareVertexDataCamera2LandScape).position(0);
    } else {
      squareVertex.put(squareVertexData).position(0);
    }

    squareVertex2 = ByteBuffer.allocateDirect(squareVertexData2.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    squareVertex2.put(squareVertexData2).position(0);
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

  public void drawScreen(int width, int height) {
    GlUtil.checkGlError("drawScreen start");
    surfaceTexture.getTransformMatrix(STMatrix);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId[0]);
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
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    GlUtil.checkGlError("drawScreen end");
  }

  public void drawEncoder(int width, int height) {
    GlUtil.checkGlError("drawScreen start");
    GLES20.glViewport(0, 0, width, height);
    GLES20.glUseProgram(program2);

    squareVertex2.position(0);
    GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
        SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex2);
    GLES20.glEnableVertexAttribArray(aPositionHandle2);

    squareVertex2.position(3);
    GLES20.glVertexAttribPointer(aTextureHandle, 2, GLES20.GL_FLOAT, false,
        SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex2);
    GLES20.glEnableVertexAttribArray(aTextureHandle);

    GLES20.glUniformMatrix4fv(uMVPMatrixHandle2, 1, false, MVPMatrix, 0);
    GLES20.glUniformMatrix4fv(uSTMatrixHandle2, 1, false, STMatrix, 0);
    GLES20.glUniform1i(uSamplerHandle, 3);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0]);
    //draw
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    GlUtil.checkGlError("drawScreen end");
  }

  /**
   * Initializes GL state.  Call this after the EGL surface has been created and made current.
   */
  public void initGl(int width, int height) {
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
    initFBO(width, height);
  }

  public void initGl2() {
    GlUtil.checkGlError("initGl start");
    String vertexShader = GlUtil.getStringFromRaw(context, R.raw.simple_vertex);
    String fragmentShader = GlUtil.getStringFromRaw(context, R.raw.simple_fragment);

    program2 = GlUtil.createProgram(vertexShader, fragmentShader);
    aPositionHandle2 = GLES20.glGetAttribLocation(program2, "aPosition");
    aTextureHandle = GLES20.glGetAttribLocation(program2, "aTextureCoord");
    uMVPMatrixHandle2 = GLES20.glGetUniformLocation(program2, "uMVPMatrix");
    uSTMatrixHandle2 = GLES20.glGetUniformLocation(program2, "uSTMatrix");
    uSamplerHandle = GLES20.glGetUniformLocation(program2, "uSampler");

    GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0]);
    GlUtil.checkGlError("initGl end");
  }

  private void initFBO(int width, int height) {
    GlUtil.checkGlError("initFBO_S");

    GLES20.glGenFramebuffers(1, fboId, 0);
    GLES20.glGenRenderbuffers(1, rboId, 0);
    GLES20.glGenTextures(1, texId, 0);

    GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, rboId[0]);
    GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width,
        height);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId[0]);
    GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
        GLES20.GL_RENDERBUFFER, rboId[0]);

    GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0]);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA,
        GLES20.GL_UNSIGNED_BYTE, null);
    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
        GLES20.GL_TEXTURE_2D, texId[0], 0);

    GlUtil.checkGlError("initFBO_E");
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
