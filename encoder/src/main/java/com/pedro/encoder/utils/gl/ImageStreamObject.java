package com.pedro.encoder.utils.gl;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * Created by pedro on 23/09/17.
 */

public class ImageStreamObject extends StreamObjectBase {

  private static final String TAG = "ImageStreamObject";

  private int numFrames;
  private Bitmap imageBitmap;

  public ImageStreamObject() {
  }

  @Override
  public int getWidth() {
    return imageBitmap != null ? imageBitmap.getWidth() : 0;
  }

  @Override
  public int getHeight() {
    return imageBitmap != null ? imageBitmap.getHeight() : 0;
  }

  public void load(Bitmap imageBitmap) {
    this.imageBitmap = imageBitmap;
    numFrames = 1;
    Log.i(TAG, "finish load image");
  }

  @Override
  public void recycle() {
    if (imageBitmap != null) imageBitmap.recycle();
  }

  @Override
  public int getNumFrames() {
    return numFrames;
  }

  @Override
  public Bitmap[] getBitmaps() {
    return new Bitmap[]{imageBitmap};
  }

  @Override
  public int updateFrame() {
    return 0;
  }
}
