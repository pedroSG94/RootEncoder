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
  private final List<BaseFilterRender> filterRenders;
  private final ScreenRender screenRender;

  private int width;
  private int height;
  private int previewWidth;
  private int previewHeight;
  private Context context;

  public ManagerRender() {
    filterRenders = new ArrayList<>();
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
    for (BaseFilterRender baseFilterRender : filterRenders) baseFilterRender.draw();
  }

  public void drawScreen(int width, int height, boolean keepAspectRatio, int mode, int rotation,
      boolean flipStreamVertical, boolean flipStreamHorizontal) {
    screenRender.draw(width, height, keepAspectRatio, mode, rotation, flipStreamVertical,
        flipStreamHorizontal);
  }

  public void release() {
    cameraRender.release();
    for (BaseFilterRender baseFilterRender : filterRenders) baseFilterRender.release();
    filterRenders.clear();
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
    final int id = filterRenders.get(position).getPreviousTexId();
    final RenderHandler renderHandler = filterRenders.get(position).getRenderHandler();
    filterRenders.get(position).release();
    filterRenders.set(position, baseFilterRender);
    filterRenders.get(position).setPreviousTexId(id);
    filterRenders.get(position).initGl(width, height, context, previewWidth, previewHeight);
    filterRenders.get(position).setRenderHandler(renderHandler);
  }

  private void addFilter(BaseFilterRender baseFilterRender) {
    filterRenders.add(baseFilterRender);
    baseFilterRender.initGl(width, height, context, previewWidth, previewHeight);
    baseFilterRender.initFBOLink();
    reOrderFilters();
  }

  private void addFilter(int position, BaseFilterRender baseFilterRender) {
    filterRenders.add(position, baseFilterRender);
    baseFilterRender.initGl(width, height, context, previewWidth, previewHeight);
    baseFilterRender.initFBOLink();
    reOrderFilters();
  }

  private void clearFilters() {
    for (BaseFilterRender baseFilterRender: filterRenders) {
      baseFilterRender.release();
    }
    filterRenders.clear();
    reOrderFilters();
  }

  private void removeFilter(int position) {
    filterRenders.remove(position).release();
    reOrderFilters();
  }

  private void removeFilter(BaseFilterRender baseFilterRender) {
    baseFilterRender.release();
    filterRenders.remove(baseFilterRender);
    reOrderFilters();
  }

  private void reOrderFilters() {
    for (int i = 0; i < filterRenders.size(); i++) {
      int texId = i == 0 ? cameraRender.getTexId() : filterRenders.get(i - 1).getTexId();
      filterRenders.get(i).setPreviousTexId(texId);
    }
    int texId = filterRenders.isEmpty() ? cameraRender.getTexId() :
        filterRenders.get(filterRenders.size() - 1).getTexId();
    screenRender.setTexId(texId);
  }

  public void setFilterAction(FilterAction filterAction,  int position, BaseFilterRender baseFilterRender) {
    switch (filterAction) {
      case SET:
        if (filterRenders.size() > 0) {
          setFilter(position, baseFilterRender);
        } else {
          addFilter(baseFilterRender);
        }
        break;
      case SET_INDEX:
        setFilter(position, baseFilterRender);
        break;
      case ADD:
        if (numFilters > 0 && filterRenders.size() >= numFilters) {
          throw new RuntimeException("limit of filters(" + numFilters + ") exceeded");
        }
        addFilter(baseFilterRender);
        break;
      case ADD_INDEX:
        if (numFilters > 0 && filterRenders.size() >= numFilters) {
          throw new RuntimeException("limit of filters(" + numFilters + ") exceeded");
        }
        addFilter(position, baseFilterRender);
        break;
      case CLEAR:
        clearFilters();
        break;
      case REMOVE:
        removeFilter(baseFilterRender);
        break;
      case REMOVE_INDEX:
        removeFilter(position);
        break;
      default:
        break;
    }
  }

  public int filtersCount() {
    return filterRenders.size();
  }

  public void setCameraRotation(int rotation) {
    cameraRender.setRotation(rotation);
  }

  public void setCameraFlip(boolean isFlipHorizontal, boolean isFlipVertical) {
    cameraRender.setFlip(isFlipHorizontal, isFlipVertical);
  }

  public void setPreviewSize(int previewWidth, int previewHeight) {
    for (int i = 0; i < filterRenders.size(); i++) {
      filterRenders.get(i).setPreviewSize(previewWidth, previewHeight);
    }
  }
}
