package com.pedro.rtplibrary.view;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import com.pedro.encoder.input.gl.SurfaceManager;
import com.pedro.encoder.utils.gl.GlUtil;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by pedro on 26/02/18.
 * Simple 3D cube with touch rotation.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class CustomGlSurfaceView extends GLSurfaceView {

  private CubeRenderer mRenderer;
  private float mPreviousX;
  private float mPreviousY;
  private float mPreviousDeg;
  private int previewWidth, previewHeight;
  private SurfaceTexture surfaceTexture;
  private SurfaceManager surfaceManager;
  //class used to share frames with surface from VideoEncoder class and GlSurfaceView
  private int encoderWidth, encoderHeight;
  private final Object sync = new Object();
  private EGLContext eglContext;
  private EGLDisplay savedEglDisplay;
  private EGLSurface savedEglDrawSurface;
  private EGLSurface savedEglReadSurface;
  private EGLContext savedEglContext;

  private class MyConfigChooser implements GLSurfaceView.EGLConfigChooser {

    @Override
    public EGLConfig chooseConfig(EGL10 egl10,
        javax.microedition.khronos.egl.EGLDisplay eglDisplay) {
      int attribs[] = {
          EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
          EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
          /* AA https://stackoverflow.com/questions/27035893/antialiasing-in-opengl-es-2-0 */
          //EGL14.EGL_SAMPLE_BUFFERS, 1 /* true */,
          //EGL14.EGL_SAMPLES, 4, /* increase to more smooth limit of your GPU */
          EGL14.EGL_NONE
      };
      EGLConfig[] configs = new EGLConfig[1];
      int[] configCounts = new int[1];
      egl10.eglChooseConfig(eglDisplay, attribs, configs, 1, configCounts);
      if (configCounts[0] == 0) {
        Log.e("GlConfig", "failed config");
        return null;
      } else {
        return configs[0];
      }
    }
  }

  public CustomGlSurfaceView(Context context) {
    this(context, null);
  }

  public CustomGlSurfaceView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setEGLConfigChooser(new MyConfigChooser());
    setEGLContextClientVersion(2);
    setRenderer(mRenderer = new CubeRenderer());
    setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
  }

  public void addMediaCodecSurface(Surface surface) {
    synchronized (sync) {
      surfaceManager = new SurfaceManager(surface, eglContext);
    }
  }

  public void removeMediaCodecSurface() {
    synchronized (sync) {
      if (surfaceManager != null) {
        surfaceManager.release();
        surfaceManager = null;
      }
    }
  }

  public void setEncoderSize(int width, int height) {
    this.encoderWidth = width;
    this.encoderHeight = height;
  }

  @Override
  public void onPause() {
  } //do stuff

  @Override
  public void onResume() {
  } //do stuff

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (event != null) {
      System.out.println();
      if (event.getPointerCount() == 1) {
        float x = event.getX();
        float y = event.getY();
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
          if (mRenderer != null) {
            float deltaX = (x - mPreviousX) / this.getWidth() * 360;
            float deltaY = (y - mPreviousY) / this.getHeight() * 360;
            mRenderer.mDeltaX += deltaY;
            mRenderer.mDeltaY += deltaX;
          }
        }
        mPreviousX = x;
        mPreviousY = y;
      } else if (event.getPointerCount() == 2) {
        float dx = event.getX(1) - event.getX(0);
        float dy = event.getY(1) - event.getY(0);
        float deg = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (event.getAction() != MotionEvent.ACTION_MOVE) {
          mPreviousDeg = deg;
          mPreviousX = event.getX();
          mPreviousY = event.getY();
          return true;
        }
        float ddeg = deg - mPreviousDeg;
        mRenderer.mDeltaZ -= ddeg;
        mPreviousDeg = deg;
      }
      requestRender();
    }
    return true;
  }

  private class CubeRenderer implements Renderer {
    volatile public float mDeltaX, mDeltaY, mDeltaZ;
    int iProgId;
    int iPosition;
    int iVPMatrix;
    int iTexId;
    int iTexLoc;
    int iTexCoords;
    float[] m_fProjMatrix = new float[16];
    float[] m_fViewMatrix = new float[16];
    float[] m_fIdentity = new float[16];
    float[] m_fVPMatrix = new float[16];
    /** Store the accumulated rotation. */
    private float[] mAccumulatedRotation = new float[16];
    /** Store the current rotation. */
    private float[] mCurrentRotation = new float[16];
    /** A temporary matrix */
    private float[] mTemporaryMatrix = new float[16];
    float[] cube = {
        2, 2, 2, -2, 2, 2, -2, -2, 2, 2, -2, 2, //0-1-2-3 front
        2, 2, 2, 2, -2, 2, 2, -2, -2, 2, 2, -2,//0-3-4-5 right
        2, -2, -2, -2, -2, -2, -2, 2, -2, 2, 2, -2,//4-7-6-5 back
        -2, 2, 2, -2, 2, -2, -2, -2, -2, -2, -2, 2,//1-6-7-2 left
        2, 2, 2, 2, 2, -2, -2, 2, -2, -2, 2, 2, //top
        2, -2, 2, -2, -2, 2, -2, -2, -2, 2, -2, -2,//bottom
    };
    short[] indeces = {
        0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7, 8, 9, 10, 8, 10, 11, 12, 13, 14, 12, 14, 15, 16, 17, 18,
        16, 18, 19, 20, 21, 22, 20, 22, 23,
    };
    float[] tex = {
        1, 1, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1, //0-1-2-3 front
        1, 1, 1, 1, -1, 1, 1, -1, -1, 1, 1, -1,//0-3-4-5 right
        1, -1, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1,//4-7-6-5 back
        -1, 1, 1, -1, 1, -1, -1, -1, -1, -1, -1, 1,//1-6-7-2 left
        1, 1, 1, 1, 1, -1, -1, 1, -1, -1, 1, 1, //top
        1, -1, 1, -1, -1, 1, -1, -1, -1, 1, -1, -1,//bottom
    };
    final String strVShader = "attribute vec4 a_position;"
        + "attribute vec4 a_color;"
        + "attribute vec3 a_normal;"
        + "uniform mat4 u_VPMatrix;"
        + "uniform vec3 u_LightPos;"
        + "varying vec3 v_texCoords;"
        + "attribute vec3 a_texCoords;"
        + "void main()"
        + "{"
        + "v_texCoords = a_texCoords;"
        + "gl_Position = u_VPMatrix * a_position;"
        + "}";
    final String strFShader = "precision mediump float;"
        + "uniform samplerCube u_texId;"
        + "varying vec3 v_texCoords;"
        + "void main()"
        + "{"
        + "gl_FragColor = textureCube(u_texId, v_texCoords);"
        + "}";
    FloatBuffer cubeBuffer = null;
    ShortBuffer indexBuffer = null;
    FloatBuffer texBuffer = null;

    public CubeRenderer() {
      cubeBuffer =
          ByteBuffer.allocateDirect(cube.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
      cubeBuffer.put(cube).position(0);
      indexBuffer = ByteBuffer.allocateDirect(indeces.length * 4)
          .order(ByteOrder.nativeOrder())
          .asShortBuffer();
      indexBuffer.put(indeces).position(0);
      texBuffer =
          ByteBuffer.allocateDirect(tex.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
      texBuffer.put(tex).position(0);
    }

    public void onDrawFrame(GL10 arg0) {
      synchronized (sync) {
        drawFrame(previewWidth, previewHeight); //draw in preview
        //draw in encoder
        if (surfaceManager != null) {
          saveRenderState();
          surfaceManager.makeCurrent();
          drawFrame(encoderWidth, encoderHeight);
          surfaceManager.setPresentationTime(getDrawingTime());
          surfaceManager.swapBuffer();
          restoreRenderState();
        }
        surfaceTexture.updateTexImage();
      }
    }

    //save state of main opengl context
    private void saveRenderState() {
      savedEglDisplay = EGL14.eglGetCurrentDisplay();
      savedEglDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
      savedEglReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
      savedEglContext = EGL14.eglGetCurrentContext();
    }

    //restore state of main opengl context
    private void restoreRenderState() {
      if (!EGL14.eglMakeCurrent(savedEglDisplay, savedEglDrawSurface, savedEglReadSurface,
          savedEglContext)) {
        throw new RuntimeException("eglMakeCurrent failed");
      }
    }

    private void drawFrame(int width, int height) {
      GLES20.glViewport(0, 0, width, height);
      Matrix.frustumM(m_fProjMatrix, 0, -(float) width / height, (float) width / height, -1, 1, 1,
          10);
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
      GLES20.glUseProgram(iProgId);
      cubeBuffer.position(0);
      GLES20.glVertexAttribPointer(iPosition, 3, GLES20.GL_FLOAT, false, 0, cubeBuffer);
      GLES20.glEnableVertexAttribArray(iPosition);
      texBuffer.position(0);
      GLES20.glVertexAttribPointer(iTexCoords, 3, GLES20.GL_FLOAT, false, 0, texBuffer);
      GLES20.glEnableVertexAttribArray(iTexCoords);
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_CUBE_MAP, iTexId);
      GLES20.glUniform1i(iTexLoc, 0);
      // Draw a cube.
      // Translate the cube into the screen.
      Matrix.setIdentityM(m_fIdentity, 0);
      //              Matrix.translateM(m_fIdentity, 0, 0.0f, 0.8f, -3.5f);
      // Set a matrix that contains the current rotation.
      Matrix.setIdentityM(mCurrentRotation, 0);
      Matrix.rotateM(mCurrentRotation, 0, mDeltaX, 1.0f, 0.0f, 0.0f);
      Matrix.rotateM(mCurrentRotation, 0, mDeltaY, 0.0f, 1.0f, 0.0f);
      Matrix.rotateM(mCurrentRotation, 0, mDeltaZ, 0.0f, 0.0f, 1.0f);
      mDeltaX = 0.0f;
      mDeltaY = 0.0f;
      mDeltaZ = 0.0f;
      // Multiply the current rotation by the accumulated rotation, and then set the accumulated
      // rotation to the result.
      Matrix.multiplyMM(mTemporaryMatrix, 0, mCurrentRotation, 0, mAccumulatedRotation, 0);
      System.arraycopy(mTemporaryMatrix, 0, mAccumulatedRotation, 0, 16);
      // Rotate the cube taking the overall rotation into account.
      Matrix.multiplyMM(mTemporaryMatrix, 0, m_fIdentity, 0, mAccumulatedRotation, 0);
      System.arraycopy(mTemporaryMatrix, 0, m_fIdentity, 0, 16);
      Matrix.multiplyMM(m_fVPMatrix, 0, m_fViewMatrix, 0, m_fIdentity, 0);
      Matrix.multiplyMM(m_fVPMatrix, 0, m_fProjMatrix, 0, m_fVPMatrix, 0);
      //              Matrix.translateM(m_fVPMatrix, 0, 0, 0, 1);
      GLES20.glUniformMatrix4fv(iVPMatrix, 1, false, m_fVPMatrix, 0);
      GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_SHORT, indexBuffer);
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
      GLES20.glClearColor(0, 0, 0, 0);
      GLES20.glEnable(GLES20.GL_DEPTH_TEST);
      GLES20.glDepthFunc(GLES20.GL_LEQUAL);
      GLES20.glFrontFace(GLES20.GL_CCW);
      GLES20.glEnable(GLES20.GL_CULL_FACE);
      GLES20.glCullFace(GLES20.GL_BACK);
      GLES20.glEnable(GLES20.GL_BLEND);
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
      Matrix.setLookAtM(m_fViewMatrix, 0, 0, 0, 6, 0, 0, 0, 0, 1, 0);
      Matrix.setIdentityM(mAccumulatedRotation, 0);
      iProgId = GlUtil.createProgram(strVShader, strFShader);
      iPosition = GLES20.glGetAttribLocation(iProgId, "a_position");
      iVPMatrix = GLES20.glGetUniformLocation(iProgId, "u_VPMatrix");
      iTexLoc = GLES20.glGetUniformLocation(iProgId, "u_texId");
      iTexCoords = GLES20.glGetAttribLocation(iProgId, "a_texCoords");
      iTexId = createCubeTexture();
      surfaceTexture = new SurfaceTexture(iTexId);
      surfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
          requestRender();
        }
      });
      eglContext = EGL14.eglGetCurrentContext();
    }

    public void onSurfaceChanged(GL10 arg0, int width, int height) {
      previewWidth = width;
      previewHeight = height;
    }

    public int createCubeTexture() {
      int[] textureId = new int[1];
      // Face 0 - Red
      byte[] cubePixels0 = { 127, 0, 0 };
      // Face 1 - Green
      byte[] cubePixels1 = { 0, 127, 0 };
      // Face 2 - Blue
      byte[] cubePixels2 = { 0, 0, 127 };
      // Face 3 - Yellow
      byte[] cubePixels3 = { 127, 127, 0 };
      // Face 4 - Purple
      byte[] cubePixels4 = { 127, 0, 127 };
      // Face 5 - White
      byte[] cubePixels5 = { 127, 127, 127 };
      ByteBuffer cubePixels = ByteBuffer.allocateDirect(3);
      // Generate a texture object
      GLES20.glGenTextures(1, textureId, 0);
      // Bind the texture object
      GLES20.glBindTexture(GLES20.GL_TEXTURE_CUBE_MAP, textureId[0]);
      // Load the cube face - Positive X
      cubePixels.put(cubePixels0).position(0);
      GLES20.glTexImage2D(GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_X, 0, GLES20.GL_RGB, 1, 1, 0,
          GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, cubePixels);
      // Load the cube face - Negative X
      cubePixels.put(cubePixels1).position(0);
      GLES20.glTexImage2D(GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_X, 0, GLES20.GL_RGB, 1, 1, 0,
          GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, cubePixels);
      // Load the cube face - Positive Y
      cubePixels.put(cubePixels2).position(0);
      GLES20.glTexImage2D(GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_Y, 0, GLES20.GL_RGB, 1, 1, 0,
          GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, cubePixels);
      // Load the cube face - Negative Y
      cubePixels.put(cubePixels3).position(0);
      GLES20.glTexImage2D(GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y, 0, GLES20.GL_RGB, 1, 1, 0,
          GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, cubePixels);
      // Load the cube face - Positive Z
      cubePixels.put(cubePixels4).position(0);
      GLES20.glTexImage2D(GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_Z, 0, GLES20.GL_RGB, 1, 1, 0,
          GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, cubePixels);
      // Load the cube face - Negative Z
      cubePixels.put(cubePixels5).position(0);
      GLES20.glTexImage2D(GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z, 0, GLES20.GL_RGB, 1, 1, 0,
          GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, cubePixels);
      // Set the filtering mode
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_MIN_FILTER,
          GLES20.GL_LINEAR);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_MAG_FILTER,
          GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_WRAP_S,
          GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_WRAP_T,
          GLES20.GL_CLAMP_TO_EDGE);
      return textureId[0];
    }
  }
}
