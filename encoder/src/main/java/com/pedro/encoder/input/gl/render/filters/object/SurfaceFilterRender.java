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
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.R;
import com.pedro.encoder.utils.gl.GlUtil;

/**
 * Created by pedro on 18/07/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class SurfaceFilterRender extends BaseObjectFilterRender {

  public interface SurfaceReadyCallback {
    void surfaceReady(SurfaceTexture surfaceTexture);
  }

  private SurfaceTexture surfaceTexture;
  private Surface surface;
  private final SurfaceReadyCallback surfaceReadyCallback;

  public SurfaceFilterRender() {
    this(null);
  }

  public SurfaceFilterRender(SurfaceReadyCallback surfaceReadyCallback) {
    super();
    this.surfaceReadyCallback = surfaceReadyCallback;
  }

  @Override
  protected void initGlFilter(Context context) {
    fragment = R.raw.surface_fragment;
    super.initGlFilter(context);
    GlUtil.createExternalTextures(streamObjectTextureId.length, streamObjectTextureId, 0);
    surfaceTexture = new SurfaceTexture(streamObjectTextureId[0]);
    surfaceTexture.setDefaultBufferSize(getWidth(), getHeight());
    surface = new Surface(surfaceTexture);
    if (surfaceReadyCallback != null) {
      new Handler(Looper.getMainLooper()).post(() -> surfaceReadyCallback.surfaceReady(surfaceTexture));
    }
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
    surfaceTexture.release();
    surface.release();
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
}