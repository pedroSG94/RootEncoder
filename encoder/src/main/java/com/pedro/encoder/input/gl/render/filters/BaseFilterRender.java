package com.pedro.encoder.input.gl.render.filters;

import android.content.Context;
import android.opengl.GLES20;
import android.os.Build;
import android.support.annotation.RequiresApi;
import com.pedro.encoder.input.gl.render.BaseRenderOffScreen;
import com.pedro.encoder.utils.gl.GlUtil;

/**
 * Created by pedro on 29/01/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public abstract class BaseFilterRender extends BaseRenderOffScreen {

  protected int texId;

  private int width;
  private int height;

  public void initGl(int width, int height, Context context) {
    this.width = width;
    this.height = height;
    GlUtil.checkGlError("initGl start");
    initGlFilter(context);
    initFBO(width, height);
    GlUtil.checkGlError("initGl end");
  }

  protected abstract void initGlFilter(Context context);

  public void draw() {
    GlUtil.checkGlError("drawFilter start");
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId[0]);
    GLES20.glViewport(0, 0, width, height);
    drawFilter();
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    GlUtil.checkGlError("drawFilter end");
  }

  protected abstract void drawFilter();

  public void setTexId(int texId) {
    this.texId = texId;
  }
}
