package com.pedro.rtplibrary;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.Surface;
import com.pedro.encoder.input.gl.SurfaceManager;
import com.pedro.encoder.input.gl.render.ManagerRender;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.utils.gl.GifStreamObject;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.TextStreamObject;
import com.pedro.encoder.utils.gl.TranslateTo;
import java.util.concurrent.Semaphore;

/**
 * Created by pedro on 4/03/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class OffScreenGlThread implements Runnable, SurfaceTexture.OnFrameAvailableListener {

  private final Context context;
  private Thread thread = null;
  private boolean frameAvailable = false;
  private boolean running = true;
  private boolean initialized = false;

  private SurfaceManager surfaceManager = null;
  private SurfaceManager surfaceManagerEncoder = null;

  private ManagerRender textureManager = null;

  private final Semaphore semaphore = new Semaphore(0);
  private final Object sync = new Object();
  private int encoderWidth, encoderHeight;
  private boolean loadStreamObject = false;
  private boolean loadAlpha = false;
  private boolean loadScale = false;
  private boolean loadPosition = false;
  private boolean loadFilter = false;
  private boolean loadAA = false;

  private boolean loadPositionTo = false;
  private TextStreamObject textStreamObject;
  private ImageStreamObject imageStreamObject;
  private GifStreamObject gifStreamObject;
  private BaseFilterRender baseFilterRender;
  private float alpha;
  private float scaleX, scaleY;
  private float positionX, positionY;
  private boolean AAEnabled = false;
  private TranslateTo positionTo;
  private Surface surface;
  private int waitTime = 10;

  public OffScreenGlThread(Context context, int encoderWidth, int encoderHeight) {
    this.context = context;
    this.encoderWidth = encoderWidth;
    this.encoderHeight = encoderHeight;
  }

  public void init() {
    if (!initialized) textureManager = new ManagerRender();
    initialized = true;
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

  public void setFilter(BaseFilterRender baseFilterRender) {
    loadFilter = true;
    this.baseFilterRender = baseFilterRender;
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

  public void enableAA(boolean AAEnabled) {
    this.AAEnabled = AAEnabled;
    loadAA = true;
  }

  public boolean isAAEnabled() {
    return textureManager != null && textureManager.isAAEnabled();
  }

  public void setWaitTime(int waitTime) {
    this.waitTime = waitTime;
  }

  public PointF getScale() {
    if (textureManager != null) {
      return textureManager.getScale();
    } else {
      return new PointF(0f, 0f);
    }
  }

  public PointF getPosition() {
    if (textureManager != null) {
      return textureManager.getPosition();
    } else {
      return new PointF(0f, 0f);
    }
  }

  public void start() {
    thread = new Thread(this);
    running = true;
    thread.start();
    semaphore.acquireUninterruptibly();
  }

  public void stop() {
    if (thread != null) {
      thread.interrupt();
      try {
        thread.join(1000);
      } catch (InterruptedException e) {
        thread.interrupt();
      }
      thread = null;
    }
    running = false;
  }

  @Override
  public void run() {
    surfaceManager = new SurfaceManager();
    surfaceManager.makeCurrent();
    textureManager.setStreamSize(encoderWidth, encoderHeight);
    textureManager.initGl(encoderWidth, encoderHeight, false, context);
    textureManager.getSurfaceTexture().setOnFrameAvailableListener(this);
    semaphore.release();
    try {
      while (running) {
        synchronized (sync) {
          sync.wait(waitTime);
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
            textureManager.drawOffScreen();
            textureManager.drawScreen(encoderWidth, encoderHeight, false);
            surfaceManager.swapBuffer();
            //stream object loaded but you need reset surfaceManagerEncoder
            if (loadStreamObject) {
              surfaceManagerEncoder.release();
              surfaceManagerEncoder = null;
              addMediaCodecSurface(surface);
              loadStreamObject = false;
              continue;
            }
            synchronized (sync) {
              if (surfaceManagerEncoder != null) {
                surfaceManagerEncoder.makeCurrent();
                textureManager.drawScreen(encoderWidth, encoderHeight, false);
                long ts = textureManager.getSurfaceTexture().getTimestamp();
                surfaceManagerEncoder.setPresentationTime(ts);
                surfaceManagerEncoder.swapBuffer();
              }
            }
          }
          //set new parameters
          if (loadAlpha) {
            textureManager.setAlpha(alpha);
            loadAlpha = false;
          } else if (loadScale) {
            textureManager.setScale(scaleX, scaleY);
            loadScale = false;
          } else if (loadPosition) {
            textureManager.setPosition(positionX, positionY);
            loadPosition = false;
          } else if (loadPositionTo) {
            textureManager.setPosition(positionTo);
            loadPositionTo = false;
          } else if (loadFilter) {
            textureManager.setFilter(baseFilterRender);
            loadFilter = false;
          } else if (loadAA) {
            textureManager.enableAA(AAEnabled);
            loadAA = false;
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
}
