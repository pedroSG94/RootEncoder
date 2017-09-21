package com.pedro.encoder.utils.gl.watermark;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

/**
 * Created by pedro on 20/09/17.
 */

public class WatermarkUtil {

  private int streamWidth, streamHeight;

  public WatermarkUtil(int streamWidth, int streamHeight) {
    this.streamWidth = streamWidth;
    this.streamHeight = streamHeight;
  }

  public Bitmap createWatermarkBitmap(Bitmap watermark, int width, int height) {
    Bitmap background = createTransparentBitmap(streamWidth, streamHeight);
    Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    Canvas canvas = new Canvas(background);
    canvas.drawBitmap(Bitmap.createScaledBitmap(watermark, width, height, false), new Matrix(), paint);
    return background;
  }

  private Bitmap createTransparentBitmap(int width, int height) {
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    int[] allPixels = new int[width * height];
    bitmap.getPixels(allPixels, 0, width, 0, 0, width, height);
    for (int i = 0; i < width * height; i++) {
      allPixels[i] = Color.TRANSPARENT;
    }
    bitmap.setPixels(allPixels, 0, width, 0, 0, width, height);
    return bitmap;
  }
}
