package com.pedro.rtplibrary.view;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.Surface;
import com.pedro.encoder.input.gl.SurfaceManager;
import com.pedro.encoder.input.gl.render.ManagerRender;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.utils.gl.GlUtil;

/**
 * Created by pedro on 9/09/17.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class OpenGlView extends OpenGlViewBase {

  private ManagerRender managerRender = null;
  private boolean loadAA = false;

  private boolean AAEnabled = false;
  private boolean keepAspectRatio = false;
  private boolean isFrontPreviewFlip = false;

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

  @Override
  public void setFilter(int filterPosition, BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(filterPosition, baseFilterRender));
  }

  @Override
  public void setFilter(BaseFilterRender baseFilterRender) {
    setFilter(0, baseFilterRender);
  }

  @Override
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

  public boolean isFrontPreviewFlip() {
    return isFrontPreviewFlip;
  }

  public void setFrontPreviewFlip(boolean frontPreviewFlip) {
    isFrontPreviewFlip = frontPreviewFlip;
  }

  @Override
  public boolean isAAEnabled() {
    return managerRender != null && managerRender.isAAEnabled();
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
            managerRender.updateFrame();
            managerRender.drawOffScreen();
            managerRender.drawScreen(previewWidth, previewHeight, keepAspectRatio,
                isFrontPreviewFlip);
            surfaceManager.swapBuffer();
            if (takePhotoCallback != null) {
              takePhotoCallback.onTakePhoto(
                  GlUtil.getBitmap(previewWidth, previewHeight, encoderWidth, encoderHeight));
              takePhotoCallback = null;
            }
            //stream object loaded but you need reset surfaceManagerEncoder
            synchronized (sync) {
              if (surfaceManagerEncoder != null) {
                surfaceManagerEncoder.makeCurrent();
                managerRender.drawScreen(encoderWidth, encoderHeight, false, false);
                long ts = managerRender.getSurfaceTexture().getTimestamp();
                surfaceManagerEncoder.setPresentationTime(ts);
                surfaceManagerEncoder.swapBuffer();
              }
            }
          }
          if (!filterQueue.isEmpty()) {
            Filter filter = filterQueue.take();
            managerRender.setFilter(filter.getPosition(), filter.getBaseFilterRender());
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