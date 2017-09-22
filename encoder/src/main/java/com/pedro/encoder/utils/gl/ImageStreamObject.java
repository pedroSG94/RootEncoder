package com.pedro.encoder.utils.gl;

import android.graphics.Bitmap;
import android.util.Log;
import com.pedro.encoder.utils.gl.watermark.WatermarkUtil;
import java.io.IOException;

/**
 * Created by pedro on 23/09/17.
 */

public class ImageStreamObject {

  private static final String TAG = "ImageStreamObject";

  private int streamWidth, streamHeight;
  private int numFrames;
  private Bitmap imageBitmap;

  public ImageStreamObject(int streamWidth, int streamHeight) {
    this.streamWidth = streamWidth;
    this.streamHeight = streamHeight;
  }

  public void load(Bitmap imageBitmap) throws IOException {
    this.imageBitmap = imageBitmap;
    numFrames = 1;
    resize(imageBitmap.getWidth(), imageBitmap.getHeight());
    setPosition(0, 0);
    Log.i(TAG, "finish load image!!!");
  }

  public void resize(int width, int height) {
    imageBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, false);
  }

  public void setPosition(int positionX, int positionY) {
    WatermarkUtil watermarkUtil = new WatermarkUtil(streamWidth, streamHeight);
    imageBitmap = watermarkUtil.createWatermarkBitmap(imageBitmap, positionX, positionY);
  }

  public void recycle() {
    imageBitmap.recycle();
  }

  public int getNumFrames() {
    return numFrames;
  }

  public Bitmap getImageBitmap() {
    return imageBitmap;
  }
}
