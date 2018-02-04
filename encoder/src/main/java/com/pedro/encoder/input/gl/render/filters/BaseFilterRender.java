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

  private int width;
  private int height;

  //static to share with all filters
  protected static int previousTexId;
  protected static final int[] fboId = new int[] { 0 };
  private static final int[] rboId = new int[] { 0 };
  private static final int[] texId = new int[] { 0 };

  public void initGl(int width, int height, Context context) {
    this.width = width;
    this.height = height;
    GlUtil.checkGlError("initGl start");
    initGlFilter(context);
    GlUtil.checkGlError("initGl end");
  }

  public void initFBOLink() {
    initFBO(width, height, fboId, rboId, texId);
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
    this.previousTexId = texId;
  }

  @Override
  public int getTexId() {
    return texId[0];
  }

  protected int getWidth() {
    return width;
  }

  protected int getHeight() {
    return height;
  }
}
