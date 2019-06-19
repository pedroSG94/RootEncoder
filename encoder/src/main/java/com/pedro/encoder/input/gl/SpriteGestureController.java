package com.pedro.encoder.input.gl;

import android.graphics.PointF;
import android.os.Build;
import androidx.annotation.RequiresApi;
import android.view.MotionEvent;
import android.view.View;
import com.pedro.encoder.input.gl.render.filters.object.BaseObjectFilterRender;
import com.pedro.encoder.input.video.CameraHelper;

/**
 * Created by pedro on 9/09/17.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class SpriteGestureController {

  private BaseObjectFilterRender baseObjectFilterRender;
  private float lastDistance;
  private boolean preventMoveOutside = true;

  public SpriteGestureController() {
  }

  public SpriteGestureController(BaseObjectFilterRender sprite) {
    this.baseObjectFilterRender = sprite;
  }

  public BaseObjectFilterRender getBaseObjectFilterRender() {
    return baseObjectFilterRender;
  }

  public void setBaseObjectFilterRender(BaseObjectFilterRender baseObjectFilterRender) {
    this.baseObjectFilterRender = baseObjectFilterRender;
  }

  public void setPreventMoveOutside(boolean preventMoveOutside) {
    this.preventMoveOutside = preventMoveOutside;
  }

  public boolean spriteTouched(View view, MotionEvent motionEvent) {
    if (baseObjectFilterRender == null) return false;
    float xPercent = motionEvent.getX() * 100 / view.getWidth();
    float yPercent = motionEvent.getY() * 100 / view.getHeight();
    PointF scale = baseObjectFilterRender.getScale();
    PointF position = baseObjectFilterRender.getPosition();
    boolean xTouched = xPercent >= position.x && xPercent <= position.x + scale.x;
    boolean yTouched = yPercent >= position.y && yPercent <= position.y + scale.y;
    return xTouched && yTouched;
  }

  public void moveSprite(View view, MotionEvent motionEvent) {
    if (baseObjectFilterRender == null) return;
    if (motionEvent.getPointerCount() == 1) {
      float xPercent = motionEvent.getX() * 100 / view.getWidth();
      float yPercent = motionEvent.getY() * 100 / view.getHeight();
      PointF scale = baseObjectFilterRender.getScale();
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
        baseObjectFilterRender.setPosition(x, y);
      } else {
        baseObjectFilterRender.setPosition(xPercent - scale.x / 2f, yPercent - scale.y / 2f);
      }
    }
  }

  public void scaleSprite(MotionEvent motionEvent) {
    if (baseObjectFilterRender == null) return;
    if (motionEvent.getPointerCount() > 1) {
      float distance = CameraHelper.getFingerSpacing(motionEvent);
      float percent = distance >= lastDistance ? 1 : -1;
      PointF scale = baseObjectFilterRender.getScale();
      scale.x += percent;
      scale.y += percent;
      baseObjectFilterRender.setScale(scale.x, scale.y);
      lastDistance = distance;
    }
  }
}
