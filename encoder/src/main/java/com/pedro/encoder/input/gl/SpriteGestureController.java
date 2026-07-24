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
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.input.video.CameraHelper;

/**
 * Created by pedro on 9/09/17.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class SpriteGestureController {

  private Sprite sprite;
  private float lastDistance;
  private boolean preventMoveOutside = true;

  public SpriteGestureController() {
  }

  public SpriteGestureController(Sprite sprite) {
    this.sprite = sprite;
  }

  public void setSprite(Sprite sprite) {
    this.sprite = sprite;
  }

  public void stopListener() {
    sprite = null;
  }

  public void setPreventMoveOutside(boolean preventMoveOutside) {
    this.preventMoveOutside = preventMoveOutside;
  }

  public boolean spriteTouched(View view, MotionEvent motionEvent) {
    Sprite sprite = this.sprite;
    if (sprite == null) return false;
    float xPercent = motionEvent.getX() * 100 / view.getWidth();
    float yPercent = motionEvent.getY() * 100 / view.getHeight();
    PointF scale = sprite.getScale();
    PointF position = sprite.getTranslation();
    boolean xTouched = xPercent >= position.x && xPercent <= position.x + scale.x;
    boolean yTouched = yPercent >= position.y && yPercent <= position.y + scale.y;
    return xTouched && yTouched;
  }

  public void moveSprite(View view, MotionEvent motionEvent) {
    Sprite sprite = this.sprite;
    if (sprite == null) return;
    if (motionEvent.getPointerCount() == 1) {
      float xPercent = motionEvent.getX() * 100 / view.getWidth();
      float yPercent = motionEvent.getY() * 100 / view.getHeight();
      PointF scale = sprite.getScale();
      if (preventMoveOutside) {
        float x = xPercent - scale.x / 2.0F;
        float y = yPercent - scale.y / 2.0F;
        if (x < 0) {
          x = 0;
        }
        if (x + scale.x > 100.0F) {
          x = 100.0F - scale.x;
        }
        if (y < 0) {
          y = 0;
        }
        if (y + scale.y > 100.0F) {
          y = 100.0F - scale.y;
        }
        sprite.translate(x, y);
      } else {
        sprite.translate(xPercent - scale.x / 2f, yPercent - scale.y / 2f);
      }
    }
  }

  public void scaleSprite(MotionEvent motionEvent) {
    Sprite sprite = this.sprite;
    if (sprite == null) return;
    if (motionEvent.getPointerCount() > 1) {
      float distance = CameraHelper.getFingerSpacing(motionEvent);
      float percent = distance >= lastDistance ? 1 : -1;
      PointF scale = sprite.getScale();
      float newScaleX = scale.x + percent;
      float newScaleY = scale.y + percent;
      sprite.scale(newScaleX, newScaleY);
      lastDistance = distance;
    }
  }
}
