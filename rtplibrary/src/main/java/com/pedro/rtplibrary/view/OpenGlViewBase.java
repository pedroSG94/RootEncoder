package com.pedro.rtplibrary.view;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.pedro.encoder.input.gl.SurfaceManager;
import java.util.concurrent.Semaphore;

/**
 * Created by pedro on 10/03/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public abstract class OpenGlViewBase extends SurfaceView
    implements Runnable, SurfaceTexture.OnFrameAvailableListener, SurfaceHolder.Callback{

  public final static String TAG = "OpenGlViewBase";

  protected Thread thread = null;
  protected boolean frameAvailable = false;
  protected boolean running = false;
  protected boolean initialized = false;

  protected SurfaceManager surfaceManager = null;
  protected SurfaceManager surfaceManagerEncoder = null;

  protected final Semaphore semaphore = new Semaphore(0);
  protected final Object sync = new Object();
  protected int previewWidth, previewHeight;
  protected int encoderWidth, encoderHeight;
  protected int rotatedPreviewWidth, rotatedPreviewHeight;
  protected int rotatedEncoderWidth, rotatedEncoderHeight;
  protected boolean isCamera2Landscape = false;
  protected int waitTime;
  protected static boolean rotate = false;
  protected Surface surface;
  protected OnStartResolution onRotateResolution;

  public OpenGlViewBase(Context context) {
    super(context);
    getHolder().addCallback(this);
  }

  public OpenGlViewBase(Context context, AttributeSet attrs) {
    super(context, attrs);
    getHolder().addCallback(this);
  }

  public abstract void init();

  public abstract SurfaceTexture getSurfaceTexture();

  public abstract Surface getSurface();

  public void rotated() {
    rotate = !rotate;
  }

  public void addMediaCodecSurface(Surface surface) {
    synchronized (sync) {
      this.surface = surface;
      surfaceManagerEncoder = new SurfaceManager(surface, surfaceManager);
    }
  }

  public void removeMediaCodecSurface() {
    synchronized (sync) {
      if (surfaceManagerEncoder != null) {
        surfaceManagerEncoder.release();
        surfaceManagerEncoder = null;
      }
    }
  }

  public void setWaitTime(int waitTime) {
    this.waitTime = waitTime;
  }

  public void onChangeResolution(OnStartResolution onRotateResolution) {
    this.onRotateResolution = onRotateResolution;
  }

  public void setPreviewResolution(int width, int height) {
    previewWidth = width;
    previewHeight = height;
  }

  public void setRotatedPreviewResolution(int width, int height) {
    rotatedPreviewWidth = width;
    rotatedPreviewHeight = height;
  }

  public void setEncoderResolution(int width, int height) {
    encoderWidth = width;
    encoderHeight = height;
  }

  public void setRotatedEncoderResolution(int width, int height) {
    rotatedEncoderWidth = width;
    rotatedEncoderHeight = height;
  }

  public void setEncoderSize(int width, int height) {
    this.encoderWidth = width;
    this.encoderHeight = height;
    this.rotatedEncoderWidth = width;
    this.rotatedEncoderHeight = height;
  }

  public void startGLThread(boolean isCamera2Landscape) {
    this.isCamera2Landscape = isCamera2Landscape;
    Log.i(TAG, "Thread started.");
    thread = new Thread(this);
    running = true;
    thread.start();
    semaphore.acquireUninterruptibly();
  }

  public void stopGlThread() {
    if (thread != null) {
      thread.interrupt();
      try {
        thread.join();
      } catch (InterruptedException e) {
        thread.interrupt();
      }
      thread = null;
    }
    running = false;
  }

  @Override
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    synchronized (sync) {
      frameAvailable = true;
      sync.notifyAll();
    }
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    Log.i(TAG, "size: " + width + "x" + height);
    this.previewWidth = width;
    this.previewHeight = height;
    this.rotatedPreviewWidth = width;
    this.rotatedPreviewHeight = height;
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    stopGlThread();
  }
}
