package com.pedro.rtplibrary.view;

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
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.TextStreamObject;
import com.pedro.encoder.utils.gl.TranslateTo;
import java.util.concurrent.Semaphore;

/**
 * Created by pedro on 4/03/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class OffScreenGlThread
    implements GlInterface, Runnable, SurfaceTexture.OnFrameAvailableListener {

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
  //used with camera
  private boolean isCamera2Landscape = false;
  protected boolean onChangeFace = false;
  protected boolean isFrontCamera = false;
  private TakePhotoCallback takePhotoCallback;

  public OffScreenGlThread(Context context) {
    this.context = context;
  }

  @Override
  public void init() {
    if (!initialized) textureManager = new ManagerRender();
    initialized = true;
  }

  @Override
  public void setEncoderSize(int width, int height) {
    this.encoderWidth = width;
    this.encoderHeight = height;
  }

  @Override
  public SurfaceTexture getSurfaceTexture() {
    return textureManager.getSurfaceTexture();
  }

  @Override
  public Surface getSurface() {
    return textureManager.getSurface();
  }

  @Override
  public void addMediaCodecSurface(Surface surface) {
    synchronized (sync) {
      this.surface = surface;
      surfaceManagerEncoder = new SurfaceManager(surface, surfaceManager);
    }
  }

  @Override
  public void removeMediaCodecSurface() {
    synchronized (sync) {
      if (surfaceManagerEncoder != null) {
        surfaceManagerEncoder.release();
        surfaceManagerEncoder = null;
      }
    }
  }

  @Override
  public void takePhoto(TakePhotoCallback takePhotoCallback) {
    this.takePhotoCallback = takePhotoCallback;
  }

  @Override
  public void setFilter(BaseFilterRender baseFilterRender) {
    loadFilter = true;
    this.baseFilterRender = baseFilterRender;
  }

  @Override
  public void setGif(GifStreamObject gifStreamObject) {
    this.gifStreamObject = gifStreamObject;
    this.imageStreamObject = null;
    this.textStreamObject = null;
    loadStreamObject = true;
  }

  @Override
  public void setImage(ImageStreamObject imageStreamObject) {
    this.imageStreamObject = imageStreamObject;
    this.gifStreamObject = null;
    this.textStreamObject = null;
    loadStreamObject = true;
  }

  @Override
  public void setText(TextStreamObject textStreamObject) {
    this.textStreamObject = textStreamObject;
    this.gifStreamObject = null;
    this.imageStreamObject = null;
    loadStreamObject = true;
  }

  @Override
  public void clear() {
    this.textStreamObject = null;
    this.gifStreamObject = null;
    this.imageStreamObject = null;
    loadStreamObject = true;
  }

  @Override
  public void setStreamObjectAlpha(float alpha) {
    this.alpha = alpha;
    loadAlpha = true;
  }

  @Override
  public void setStreamObjectSize(float sizeX, float sizeY) {
    this.scaleX = sizeX;
    this.scaleY = sizeY;
    loadScale = true;
  }

  @Override
  public void setStreamObjectPosition(float x, float y) {
    this.positionX = x;
    this.positionY = y;
    loadPosition = true;
  }

  @Override
  public void setStreamObjectPosition(TranslateTo translateTo) {
    this.positionTo = translateTo;
    loadPositionTo = true;
  }

  @Override
  public void enableAA(boolean AAEnabled) {
    this.AAEnabled = AAEnabled;
    loadAA = true;
  }

  @Override
  public void setCameraFace(boolean frontCamera) {
    onChangeFace = true;
    isFrontCamera = frontCamera;
  }

  @Override
  public boolean isAAEnabled() {
    return textureManager != null && textureManager.isAAEnabled();
  }

  @Override
  public void setWaitTime(int waitTime) {
    this.waitTime = waitTime;
  }

  @Override
  public PointF getScale() {
    if (textureManager != null) {
      return textureManager.getScale();
    } else {
      return new PointF(0f, 0f);
    }
  }

  @Override
  public PointF getPosition() {
    if (textureManager != null) {
      return textureManager.getPosition();
    } else {
      return new PointF(0f, 0f);
    }
  }

  @Override
  public void start(boolean isCamera2Landscape) {
    this.isCamera2Landscape = isCamera2Landscape;
    thread = new Thread(this);
    running = true;
    thread.start();
    semaphore.acquireUninterruptibly();
  }

  @Override
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
    textureManager.initGl(encoderWidth, encoderHeight, isCamera2Landscape, context);
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
            textureManager.drawScreen(encoderWidth, encoderHeight, false, false);
            surfaceManager.swapBuffer();
            if (takePhotoCallback != null) {
              takePhotoCallback.onTakePhoto(
                  GlUtil.getBitmap(encoderWidth, encoderHeight, encoderWidth, encoderHeight));
              takePhotoCallback = null;
            }
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
                textureManager.drawScreen(encoderWidth, encoderHeight, false, false);
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
          } else if (onChangeFace) {
            textureManager.faceChanged(isFrontCamera);
            onChangeFace = false;
          }
        }
      }
    } catch (InterruptedException ignore) {
      Thread.currentThread().interrupt();
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
