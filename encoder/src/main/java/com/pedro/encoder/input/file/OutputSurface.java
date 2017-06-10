package com.pedro.encoder.input.file;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.view.Surface;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * Holds state associated with a Surface used for MediaCodec decoder output.
 * <p>
 * The constructor for this class will prepare GL, create a SurfaceTexture,
 * and then create a Surface for that SurfaceTexture.  The Surface can be passed to
 * MediaCodec.configure() to receive decoder output.  When a frame arrives, we latch the
 * texture with updateTexImage(), then render the texture with GL to a pbuffer.
 * <p>
 * By default, the Surface will be using a BufferQueue in asynchronous mode, so we
 * can potentially drop frames.
 */

/**
 * Created by pedro on 9/06/17.
 */
public class OutputSurface implements SurfaceTexture.OnFrameAvailableListener {
  private TextureRender mTextureRender;
  private SurfaceTexture mSurfaceTexture;
  private Surface mSurface;
  private EGL10 mEgl;

  private EGLDisplay mEGLDisplay = EGL10.EGL_NO_DISPLAY;
  private EGLContext mEGLContext = EGL10.EGL_NO_CONTEXT;
  private EGLSurface mEGLSurface = EGL10.EGL_NO_SURFACE;
  int mWidth;
  int mHeight;

  private final Object mFrameSyncObject = new Object();     // guards mFrameAvailable
  private boolean mFrameAvailable;

  private ByteBuffer mPixelBuf;                       // used by saveFrame()

  /**
   * Creates a CodecOutputSurface backed by a pbuffer with the specified dimensions.  The
   * new EGL context and surface will be made current.  Creates a Surface that can be passed
   * to MediaCodec.configure().
   */
  public OutputSurface(int width, int height) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException();
    }
    mEgl = (EGL10) EGLContext.getEGL();
    mWidth = width;
    mHeight = height;

    eglSetup();
    makeCurrent();
    setup();
  }

  /**
   * Creates interconnected instances of TextureRender, SurfaceTexture, and Surface.
   */
  private void setup() {
    mTextureRender = new TextureRender();
    mTextureRender.surfaceCreated();

    mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());

    // This doesn't work if this object is created on the thread that CTS started for
    // these test cases.
    //
    // The CTS-created thread has a Looper, and the SurfaceTexture constructor will
    // create a Handler that uses it.  The "frame available" message is delivered
    // there, but since we're not a Looper-based thread we'll never see it.  For
    // this to do anything useful, CodecOutputSurface must be created on a thread without
    // a Looper, so that SurfaceTexture uses the main application Looper instead.
    //
    // Java language note: passing "this" out of a constructor is generally unwise,
    // but we should be able to get away with it here.
    mSurfaceTexture.setOnFrameAvailableListener(this);

    mSurface = new Surface(mSurfaceTexture);

    mPixelBuf = ByteBuffer.allocateDirect(mWidth * mHeight * 4);
    mPixelBuf.order(ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Prepares EGL.  We want a GLES 2.0 context and a surface that supports pbuffer.
   */
  private void eglSetup() {
    final int EGL_OPENGL_ES2_BIT = 0x0004;
    final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    mEGLDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
    if (mEGLDisplay == EGL10.EGL_NO_DISPLAY) {
      throw new RuntimeException("unable to get EGL14 display");
    }
    int[] version = new int[2];
    if (!mEgl.eglInitialize(mEGLDisplay, version)) {
      mEGLDisplay = null;
      throw new RuntimeException("unable to initialize EGL14");
    }

    // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
    int[] attribList = {
        EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
        EGL10.EGL_ALPHA_SIZE, 8, EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT, EGL10.EGL_NONE
    };
    EGLConfig[] configs = new EGLConfig[1];
    int[] numConfigs = new int[1];
    if (!mEgl.eglChooseConfig(mEGLDisplay, attribList, configs, configs.length, numConfigs)) {
      throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
    }

    // Configure context for OpenGL ES 2.0.
    int[] attrib_list = {
        EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE
    };
    mEGLContext = mEgl.eglCreateContext(mEGLDisplay, configs[0], EGL10.EGL_NO_CONTEXT, attrib_list);
    checkEglError("eglCreateContext");
    if (mEGLContext == null) {
      throw new RuntimeException("null context");
    }

    // Create a pbuffer surface.
    int[] surfaceAttribs = {
        EGL10.EGL_WIDTH, mWidth, EGL10.EGL_HEIGHT, mHeight, EGL10.EGL_NONE
    };
    mEGLSurface = mEgl.eglCreatePbufferSurface(mEGLDisplay, configs[0], surfaceAttribs);
    checkEglError("eglCreatePbufferSurface");
    if (mEGLSurface == null) {
      throw new RuntimeException("surface was null");
    }
  }

  /**
   * Discard all resources held by this class, notably the EGL context.
   */
  public void release() {
    if (mEGLDisplay != EGL10.EGL_NO_DISPLAY) {
      mEgl.eglDestroySurface(mEGLDisplay, mEGLSurface);
      mEgl.eglDestroyContext(mEGLDisplay, mEGLContext);
      //mEgl.eglReleaseThread();
      mEgl.eglMakeCurrent(mEGLDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
          EGL10.EGL_NO_CONTEXT);
      mEgl.eglTerminate(mEGLDisplay);
    }
    mEGLDisplay = EGL10.EGL_NO_DISPLAY;
    mEGLContext = EGL10.EGL_NO_CONTEXT;
    mEGLSurface = EGL10.EGL_NO_SURFACE;

    mSurface.release();

    // this causes a bunch of warnings that appear harmless but might confuse someone:
    //  W BufferQueue: [unnamed-3997-2] cancelBuffer: BufferQueue has been abandoned!
    //mSurfaceTexture.release();

    mTextureRender = null;
    mSurface = null;
    mSurfaceTexture = null;
  }

  /**
   * Makes our EGL context and surface current.
   */
  public void makeCurrent() {
    if (!mEgl.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
      throw new RuntimeException("eglMakeCurrent failed");
    }
  }

  /**
   * Returns the Surface.
   */
  public Surface getSurface() {
    return mSurface;
  }

  /**
   * Latches the next buffer into the texture.  Must be called from the thread that created
   * the CodecOutputSurface object.  (More specifically, it must be called on the thread
   * with the EGLContext that contains the GL texture object used by SurfaceTexture.)
   */
  public void awaitNewImage() {
    final int TIMEOUT_MS = 2500;

    synchronized (mFrameSyncObject) {
      while (!mFrameAvailable) {
        try {
          // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
          // stalling the test if it doesn't arrive.
          mFrameSyncObject.wait(TIMEOUT_MS);
          if (!mFrameAvailable) {
            // TODO: if "spurious wakeup", continue while loop
            throw new RuntimeException("frame wait timed out");
          }
        } catch (InterruptedException ie) {
          // shouldn't happen
          throw new RuntimeException(ie);
        }
      }
      mFrameAvailable = false;
    }

    // Latch the data.
    mTextureRender.checkGlError("before updateTexImage");
    mSurfaceTexture.updateTexImage();
  }

  /**
   * Draws the data from SurfaceTexture onto the current EGL surface.
   *
   * @param invert if set, render the image with Y inverted (0,0 in top left)
   */
  public void drawImage(boolean invert) {
    mTextureRender.drawFrame(mSurfaceTexture, invert);
  }

  // SurfaceTexture callback
  @Override
  public void onFrameAvailable(SurfaceTexture st) {
    synchronized (mFrameSyncObject) {
      if (mFrameAvailable) {
        throw new RuntimeException("mFrameAvailable already set, frame could be dropped");
      }
      mFrameAvailable = true;
      mFrameSyncObject.notifyAll();
    }
  }

  /**
   * Saves the current frame to disk as a PNG image.
   */
  public void saveFrame(String filename) throws IOException {
    // glReadPixels gives us a ByteBuffer filled with what is essentially big-endian RGBA
    // data (i.e. a byte of red, followed by a byte of green...).  To use the Bitmap
    // constructor that takes an int[] array with pixel data, we need an int[] filled
    // with little-endian ARGB data.
    //
    // If we implement this as a series of buf.get() calls, we can spend 2.5 seconds just
    // copying data around for a 720p frame.  It's better to do a bulk get() and then
    // rearrange the data in memory.  (For comparison, the PNG compress takes about 500ms
    // for a trivial frame.)
    //
    // So... we set the ByteBuffer to little-endian, which should turn the bulk IntBuffer
    // get() into a straight memcpy on most Android devices.  Our ints will hold ABGR data.
    // Swapping B and R gives us ARGB.  We need about 30ms for the bulk get(), and another
    // 270ms for the color swap.
    //
    // We can avoid the costly B/R swap here if we do it in the fragment shader (see
    // http://stackoverflow.com/questions/21634450/ ).
    //
    // Having said all that... it turns out that the Bitmap#copyPixelsFromBuffer()
    // method wants RGBA pixels, not ARGB, so if we create an empty bitmap and then
    // copy pixel data in we can avoid the swap issue entirely, and just copy straight
    // into the Bitmap from the ByteBuffer.
    //
    // Making this even more interesting is the upside-down nature of GL, which means
    // our output will look upside-down relative to what appears on screen if the
    // typical GL conventions are used.  (For ExtractMpegFrameTest, we avoid the issue
    // by inverting the frame when we render it.)
    //
    // Allocating large buffers is expensive, so we really want mPixelBuf to be
    // allocated ahead of time if possible.  We still get some allocations from the
    // Bitmap / PNG creation.

    mPixelBuf.rewind();
    GLES20.glReadPixels(0, 0, mWidth, mHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mPixelBuf);

    BufferedOutputStream bos = null;
    try {
      bos = new BufferedOutputStream(new FileOutputStream(filename));
      Bitmap bmp = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
      mPixelBuf.rewind();
      bmp.copyPixelsFromBuffer(mPixelBuf);
      bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
      bmp.recycle();
    } finally {
      if (bos != null) bos.close();
    }
  }

  /**
   * Checks for EGL errors.
   */
  private void checkEglError(String msg) {
    int error;
    if ((error = mEgl.eglGetError()) != EGL10.EGL_SUCCESS) {
      throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
    }
  }
}
