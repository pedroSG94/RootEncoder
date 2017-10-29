package com.pedro.encoder.input.gl;

import android.content.Context;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by pedro on 21/09/17.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class TextureManagerWatermark {

  public final static String TAG = "TextureManager";

  private Context context;

  private static final int FLOAT_SIZE_BYTES = 4;
  private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
  private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
  private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;

  //rotation matrix
  private final float[] triangleVerticesData = {
      // X, Y, Z, U, V
      -1.0f, -1.0f, 0, 0.f, 0.f, 1.0f, -1.0f, 0, 1.f, 0.f, -1.0f, 1.0f, 0, 0.f, 1.f, 1.0f, 1.0f, 0,
      1.f, 1.f,
  };

  private FloatBuffer triangleVertices;

  private float[] mMVPMatrix = new float[16];
  private float[] mSTMatrix = new float[16];

  private int[] texturesID = new int[1];

  private int program = -1;
  private int textureID = -1;
  private int uMVPMatrixHandle = -1;
  private int uSTMatrixHandle = -1;
  private int aPositionHandle = -1;
  private int aTextureHandle = -1;
  private int waterMarkHandle = -1;

  private SurfaceTexture surfaceTexture;
  private Surface surface;
  //text
  private int[] streamObjectTextureId = null;
  private StreamObjectBase streamObjectBase = null;
  private TextureLoader textureLoader = new TextureLoader();

  public TextureManagerWatermark(Context context) {
    this.context = context;
    triangleVertices = ByteBuffer.allocateDirect(triangleVerticesData.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    triangleVertices.put(triangleVerticesData).position(0);
    Matrix.setIdentityM(mSTMatrix, 0);
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
    GlUtil.checkGlError("onDrawFrame start");
    surfaceTexture.getTransformMatrix(mSTMatrix);

    GLES20.glViewport(0, 0, width, height);
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

    GLES20.glUseProgram(program);
    GlUtil.checkGlError("glUseProgram");

    triangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
    GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
        TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices);
    GlUtil.checkGlError("glVertexAttribPointer maPosition");
    GLES20.glEnableVertexAttribArray(aPositionHandle);
    GlUtil.checkGlError("glEnableVertexAttribArray aPositionHandle");

    triangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
    GLES20.glVertexAttribPointer(aTextureHandle, 2, GLES20.GL_FLOAT, false,
        TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices);
    GlUtil.checkGlError("glVertexAttribPointer aTextureHandle");
    GLES20.glEnableVertexAttribArray(aTextureHandle);
    GlUtil.checkGlError("glEnableVertexAttribArray aTextureHandle");

    Matrix.setIdentityM(mMVPMatrix, 0);
    GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mMVPMatrix, 0);
    GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, mSTMatrix, 0);
    //camera
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID);
    // watermark
    GLES20.glUniform1i(waterMarkHandle, 1);
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
    }
    //draw
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    GlUtil.checkGlError("glDrawArrays");
  }

  /**
   * Initializes GL state.  Call this after the EGL surface has been created and made current.
   */
  public void initGl() {
    GlUtil.checkGlError("create handlers start");
    String vertexShader = GlUtil.getStringFromRaw(context, R.raw.simple_vertex);
    String fragmentShader = GlUtil.getStringFromRaw(context, R.raw.watermark_fragment);

    program = GlUtil.createProgram(vertexShader, fragmentShader);
    aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
    aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
    uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");
    waterMarkHandle = GLES20.glGetUniformLocation(program, "watermark");
    GlUtil.checkGlError("create handlers end");
    //camera texture
    GlUtil.createExternalTextures(1, texturesID, 0);
    textureID = texturesID[0];

    GlUtil.checkGlError("glTexParameter");
    surfaceTexture = new SurfaceTexture(textureID);
    surface = new Surface(surfaceTexture);
  }

  public void release() {
    surfaceTexture = null;
    surface = null;
    streamObjectTextureId = null;
    streamObjectBase = null;
  }

  public void setImage(ImageStreamObject imageStreamObject) {
    streamObjectTextureId = null;
    streamObjectBase = imageStreamObject;
    textureLoader.setImageStreamObject(imageStreamObject);
    streamObjectTextureId = textureLoader.load();
  }

  public void setText(TextStreamObject textStreamObject) {
    streamObjectTextureId = null;
    streamObjectBase = textStreamObject;
    textureLoader.setTextStreamObject(textStreamObject);
    streamObjectTextureId = textureLoader.load();
  }

  public void setGif(GifStreamObject gifStreamObject) {
    streamObjectTextureId = null;
    streamObjectBase = gifStreamObject;
    textureLoader.setGifStreamObject(gifStreamObject);
    streamObjectTextureId = textureLoader.load();
  }

  public void clear() {
    streamObjectTextureId = new int[] { -1 };
  }
}
