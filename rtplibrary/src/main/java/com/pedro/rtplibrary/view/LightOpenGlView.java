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
import com.pedro.encoder.input.gl.render.SimpleCameraRender;
import java.util.concurrent.Semaphore;

/**
 * Created by pedro on 21/02/18.
 * Light version of OpenGlView for devices too slow.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class LightOpenGlView extends SurfaceView
    implements Runnable, SurfaceTexture.OnFrameAvailableListener, SurfaceHolder.Callback {

  public final static String TAG = "OpenGlView";

  private Thread thread = null;
  private boolean frameAvailable = false;
  private boolean running = false;
  private boolean initialized = false;

  private SurfaceManager surfaceManager = null;
  private SurfaceManager surfaceManagerEncoder = null;

  private SimpleCameraRender simpleCameraRender = null;

  private final Semaphore semaphore = new Semaphore(0);
  private final Object sync = new Object();
  private int previewWidth, previewHeight;
  private int encoderWidth, encoderHeight;
  private int rotatedPreviewWidth, rotatedPreviewHeight;
  private int rotatedEncoderWidth, rotatedEncoderHeight;
  private boolean isCamera2Landscape = false;
  private int waitTime = 200;
  private static boolean rotate = false;
  private OnStartResolution onRotateResolution;

  public LightOpenGlView(Context context, AttributeSet attrs) {
    super(context, attrs);
    getHolder().addCallback(this);
  }

  public void init() {
    if (!initialized) simpleCameraRender = new SimpleCameraRender();
    initialized = true;
  }

  public void rotated() {
    rotate = !rotate;
  }

  public SurfaceTexture getSurfaceTexture() {
    return simpleCameraRender.getSurfaceTexture();
  }

  public Surface getSurface() {
    return simpleCameraRender.getSurface();
  }

  public void addMediaCodecSurface(Surface surface) {
    synchronized (sync) {
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
  public void run() {
    surfaceManager = new SurfaceManager(getHolder().getSurface());
    surfaceManager.makeCurrent();
    simpleCameraRender.isCamera2LandScape(isCamera2Landscape);
    simpleCameraRender.initGl(getContext());
    if (onRotateResolution != null) onRotateResolution.onStartChangeResolution();
    simpleCameraRender.getSurfaceTexture().setOnFrameAvailableListener(this);
    semaphore.release();
    try {
      while (running) {
        synchronized (sync) {
          sync.wait(waitTime);
          if (frameAvailable) {
            frameAvailable = false;
            surfaceManager.makeCurrent();

            simpleCameraRender.updateFrame();
            if (rotate) simpleCameraRender.drawFrame(rotatedPreviewWidth, rotatedPreviewHeight);
            else simpleCameraRender.drawFrame(previewWidth, previewHeight);
            surfaceManager.swapBuffer();

            synchronized (sync) {
              if (surfaceManagerEncoder != null) {
                surfaceManagerEncoder.makeCurrent();
                if (rotate) simpleCameraRender.drawFrame(rotatedEncoderWidth, rotatedEncoderHeight);
                else simpleCameraRender.drawFrame(encoderWidth, encoderHeight);
                long ts = simpleCameraRender.getSurfaceTexture().getTimestamp();
                surfaceManagerEncoder.setPresentationTime(ts);
                surfaceManagerEncoder.swapBuffer();
              }
            }
          }
        }
      }
    } catch (InterruptedException ignore) {
    } finally {
      surfaceManager.release();
      simpleCameraRender.release();
    }
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
