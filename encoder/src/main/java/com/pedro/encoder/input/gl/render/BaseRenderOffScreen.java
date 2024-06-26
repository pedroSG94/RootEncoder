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

package com.pedro.encoder.input.gl.render;

import android.content.Context;
import android.opengl.GLES20;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.utils.gl.GlUtil;

import java.nio.FloatBuffer;

/**
 * Created by pedro on 29/01/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public abstract class BaseRenderOffScreen {

  protected FloatBuffer squareVertex;

  public static final int FLOAT_SIZE_BYTES = 4;
  public static final int SQUARE_VERTEX_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
  public static final int SQUARE_VERTEX_DATA_POS_OFFSET = 0;
  public static final int SQUARE_VERTEX_DATA_UV_OFFSET = 3;

  protected float[] MVPMatrix = new float[16];
  protected float[] STMatrix = new float[16];

  protected RenderHandler renderHandler = new RenderHandler();

  protected int width;
  protected int height;

  public abstract void initGl(int width, int height, Context context, int previewWidth,
      int previewHeight);

  public abstract void draw();

  public abstract void release();

  public int getTexId() {
    return renderHandler.getTexId()[0];
  }

  protected void initFBO(int width, int height) {
    initFBO(width, height, renderHandler.getFboId(), renderHandler.getRboId(),
        renderHandler.getTexId());
  }

  protected void initFBO(int width, int height, int[] fboId, int[] rboId, int[] texId) {
    GlUtil.checkGlError("initFBO_S");

    GLES20.glGenFramebuffers(1, fboId, 0);
    GLES20.glGenRenderbuffers(1, rboId, 0);
    GLES20.glGenTextures(1, texId, 0);

    GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, rboId[0]);
    GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width,
        height);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId[0]);
    GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
        GLES20.GL_RENDERBUFFER, rboId[0]);

    GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0]);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA,
        GLES20.GL_UNSIGNED_BYTE, null);
    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
        GLES20.GL_TEXTURE_2D, texId[0], 0);

    int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
    if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
      throw new RuntimeException("FrameBuffer uncompleted code: " + status);
    }
    GlUtil.checkGlError("initFBO_E");
  }
}
