package com.pedro.rtplibrary.view;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.pedro.encoder.input.gl.SurfaceManager;
import com.pedro.encoder.input.gl.TextureManagerWatermark;
import com.pedro.encoder.utils.gl.TextStreamObject;
import com.pedro.encoder.utils.gl.GifStreamObject;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import java.util.concurrent.Semaphore;

/**
 * Created by pedro on 9/09/17.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class OpenGlView extends SurfaceView
    implements Runnable, OnFrameAvailableListener, SurfaceHolder.Callback {

  public final static String TAG = "OpenGlView";

  private Thread thread = null;
  private boolean frameAvailable = false;
  private boolean running = true;

  private SurfaceManager surfaceManager = null;
  private SurfaceManager surfaceManagerEncoder = null;

  private TextureManagerWatermark textureManager = null;

  private final Semaphore semaphore = new Semaphore(0);
  private final Object sync = new Object();
  private int previewWidth, previewHeight;
  private int encoderWidth, encoderHeight;
  private Surface surface;

  public OpenGlView(Context context, AttributeSet attrs) {
    super(context, attrs);
    getHolder().addCallback(this);
  }

  public SurfaceTexture getSurfaceTexture() {
    return textureManager.getSurfaceTexture();
  }

  public Surface getSurface() {
    return textureManager.getSurface();
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

  public void setEncoderSize(int width, int height) {
    this.encoderWidth = width;
    this.encoderHeight = height;
  }

  public void setGif(GifStreamObject gifStreamObject) {
    textureManager.setGif(gifStreamObject);
  }

  public void setImage(ImageStreamObject imageStreamObject) {
    textureManager.setImage(imageStreamObject);
  }

  public void setText(TextStreamObject textStreamObject) {
    textureManager.setText(textStreamObject);
  }

  public void startGLThread() {
    Log.i(TAG, "Thread started.");
    if (textureManager == null) {
      textureManager = new TextureManagerWatermark(getContext());
    }
    if (textureManager.getSurfaceTexture() == null) {
      thread = new Thread(OpenGlView.this);
      running = true;
      thread.start();
      semaphore.acquireUninterruptibly();
    }
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
    textureManager.initGl();
    textureManager.getSurfaceTexture().setOnFrameAvailableListener(this);
    semaphore.release();
    try {
      while (running) {
        synchronized (sync) {
          sync.wait(2500);
          if (frameAvailable) {
            frameAvailable = false;

            surfaceManager.makeCurrent();
            textureManager.updateFrame();
            textureManager.drawFrame(previewWidth, previewHeight);
            surfaceManager.swapBuffer();

            if (surfaceManagerEncoder != null) {
              surfaceManagerEncoder.makeCurrent();
              textureManager.drawFrame(encoderWidth, encoderHeight);
              long ts = textureManager.getSurfaceTexture().getTimestamp();
              surfaceManagerEncoder.setPresentationTime(ts);
              surfaceManagerEncoder.swapBuffer();
            }
          } else {
            Log.e(TAG, "No frame received !");
          }
        }
      }
    } catch (InterruptedException ignore) {
    } finally {
      surfaceManager.release();
      textureManager.release();
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