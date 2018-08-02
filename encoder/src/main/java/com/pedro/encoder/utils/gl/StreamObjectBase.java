package com.pedro.encoder.utils.gl;

/**
 * Created by pedro on 9/10/17.
 */

public abstract class StreamObjectBase {

  public abstract int getWidth();

  public abstract int getHeight();

  public abstract int updateFrame();

  public abstract void recycle();

  public abstract int getNumFrames();
}
