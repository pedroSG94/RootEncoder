package com.pedro.rtplibrary.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.Surface;
import com.pedro.encoder.input.gl.SurfaceManager;
import com.pedro.encoder.input.gl.render.SimpleCameraRender;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.rtplibrary.R;

/**
 * Created by pedro on 21/02/18.
 * Light version of OpenGlView for devices too slow.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class LightOpenGlView extends OpenGlViewBase {

  private SimpleCameraRender simpleCameraRender = null;
  private boolean keepAspectRatio = false;
  private boolean isFlipHorizontal = false, isFlipVertical = false;

  public LightOpenGlView(Context context) {
    super(context);
  }

  public LightOpenGlView(Context context, AttributeSet attrs) {
    super(context, attrs);
    TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.LightOpenGlView);
    try {
      keepAspectRatio = typedArray.getBoolean(R.styleable.LightOpenGlView_keepAspectRatio, false);
      isFlipHorizontal = typedArray.getBoolean(R.styleable.LightOpenGlView_isFlipHorizontal, false);
      isFlipVertical = typedArray.getBoolean(R.styleable.LightOpenGlView_isFlipVertical, false);
    } finally {
      typedArray.recycle();
    }
  }

  @Override
  public void init() {
    if (!initialized) simpleCameraRender = new SimpleCameraRender();
    simpleCameraRender.setFlip(isFlipHorizontal, isFlipVertical);
    waitTime = 200;
    initialized = true;
  }

  public boolean isKeepAspectRatio() {
    return keepAspectRatio;
  }

  public void setKeepAspectRatio(boolean keepAspectRatio) {
    this.keepAspectRatio = keepAspectRatio;
  }

  public void setCameraFlip(boolean isFlipHorizontal, boolean isFlipVertical) {
    simpleCameraRender.setFlip(isFlipHorizontal, isFlipVertical);
  }

  @Override
  public void run() {
    surfaceManager = new SurfaceManager(getHolder().getSurface());
    surfaceManager.makeCurrent();
    simpleCameraRender.setStreamSize(encoderWidth, encoderHeight);
    simpleCameraRender.setRotation(rotation);
    simpleCameraRender.initGl(getContext());
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
            simpleCameraRender.drawFrame(previewWidth, previewHeight, keepAspectRatio);
            surfaceManager.swapBuffer();
            if (takePhotoCallback != null) {
              takePhotoCallback.onTakePhoto(
                  GlUtil.getBitmap(previewWidth, previewHeight, encoderWidth, encoderHeight));
              takePhotoCallback = null;
            }

            synchronized (sync) {
              if (surfaceManagerEncoder != null) {
                surfaceManagerEncoder.makeCurrent();
                simpleCameraRender.drawFrame(encoderWidth, encoderHeight, false);
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
  public SurfaceTexture getSurfaceTexture() {
    return simpleCameraRender.getSurfaceTexture();
  }

  @Override
  public Surface getSurface() {
    return simpleCameraRender.getSurface();
  }

  @Override
  public void setFilter(int filterPosition, BaseFilterRender baseFilterRender) {

  }

  @Override
  public void setFilter(BaseFilterRender baseFilterRender) {
    setFilter(0, baseFilterRender);
  }

  @Override
  public void enableAA(boolean AAEnabled) {

  }

  @Override
  public boolean isAAEnabled() {
    return false;
  }
}
