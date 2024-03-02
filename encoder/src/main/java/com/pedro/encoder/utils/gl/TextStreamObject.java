/*
 * Copyright (C) 2023 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.encoder.utils.gl;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;

/**
 * Created by pedro on 23/09/17.
 */

public class TextStreamObject extends StreamObjectBase {

  private static final String TAG = "TextStreamObject";

  private int numFrames;
  private Bitmap imageBitmap;

  public TextStreamObject() {
  }

  @Override
  public int getWidth() {
    return imageBitmap != null ? imageBitmap.getWidth() : 0;
  }

  @Override
  public int getHeight() {
    return imageBitmap != null ? imageBitmap.getHeight() : 0;
  }

  public void load(String text, float textSize, int textColor, int backgroundColor, Typeface typeface) {
    numFrames = 1;
    imageBitmap = textAsBitmap(text, textSize, textColor, backgroundColor, typeface);
    Log.i(TAG, "finish load text");
  }

  @Override
  public void recycle() {
    if (imageBitmap != null && !imageBitmap.isRecycled()) imageBitmap.recycle();
  }

  private Bitmap textAsBitmap(String text, float textSize, int textColor, int backgroundColor, Typeface typeface) {
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setTextSize(textSize);
    paint.setColor(textColor);
    paint.setAlpha(255);
    if (typeface != null) paint.setTypeface(typeface);
    paint.setTextAlign(Paint.Align.LEFT);

    float baseline = -paint.ascent(); // ascent() is negative
    int width = (int) (paint.measureText(text) + 0.5f); // round
    int height = (int) (baseline + paint.descent() + 0.5f);
    Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(image);
    canvas.drawColor(backgroundColor);

    canvas.drawText(text, 0, baseline, paint);
    return image;
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
