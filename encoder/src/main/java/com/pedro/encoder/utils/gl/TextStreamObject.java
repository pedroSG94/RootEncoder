package com.pedro.encoder.utils.gl;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;
import com.pedro.encoder.utils.gl.watermark.WatermarkUtil;
import java.io.IOException;

/**
 * Created by pedro on 23/09/17.
 */

public class TextStreamObject {

  private static final String TAG = "TextStreamObject";

  private int streamWidth, streamHeight;
  private int numFrames;
  private Bitmap imageBitmap;

  public TextStreamObject(int streamWidth, int streamHeight) {
    this.streamWidth = streamWidth;
    this.streamHeight = streamHeight;
  }

  public void load(String text, float textSize, int textColor) throws IOException {
    numFrames = 1;
    imageBitmap = textAsBitmap(text, textSize, textColor);
    resize(imageBitmap.getWidth(), imageBitmap.getHeight());
    setPosition(0, 0);
    Log.i(TAG, "finish load text!!!");
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

  private Bitmap textAsBitmap(String text, float textSize, int textColor) {
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setTextSize(textSize);
    paint.setColor(textColor);
    paint.setTextAlign(Paint.Align.LEFT);
    float baseline = -paint.ascent(); // ascent() is negative
    int width = (int) (paint.measureText(text) + 0.5f); // round
    int height = (int) (baseline + paint.descent() + 0.5f);
    Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(image);
    canvas.drawText(text, 0, baseline, paint);
    return image;
  }

  public int getNumFrames() {
    return numFrames;
  }

  public Bitmap getImageBitmap() {
    return imageBitmap;
  }
}
