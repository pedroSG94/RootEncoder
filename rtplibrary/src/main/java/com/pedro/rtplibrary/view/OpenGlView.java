package com.pedro.rtplibrary.view;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.Surface;
import com.pedro.encoder.input.gl.SurfaceManager;
import com.pedro.encoder.input.gl.render.ManagerRender;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.utils.gl.GifStreamObject;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.TextStreamObject;
import com.pedro.encoder.utils.gl.TranslateTo;

/**
 * Created by pedro on 9/09/17.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class OpenGlView extends OpenGlViewBase {

  private ManagerRender managerRender = null;
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
  private boolean keepAspectRatio = false;

  public OpenGlView(Context context) {
    super(context);
  }

  public OpenGlView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void init() {
    if (!initialized) managerRender = new ManagerRender();
    waitTime = 10;
    initialized = true;
  }

  @Override
  public SurfaceTexture getSurfaceTexture() {
    return managerRender.getSurfaceTexture();
  }

  @Override
  public Surface getSurface() {
    return managerRender.getSurface();
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

  public boolean isKeepAspectRatio() {
    return keepAspectRatio;
  }

  public void setKeepAspectRatio(boolean keepAspectRatio) {
    this.keepAspectRatio = keepAspectRatio;
  }

  public boolean isAAEnabled() {
    return managerRender != null && managerRender.isAAEnabled();
  }

  public PointF getScale() {
    if (managerRender != null) {
      return managerRender.getScale();
    } else {
      return new PointF(0f, 0f);
    }
  }

  public PointF getPosition() {
    if (managerRender != null) {
      return managerRender.getPosition();
    } else {
      return new PointF(0f, 0f);
    }
  }

  @Override
  public void run() {
    surfaceManager = new SurfaceManager(getHolder().getSurface());
    surfaceManager.makeCurrent();
    managerRender.setStreamSize(encoderWidth, encoderHeight);
    managerRender.initGl(previewWidth, previewHeight, isCamera2Landscape, getContext());
    managerRender.getSurfaceTexture().setOnFrameAvailableListener(this);
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
                managerRender.setText(textStreamObject);
              } else if (imageStreamObject != null) {
                managerRender.setImage(imageStreamObject);
              } else if (gifStreamObject != null) {
                managerRender.setGif(gifStreamObject);
              } else {
                managerRender.clear();
              }
              if (surfaceManagerEncoder == null) loadStreamObject = false;
            }
            managerRender.updateFrame();
            managerRender.drawOffScreen();
            managerRender.drawScreen(previewWidth, previewHeight, keepAspectRatio);
            surfaceManager.swapBuffer();
            //stream object loaded but you need reset surfaceManagerEncoder
            synchronized (sync) {
              if (surfaceManagerEncoder != null) {
                if (loadStreamObject) {
                  surfaceManagerEncoder.release();
                  surfaceManagerEncoder = null;
                  addMediaCodecSurface(surface);
                  loadStreamObject = false;
                  continue;
                }
                surfaceManagerEncoder.makeCurrent();
                managerRender.drawScreen(encoderWidth, encoderHeight, false);
                long ts = managerRender.getSurfaceTexture().getTimestamp();
                surfaceManagerEncoder.setPresentationTime(ts);
                surfaceManagerEncoder.swapBuffer();
              }
            }
          }
          //set new parameters
          if (loadAlpha) {
            managerRender.setAlpha(alpha);
            loadAlpha = false;
          } else if (loadScale) {
            managerRender.setScale(scaleX, scaleY);
            loadScale = false;
          } else if (loadPosition) {
            managerRender.setPosition(positionX, positionY);
            loadPosition = false;
          } else if (loadPositionTo) {
            managerRender.setPosition(positionTo);
            loadPositionTo = false;
          } else if (loadFilter) {
            managerRender.setFilter(baseFilterRender);
            loadFilter = false;
          } else if (loadAA) {
            managerRender.enableAA(AAEnabled);
            loadAA = false;
          } else if (onChangeFace) {
            managerRender.faceChanged(isFrontCamera);
            onChangeFace = false;
          }
        }
      }
    } catch (InterruptedException ignore) {
    } finally {
      surfaceManager.release();
      managerRender.release();
    }
  }
}