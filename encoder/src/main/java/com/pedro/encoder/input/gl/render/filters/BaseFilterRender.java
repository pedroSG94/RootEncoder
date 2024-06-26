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

package com.pedro.encoder.input.gl.render.filters;

import android.content.Context;
import android.opengl.GLES20;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.input.gl.render.BaseRenderOffScreen;
import com.pedro.encoder.input.gl.render.RenderHandler;
import com.pedro.encoder.utils.gl.GlUtil;

/**
 * Created by pedro on 29/01/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public abstract class BaseFilterRender extends BaseRenderOffScreen {

  private int width;
  private int height;
  private int previewWidth;
  private int previewHeight;

  protected int previousTexId;
  private RenderHandler renderHandler = new RenderHandler();

  public void initGl(int width, int height, Context context, int previewWidth, int previewHeight) {
    this.width = width;
    this.height = height;
    this.previewWidth = previewWidth;
    this.previewHeight = previewHeight;
    GlUtil.checkGlError("initGl start");
    initGlFilter(context);
    GlUtil.checkGlError("initGl end");
  }

  public void setPreviewSize(int previewWidth, int previewHeight) {
    this.previewWidth = previewWidth;
    this.previewHeight = previewHeight;
  }

  public void initFBOLink() {
    initFBO(width, height, renderHandler.getFboId(), renderHandler.getRboId(),
        renderHandler.getTexId());
  }

  protected abstract void initGlFilter(Context context);

  public void draw() {
    GlUtil.checkGlError("drawFilter start");
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderHandler.getFboId()[0]);
    GLES20.glViewport(0, 0, width, height);
    drawFilter();
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    GlUtil.checkGlError("drawFilter end");
  }

  protected abstract void drawFilter();

  public void setPreviousTexId(int texId) {
    this.previousTexId = texId;
  }

  @Override
  public int getTexId() {
    return renderHandler.getTexId()[0];
  }

  protected int getWidth() {
    return width;
  }

  protected int getHeight() {
    return height;
  }

  public int getPreviewWidth() {
    return previewWidth;
  }

  public int getPreviewHeight() {
    return previewHeight;
  }

  public int getPreviousTexId() {
    return previousTexId;
  }

  public RenderHandler getRenderHandler() {
    return renderHandler;
  }

  public void setRenderHandler(RenderHandler renderHandler) {
    this.renderHandler = renderHandler;
  }
}
