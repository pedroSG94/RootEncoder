package com.pedro.encoder.input.gl.render;

/**
 * Created by pedro on 25/7/18.
 */

public class RenderHandler {

  private int[] fboId = new int[] { 0 };
  private int[] rboId = new int[] { 0 };
  private int[] texId = new int[] { 0 };

  public int[] getTexId() {
    return texId;
  }

  public int[] getFboId() {
    return fboId;
  }

  public int[] getRboId() {
    return rboId;
  }

  public void setFboId(int[] fboId) {
    this.fboId = fboId;
  }

  public void setRboId(int[] rboId) {
    this.rboId = rboId;
  }

  public void setTexId(int[] texId) {
    this.texId = texId;
  }
}
