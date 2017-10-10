package com.pedro.encoder.utils.gl;

/**
 * Created by pedro on 9/10/17.
 */

public abstract class StreamObjectBase {

  public abstract int updateFrame();

  public abstract void resize(int width, int height);

  public abstract void setPosition(int positionX, int positionY);

  public abstract void recycle();

  public abstract int getNumFrames();
}
