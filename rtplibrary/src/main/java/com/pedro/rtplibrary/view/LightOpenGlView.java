/*
 * Copyright (C) 2021 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.rtplibrary.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import androidx.annotation.RequiresApi;
import com.pedro.encoder.input.gl.render.SimpleCameraRender;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.rtplibrary.R;

/**
 * Created by pedro on 21/02/18.
 *
 * Light version of OpenGlView for devices too slow.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class LightOpenGlView extends OpenGlViewBase {

  private SimpleCameraRender simpleCameraRender = null;
  private boolean keepAspectRatio = false;
  private AspectRatioMode aspectRatioMode = AspectRatioMode.Adjust;
  private boolean isFlipHorizontal = false, isFlipVertical = false;

  public LightOpenGlView(Context context) {
    super(context);
  }

  public LightOpenGlView(Context context, AttributeSet attrs) {
    super(context, attrs);
    TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.LightOpenGlView);
    try {
      keepAspectRatio = typedArray.getBoolean(R.styleable.LightOpenGlView_keepAspectRatio, false);
      aspectRatioMode = AspectRatioMode.fromId(typedArray.getInt(R.styleable.OpenGlView_aspectRatioMode, 0));
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
    initialized = true;
  }

  public boolean isKeepAspectRatio() {
    return keepAspectRatio;
  }

  public void setAspectRatioMode(AspectRatioMode aspectRatioMode) {
    this.aspectRatioMode = aspectRatioMode;
  }

  public void setKeepAspectRatio(boolean keepAspectRatio) {
    this.keepAspectRatio = keepAspectRatio;
  }

  public void setCameraFlip(boolean isFlipHorizontal, boolean isFlipVertical) {
    simpleCameraRender.setFlip(isFlipHorizontal, isFlipVertical);
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    Log.i(TAG, "size: " + width + "x" + height);
    this.previewWidth = width;
    this.previewHeight = height;
  }

  @Override
  public void run() {
    surfaceManager.release();
    surfaceManager.eglSetup(getHolder().getSurface());
    surfaceManager.makeCurrent();
    simpleCameraRender.initGl(getContext(), encoderWidth, encoderHeight);
    simpleCameraRender.getSurfaceTexture().setOnFrameAvailableListener(this);
    surfaceManagerPhoto.release();
    surfaceManagerPhoto.eglSetup(encoderWidth, encoderHeight, surfaceManager);
    semaphore.release();
    while (running) {
      if (frameAvailable || forceRender) {
        frameAvailable = false;
        surfaceManager.makeCurrent();
        simpleCameraRender.updateFrame();
        simpleCameraRender.drawFrame(previewWidth, previewHeight, keepAspectRatio, aspectRatioMode.id,
            0, true, isPreviewVerticalFlip, isPreviewHorizontalFlip);
        surfaceManager.swapBuffer();

        synchronized (sync) {
          if (surfaceManagerEncoder.isReady() && !fpsLimiter.limitFPS()) {
            int w = muteVideo ? 0 : encoderWidth;
            int h = muteVideo ? 0 : encoderHeight;
            surfaceManagerEncoder.makeCurrent();
            simpleCameraRender.drawFrame(w, h, false, aspectRatioMode.id,
                streamRotation, false, isStreamVerticalFlip, isStreamHorizontalFlip);
            surfaceManagerEncoder.swapBuffer();
          }
          if (takePhotoCallback != null && surfaceManagerPhoto.isReady()) {
            surfaceManagerPhoto.makeCurrent();
            simpleCameraRender.drawFrame(encoderWidth, encoderHeight, false, aspectRatioMode.id,
                streamRotation, false, isStreamVerticalFlip, isStreamHorizontalFlip);
            takePhotoCallback.onTakePhoto(GlUtil.getBitmap(encoderWidth, encoderHeight));
            takePhotoCallback = null;
            surfaceManagerPhoto.swapBuffer();
          }
        }
      }
    }
    simpleCameraRender.release();
    surfaceManagerPhoto.release();
    surfaceManagerEncoder.release();
    surfaceManager.release();
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
  public void addFilter(BaseFilterRender baseFilterRender) {

  }

  @Override
  public void addFilter(int filterPosition, BaseFilterRender baseFilterRender) {

  }

  @Override
  public void clearFilters() {

  }

  @Override
  public void removeFilter(int filterPosition) {

  }

  @Override
  public void removeFilter(BaseFilterRender baseFilterRender) {

  }

  @Override
  public int filtersCount() {
    return 0;
  }

  @Override
  public void setFilter(BaseFilterRender baseFilterRender) {
    addFilter(baseFilterRender);
  }

  @Override
  public void enableAA(boolean AAEnabled) {

  }

  @Override
  public void setRotation(int rotation) {
    simpleCameraRender.setRotation(rotation);
  }

  @Override
  public boolean isAAEnabled() {
    return false;
  }
}
