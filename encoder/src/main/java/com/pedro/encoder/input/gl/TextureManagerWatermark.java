package com.pedro.encoder.input.gl;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.Surface;
import com.pedro.encoder.R;
import com.pedro.encoder.utils.gl.TextStreamObject;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.encoder.utils.gl.GifStreamObject;
import com.pedro.encoder.utils.gl.ImageStreamObject;
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
  //gif
  private int[] gifTexturesId;
  private GifStreamObject gifStreamObject;
  //image
  private int[] imageTextureId = new int[1];
  private ImageStreamObject imageStreamObject;
  //text
  private int[] textTextureId = new int[1];
  private TextStreamObject textStreamObject;

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
    GLES20.glUniform1i(waterMarkHandle, 2);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
    System.currentTimeMillis();

    if (gifStreamObject != null) {
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, gifTexturesId[gifStreamObject.updateGifFrame()]);
    } else if (imageStreamObject != null) {
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTextureId[0]);
    } else if (textStreamObject != null) {
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textTextureId[0]);
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

    //watermark
    if (gifStreamObject != null) {
      loadGif(gifStreamObject);
    } else if (imageStreamObject != null) {
      loadImage(imageStreamObject);
    } else if (textStreamObject != null) {
      loadText(textStreamObject);
    }

    GlUtil.checkGlError("glTexParameter");
    surfaceTexture = new SurfaceTexture(textureID);
    surface = new Surface(surfaceTexture);
  }

  public void release() {
    surfaceTexture = null;
    surface = null;
    imageStreamObject = null;
    textStreamObject = null;
    gifStreamObject = null;
  }

  public void setImage(ImageStreamObject imageStreamObject) {
    this.imageStreamObject = imageStreamObject;
    textStreamObject = null;
    gifStreamObject = null;
  }

  private void loadImage(ImageStreamObject imageStreamObject) {
    GlUtil.createTextures(imageStreamObject.getNumFrames(), imageTextureId, 0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTextureId[0]);
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, imageStreamObject.getImageBitmap(), 0);
    imageStreamObject.recycle();
  }

  public void setText(TextStreamObject textStreamObject) {
    this.textStreamObject = textStreamObject;
    imageStreamObject = null;
    gifStreamObject = null;
  }

  private void loadText(TextStreamObject textStreamObject) {
    GlUtil.createTextures(textStreamObject.getNumFrames(), textTextureId, 0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textTextureId[0]);
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textStreamObject.getImageBitmap(), 0);
    textStreamObject.recycle();
  }

  public void setGif(GifStreamObject gifStreamObject) {
    this.gifStreamObject = gifStreamObject;
    textStreamObject = null;
    imageStreamObject = null;
  }

  private void loadGif(GifStreamObject gifStreamObject) {
    gifTexturesId = new int[gifStreamObject.getNumFrames()];
    GlUtil.createTextures(gifStreamObject.getNumFrames(), gifTexturesId, 0);
    for (int i = 0; i < gifStreamObject.getNumFrames(); i++) {
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, gifTexturesId[i]);
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, gifStreamObject.getGifBitmaps()[i], 0);
    }
    gifStreamObject.recycle();
  }
}