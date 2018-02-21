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
  private boolean running = true;

  private SurfaceManager surfaceManager = null;
  private SurfaceManager surfaceManagerEncoder = null;

  private SimpleCameraRender simpleCameraRender = null;

  private final Semaphore semaphore = new Semaphore(0);
  private final Object sync = new Object();
  private int previewWidth, previewHeight;
  private int encoderWidth, encoderHeight;
  private boolean isCamera2Landscape = false;

  public LightOpenGlView(Context context, AttributeSet attrs) {
    super(context, attrs);
    getHolder().addCallback(this);
  }

  public void init() {
    simpleCameraRender = new SimpleCameraRender();
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

  public void setEncoderSize(int width, int height) {
    this.encoderWidth = width;
    this.encoderHeight = height;
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
    simpleCameraRender.getSurfaceTexture().setOnFrameAvailableListener(this);
    semaphore.release();
    try {
      while (running) {
        synchronized (sync) {
          sync.wait(200);
          if (frameAvailable) {
            frameAvailable = false;
            surfaceManager.makeCurrent();

            simpleCameraRender.updateFrame();
            simpleCameraRender.drawFrame(previewWidth, previewHeight);
            surfaceManager.swapBuffer();

            synchronized (sync) {
              if (surfaceManagerEncoder != null) {
                surfaceManagerEncoder.makeCurrent();
                simpleCameraRender.drawFrame(encoderWidth, encoderHeight);
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
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    stopGlThread();
  }
}
