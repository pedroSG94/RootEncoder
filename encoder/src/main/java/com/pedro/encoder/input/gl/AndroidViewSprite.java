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

package com.pedro.encoder.input.gl;

import android.graphics.PointF;
import android.view.View;

import com.pedro.encoder.utils.gl.TranslateTo;

/**
 * Created by pedro on 3/2/22.
 */
public class AndroidViewSprite {

  private PointF scale;
  private PointF position;
  private PointF rotationAxis;
  private int rotation;
  private View view;

  public AndroidViewSprite() {
    reset();
  }

  public void setView(View view) {
    this.view = view;
    rotationAxis = new PointF(view.getMeasuredWidth() / 2f, view.getMeasuredHeight() / 2f);
  }

  /**
   * @param deltaX Position x in percent
   * @param deltaY Position x in percent
   */
  public void translate(float deltaX, float deltaY) {
    position.x = deltaX;
    position.y = deltaY;
  }

  /**
   * @param translation Predefined position
   */
  public void translate(TranslateTo translation) {
    switch (translation) {
      case CENTER:
        this.position.x = 50f - scale.x / 2f;
        this.position.y = 50f - scale.x / 2f;
        break;
      case BOTTOM:
        this.position.x = 50f - scale.x / 2f;
        this.position.y = 100f - scale.y;
        break;
      case TOP:
        this.position.x = 50f - scale.x / 2f;
        this.position.y = 0f;
        break;
      case LEFT:
        this.position.x = 0f;
        this.position.y = 50f - scale.y / 2f;
        break;
      case RIGHT:
        this.position.x = 100f - scale.x;
        this.position.y = 50f - scale.y / 2f;
        break;
      case TOP_LEFT:
        this.position.x = 0f;
        this.position.y = 0f;
        break;
      case TOP_RIGHT:
        this.position.x = 100f - scale.x;
        this.position.y = 0f;
        break;
      case BOTTOM_LEFT:
        this.position.x = 0f;
        this.position.y = 100f - scale.y;
        break;
      case BOTTOM_RIGHT:
        this.position.x = 100f - scale.x;
        this.position.y = 100f - scale.y;
        break;
      default:
        break;
    }
  }

  /**
   * @param deltaX Scale x in percent
   * @param deltaY Scale y in percent
   */
  public void scale(float deltaX, float deltaY) {
    //keep old position
    position.x /= deltaX / scale.x;
    position.y /= deltaY / scale.y;
    //set new scale.
    scale = new PointF(deltaX, deltaY);
  }

  /**
   * @return Scale in percent
   */
  public PointF getScale() {
    return scale;
  }

  /**
   * @return Position in percent
   */
  public PointF getTranslation() {
    return position;
  }

  public int getRotation() {
    return rotation;
  }

  public PointF getRotationAxis() {
    return rotationAxis;
  }

  public void setRotation(int rotation) {
    if (rotation < 0) {
      this.rotation = 0;
    } else if (rotation > 360) {
      this.rotation = 360;
    } else {
      this.rotation = rotation;
    }
  }

  public void reset() {
    scale = new PointF(0f, 0f);
    position = new PointF(0f, 0f);
  }

  /**
   * Traduce position in percent to work with canvas.
   */
  public PointF getCanvasPosition(float previewX, float previewY) {
    return new PointF(previewX * position.x / 100f, previewY * position.y / 100f);
  }

  /**
   * Traduce scale in percent to work with canvas.
   */
  public PointF getCanvasScale(float previewX, float previewY) {
    float scaleFactorX = 100f * (float) view.getWidth() / previewX;
    float scaleFactorY = 100f * (float) view.getHeight() / previewY;
    return new PointF(scale.x / scaleFactorX, scale.y / scaleFactorY);
  }

  /**
   * Calculate default scale if none is indicated after load the filter.
   */
  public void calculateDefaultScale(float previewX, float previewY) {
    if (scale.x == 0f && scale.y == 0f) {
      scale.x = 100f * (float) view.getWidth() / previewX;
      scale.y = 100f * (float) view.getHeight() / previewY;
    }
  }
}
