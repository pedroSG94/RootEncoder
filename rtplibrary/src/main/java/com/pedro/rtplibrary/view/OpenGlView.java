package com.pedro.rtplibrary.view;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.pedro.encoder.input.gl.GlWatermarkRenderer;
import com.pedro.encoder.input.gl.SurfaceManager;
import com.pedro.encoder.utils.gl.GifStreamObject;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.TextStreamObject;
import com.pedro.encoder.utils.gl.TranslateTo;
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

  private GlWatermarkRenderer textureManager = null;

  private final Semaphore semaphore = new Semaphore(0);
  private final Object sync = new Object();
  private int previewWidth, previewHeight;
  private int encoderWidth, encoderHeight;
  private boolean loadStreamObject = false;
  private boolean loadAlpha = false;
  private boolean loadScale = false;
  private boolean loadPosition = false;
  private boolean loadPositionTo = false;

  private TextStreamObject textStreamObject;
  private ImageStreamObject imageStreamObject;
  private GifStreamObject gifStreamObject;
  private float alpha;
  private float scaleX, scaleY;
  private float positionX, positionY;
  private TranslateTo positionTo;
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
      textureManager.setStreamSize(encoderWidth, encoderHeight);
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
    this.gifStreamObject = gifStreamObject;
    this.imageStreamObject = null;
    this.textStreamObject = null;
    loadStreamObject = true;
  }

  public void setImage(ImageStreamObject imageStreamObject) {
    this.imageStreamObject = imageStreamObject;
    this.gifStreamObject = null;
    this.textStreamObject = null;
    loadStreamObject = true;
  }

  public void setText(TextStreamObject textStreamObject) {
    this.textStreamObject = textStreamObject;
    this.gifStreamObject = null;
    this.imageStreamObject = null;
    loadStreamObject = true;
  }

  public void clear() {
    this.textStreamObject = null;
    this.gifStreamObject = null;
    this.imageStreamObject = null;
    loadStreamObject = true;
  }

  public void setStreamObjectAlpha(float alpha) {
    this.alpha = alpha;
    loadAlpha = true;
  }

  public void setStreamObjectSize(float sizeX, float sizeY) {
    this.scaleX = sizeX;
    this.scaleY = sizeY;
    loadScale = true;
  }

  public void setStreamObjectPosition(float x, float y) {
    this.positionX = x;
    this.positionY = y;
    loadPosition = true;
  }

  public void setStreamObjectPosition(TranslateTo translateTo) {
    this.positionTo = translateTo;
    loadPositionTo = true;
  }

  public PointF getScale() {
    if (textureManager != null) return textureManager.getScale();
    else return new PointF(0f, 0f);
  }

  public PointF getPosition() {
    if (textureManager != null) return textureManager.getPosition();
    else return new PointF(0f, 0f);
  }

  public void startGLThread() {
    Log.i(TAG, "Thread started.");
    if (textureManager == null) {
      textureManager = new GlWatermarkRenderer(getContext());
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
          sync.wait(500);
          if (frameAvailable) {
            frameAvailable = false;
            surfaceManager.makeCurrent();

            //need load a stream object
            if (loadStreamObject) {
              if (textStreamObject != null) {
                textureManager.setText(textStreamObject);
              } else if (imageStreamObject != null) {
                textureManager.setImage(imageStreamObject);
              } else if (gifStreamObject != null) {
                textureManager.setGif(gifStreamObject);
              } else {
                textureManager.clear();
              }
            }
            textureManager.updateFrame();
            textureManager.drawFrame(previewWidth, previewHeight);
            surfaceManager.swapBuffer();
            //stream object loaded but you need reset surfaceManagerEncoder
            if (loadStreamObject) {
              surfaceManagerEncoder.release();
              surfaceManagerEncoder = null;
              addMediaCodecSurface(surface);
              loadStreamObject = false;
              continue;
            }
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
          //set new parameters
          if (loadAlpha) {
            textureManager.setAlpha(alpha);
            loadAlpha = false;
          }

          if (loadScale) {
            textureManager.setScale(scaleX, scaleY);
            loadScale = false;
          }

          if (loadPosition) {
            textureManager.setPosition(positionX, positionY);
            loadPosition = false;
          }

          if (loadPositionTo) {
            textureManager.setPosition(positionTo);
            loadPositionTo = false;
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