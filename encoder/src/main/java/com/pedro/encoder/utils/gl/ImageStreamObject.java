package com.pedro.encoder.utils.gl;

import android.graphics.Bitmap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Created by pedro on 23/09/17.
 */

public class ImageStreamObject extends StreamObjectBase {

  private final static Logger logger = LoggerFactory.getLogger(ImageStreamObject.class);

  private int numFrames;
  private Bitmap imageBitmap;

  public ImageStreamObject() {
  }

  @Override
  public int getWidth() {
    return imageBitmap.getWidth();
  }

  @Override
  public int getHeight() {
    return imageBitmap.getHeight();
  }

  public void load(Bitmap imageBitmap) throws IOException {
    this.imageBitmap = imageBitmap;
    numFrames = 1;
    logger.info("finish load image");
  }

  @Override
  public void resize(int width, int height) {
    imageBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, false);
  }

  @Override
  public void recycle() {
    imageBitmap.recycle();
  }

  @Override
  public int getNumFrames() {
    return numFrames;
  }

  public Bitmap getImageBitmap() {
    return imageBitmap;
  }

  @Override
  public int updateFrame() {
    return 0;
  }
}
