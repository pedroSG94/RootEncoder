/*
 * Copyright (C) 2024 pedroSG94.
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

package com.pedro.encoder.input.gl.render.filters.object;

import android.graphics.Color;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.utils.gl.TextStreamObject;

/**
 * Created by pedro on 27/07/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class TextObjectFilterRender extends BaseObjectFilterRender {

  private String text;
  private float textSize;
  private int textColor;
  private int backgroundColor;
  private Typeface typeface;

  public TextObjectFilterRender() {
    super();
    streamObject = new TextStreamObject();
  }

  @Override
  protected void drawFilter() {
    super.drawFilter();
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, streamObjectTextureId[0]);
    //Set alpha. 0f if no image loaded.
    GLES20.glUniform1f(uAlphaHandle, streamObjectTextureId[0] == -1 ? 0f : alpha);
  }

  public void setText(String text, float textSize, int textColor) {
    setText(text, textSize, textColor, Color.TRANSPARENT, null);
  }

  public void setText(String text, float textSize, int textColor, int backgroundColor) {
    setText(text, textSize, textColor, backgroundColor, null);
  }

  public void setText(String text, float textSize, int textColor, Typeface typeface) {
    setText(text, textSize, textColor, Color.TRANSPARENT, typeface);
  }

  public void setText(String text, float textSize, int textColor, int backgroundColor, Typeface typeface) {
    this.text = text;
    this.textSize = textSize;
    this.textColor = textColor;
    this.backgroundColor = backgroundColor;
    this.typeface = typeface;
    ((TextStreamObject) streamObject).load(text, textSize, textColor, backgroundColor, typeface);
    shouldLoad = true;
  }

  public void addText(String text) {
    setText(this.text + text, textSize, textColor, backgroundColor, typeface);
  }

  public void updateColor(int textColor) {
    setText(this.text + text, textSize, textColor, backgroundColor, typeface);
  }

  public void updateTypeface(Typeface typeface) {
    setText(this.text + text, textSize, textColor, backgroundColor, typeface);
  }

  public void updateTextSize(float textSize) {
    setText(this.text + text, textSize, textColor, backgroundColor, typeface);
  }
}
