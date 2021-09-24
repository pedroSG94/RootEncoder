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

package com.pedro.encoder.input.gl.render;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.view.Surface;
import androidx.annotation.RequiresApi;

import com.pedro.encoder.input.gl.FilterAction;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pedro on 27/01/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class ManagerRender {

  //Set filter limit. If the number is 0 or less you can add infinity filters
  public static int numFilters = 0;

  private final CameraRender cameraRender;
  private final List<BaseFilterRender> baseFilterRender;
  private final ScreenRender screenRender;

  private int width;
  private int height;
  private int previewWidth;
  private int previewHeight;
  private Context context;

  public ManagerRender() {
    baseFilterRender = new ArrayList<>();
    cameraRender = new CameraRender();
    screenRender = new ScreenRender();
  }

  public void initGl(Context context, int encoderWidth, int encoderHeight, int previewWidth,
      int previewHeight) {
    this.context = context;
    this.width = encoderWidth;
    this.height = encoderHeight;
    this.previewWidth = previewWidth;
    this.previewHeight = previewHeight;
    cameraRender.initGl(width, height, context, previewWidth, previewHeight);
    screenRender.setStreamSize(encoderWidth, encoderHeight);
    screenRender.setTexId(cameraRender.getTexId());
    screenRender.initGl(context);
  }

  public void drawOffScreen() {
    cameraRender.draw();
    for (BaseFilterRender baseFilterRender : baseFilterRender) baseFilterRender.draw();
  }

  public void drawScreen(int width, int height, boolean keepAspectRatio, int mode, int rotation,
      boolean isPreview, boolean flipStreamVertical, boolean flipStreamHorizontal) {
    screenRender.draw(width, height, keepAspectRatio, mode, rotation, isPreview,
            flipStreamVertical, flipStreamHorizontal);
  }

  public void release() {
    cameraRender.release();
    for (int i = 0; i < baseFilterRender.size(); i++) {
      baseFilterRender.get(i).release();
    }
    baseFilterRender.clear();
    screenRender.release();
  }

  public void enableAA(boolean AAEnabled) {
    screenRender.setAAEnabled(AAEnabled);
  }

  public boolean isAAEnabled() {
    return screenRender.isAAEnabled();
  }

  public void updateFrame() {
    cameraRender.updateTexImage();
  }

  public SurfaceTexture getSurfaceTexture() {
    return cameraRender.getSurfaceTexture();
  }

  public Surface getSurface() {
    return cameraRender.getSurface();
  }

  private void setFilter(int position, BaseFilterRender baseFilterRender) {
    this.baseFilterRender.get(position).release();
    this.baseFilterRender.set(position, baseFilterRender);
    baseFilterRender.setPreviousTexId(0);
    baseFilterRender.initGl(width, height, context, previewWidth, previewHeight);
    baseFilterRender.initFBOLink();
    reOrderFilters();
  }

  private void addFilter(BaseFilterRender baseFilterRender) {
    this.baseFilterRender.add(baseFilterRender);
    baseFilterRender.setPreviousTexId(0);
    baseFilterRender.initGl(width, height, context, previewWidth, previewHeight);
    baseFilterRender.initFBOLink();
    reOrderFilters();
  }

  private void addFilter(int position, BaseFilterRender baseFilterRender) {
    this.baseFilterRender.add(position, baseFilterRender);
    baseFilterRender.setPreviousTexId(0);
    baseFilterRender.initGl(width, height, context, previewWidth, previewHeight);
    baseFilterRender.initFBOLink();
    reOrderFilters();
  }

  private void clearFilters() {
    for (BaseFilterRender baseFilterRender: this.baseFilterRender) {
      baseFilterRender.release();
    }
    baseFilterRender.clear();
    reOrderFilters();
  }

  private void removeFilter(int position) {
    baseFilterRender.get(position).release();
    baseFilterRender.remove(position);
    reOrderFilters();
  }

  private void reOrderFilters() {
    for (int i = 0; i < baseFilterRender.size(); i++) {
      int texId = i == 0 ? cameraRender.getTexId() : baseFilterRender.get(i - 1).getTexId();
      baseFilterRender.get(i).setPreviousTexId(texId);
    }
    int texId = baseFilterRender.size() < 1 ? cameraRender.getTexId() :
        baseFilterRender.get(baseFilterRender.size() - 1).getTexId();
    screenRender.setTexId(texId);
  }

  public void setFilterAction(FilterAction filterAction,  int position, BaseFilterRender baseFilterRender) {
    switch (filterAction) {
      case SET:
        setFilter(position, baseFilterRender);
        break;
      case ADD:
        if (numFilters > 0 && this.baseFilterRender.size() >= numFilters) {
          throw new RuntimeException("limit of filters(" + numFilters + ") exceeded");
        }
        addFilter(baseFilterRender);
        break;
      case ADD_INDEX:
        if (numFilters > 0 && this.baseFilterRender.size() >= numFilters) {
          throw new RuntimeException("limit of filters(" + numFilters + ") exceeded");
        }
        addFilter(position, baseFilterRender);
        break;
      case CLEAR:
        clearFilters();
        break;
      case REMOVE:
        removeFilter(position);
        break;
      default:
        break;
    }
  }

  public int filtersCount() {
    return baseFilterRender.size();
  }

  public void setCameraRotation(int rotation) {
    cameraRender.setRotation(rotation);
  }

  public void setCameraFlip(boolean isFlipHorizontal, boolean isFlipVertical) {
    cameraRender.setFlip(isFlipHorizontal, isFlipVertical);
  }

  public void setPreviewSize(int previewWidth, int previewHeight) {
    for (int i = 0; i < this.baseFilterRender.size(); i++) {
      this.baseFilterRender.get(i).setPreviewSize(previewWidth, previewHeight);
    }
  }
}
