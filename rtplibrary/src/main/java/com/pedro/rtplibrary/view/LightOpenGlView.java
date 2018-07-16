package com.pedro.rtplibrary.view;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.Surface;
import com.pedro.encoder.input.gl.SurfaceManager;
import com.pedro.encoder.input.gl.render.SimpleCameraRender;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.utils.gl.GifStreamObject;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.TextStreamObject;
import com.pedro.encoder.utils.gl.TranslateTo;

/**
 * Created by pedro on 21/02/18.
 * Light version of OpenGlView for devices too slow.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class LightOpenGlView extends OpenGlViewBase {

  private SimpleCameraRender simpleCameraRender = null;
  private boolean keepAspectRatio = false;
  private boolean isFrontPreviewFlip = false;

  public LightOpenGlView(Context context) {
    super(context);
  }

  public LightOpenGlView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void init() {
    if (!initialized) simpleCameraRender = new SimpleCameraRender();
    waitTime = 200;
    initialized = true;
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
  public void run() {
    surfaceManager = new SurfaceManager(getHolder().getSurface());
    surfaceManager.makeCurrent();
    simpleCameraRender.setStreamSize(encoderWidth, encoderHeight);
    simpleCameraRender.isCamera2LandScape(isCamera2Landscape);
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
            simpleCameraRender.drawFrame(previewWidth, previewHeight, keepAspectRatio,
                isFrontPreviewFlip);
            surfaceManager.swapBuffer();
            if (takePhotoCallback != null) {
              takePhotoCallback.onTakePhoto(
                  GlUtil.getBitmap(previewWidth, previewHeight, encoderWidth, encoderHeight));
              takePhotoCallback = null;
            }

            synchronized (sync) {
              if (surfaceManagerEncoder != null) {
                surfaceManagerEncoder.makeCurrent();
                simpleCameraRender.drawFrame(encoderWidth, encoderHeight, false, false);
                long ts = simpleCameraRender.getSurfaceTexture().getTimestamp();
                surfaceManagerEncoder.setPresentationTime(ts);
                surfaceManagerEncoder.swapBuffer();
              }
            }
            if (onChangeFace) {
              simpleCameraRender.faceChanged(isFrontCamera);
              onChangeFace = false;
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
  public void setFilter(BaseFilterRender baseFilterRender) {

  }

  @Override
  public void setGif(GifStreamObject gifStreamObject) {

  }

  @Override
  public void setImage(ImageStreamObject imageStreamObject) {

  }

  @Override
  public void setText(TextStreamObject textStreamObject) {

  }

  @Override
  public void clear() {

  }

  @Override
  public void setStreamObjectAlpha(float alpha) {

  }

  @Override
  public void setStreamObjectSize(float sizeX, float sizeY) {

  }

  @Override
  public void setStreamObjectPosition(float x, float y) {

  }

  @Override
  public void setStreamObjectPosition(TranslateTo translateTo) {

  }

  @Override
  public void enableAA(boolean AAEnabled) {

  }

  @Override
  public boolean isAAEnabled() {
    return false;
  }

  @Override
  public PointF getScale() {
    return null;
  }

  @Override
  public PointF getPosition() {
    return null;
  }
}
