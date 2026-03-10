/*
 * Copyright (C) 2024 pedroSG94.
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

package com.pedro.encoder.input.gl.render.filters.object;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.View;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.R;
import com.pedro.encoder.utils.ViewPresentation;
import com.pedro.encoder.utils.gl.GlUtil;

/**
 * Created by pedro on 18/07/18.
 */

@RequiresApi(api = Build.VERSION_CODES.KITKAT_WATCH)
public class ViewSurfaceFilterRender extends BaseObjectFilterRender {

  private SurfaceTexture surfaceTexture;
  private Surface surface;
  private VirtualDisplay virtualDisplay;
  private ViewPresentation viewPresentation;
  private View view;

  public ViewSurfaceFilterRender() {
    super();
  }

  @Override
  protected void initGlFilter(Context context) {
    fragment = R.raw.surface_fragment;
    super.initGlFilter(context);
    GlUtil.createExternalTextures(streamObjectTextureId.length, streamObjectTextureId, 0);
    surfaceTexture = new SurfaceTexture(streamObjectTextureId[0]);
    surfaceTexture.setDefaultBufferSize(getWidth(), getHeight());
    surface = new Surface(surfaceTexture);
    DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    virtualDisplay = displayManager.createVirtualDisplay(
        "MiVistaVirtual",
        getWidth(), getHeight(), context.getResources().getDisplayMetrics().densityDpi,
        surface,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
    );
    startRender();
  }

  @Override
  protected void drawFilter() {
    surfaceTexture.updateTexImage();
    super.drawFilter();
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, streamObjectTextureId[0]);
    //Set alpha. 0f if no image loaded.
    GLES20.glUniform1f(uAlphaHandle, streamObjectTextureId[0] == -1 ? 0f : alpha);
  }

  @Override
  public void release() {
    super.release();
    if (viewPresentation != null) {
      viewPresentation.dismiss();
      viewPresentation = null;
    }
    if (virtualDisplay != null) virtualDisplay.release();
    if (surfaceTexture != null) surfaceTexture.release();
    if (surface != null) surface.release();
  }

  /**
   * This texture must be renderer using an api called on main thread to avoid possible errors
   */
  public SurfaceTexture getSurfaceTexture() {
    return surfaceTexture;
  }

  /**
   * This surface must be renderer using an api called on main thread to avoid possible errors
   */
  public Surface getSurface() {
    return surface;
  }

  public void setView(View view) {
    if (view == null) return;
    this.view = view;
    startRender();
  }

  private void startRender() {
    new Handler(Looper.getMainLooper()).post(() -> {
      if (view == null || virtualDisplay == null) return;
      if (viewPresentation != null) viewPresentation.dismiss();
      viewPresentation = new ViewPresentation(view, view.getContext(), virtualDisplay.getDisplay());
      viewPresentation.show();
    });
  }
}