package com.pedro.encoder.input.gl;

import android.graphics.PointF;
import android.os.Build;
import androidx.annotation.RequiresApi;
import android.view.MotionEvent;
import android.view.View;
import com.pedro.encoder.input.gl.render.filters.AndroidViewFilterRender;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.input.gl.render.filters.object.BaseObjectFilterRender;
import com.pedro.encoder.input.video.CameraHelper;

/**
 * Created by pedro on 9/09/17.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class SpriteGestureController {

  private BaseObjectFilterRender baseObjectFilterRender;
  private AndroidViewFilterRender androidViewFilterRender;
  private float lastDistance;
  private boolean preventMoveOutside = true;

  public SpriteGestureController() {
  }

  public SpriteGestureController(BaseObjectFilterRender sprite) {
    this.baseObjectFilterRender = sprite;
  }

  public SpriteGestureController(AndroidViewFilterRender sprite) {
    this.androidViewFilterRender = sprite;
  }

  public BaseFilterRender getFilterRender() {
    return androidViewFilterRender == null ? baseObjectFilterRender : androidViewFilterRender;
  }

  public void setBaseObjectFilterRender(BaseObjectFilterRender baseObjectFilterRender) {
    this.baseObjectFilterRender = baseObjectFilterRender;
    this.androidViewFilterRender = null;
  }

  public void setBaseObjectFilterRender(AndroidViewFilterRender androidViewFilterRender) {
    this.androidViewFilterRender = androidViewFilterRender;
    this.baseObjectFilterRender = null;
  }

  public void stopListener() {
    this.androidViewFilterRender = null;
    this.baseObjectFilterRender = null;
  }

  public void setPreventMoveOutside(boolean preventMoveOutside) {
    this.preventMoveOutside = preventMoveOutside;
  }

  public boolean spriteTouched(View view, MotionEvent motionEvent) {
    if (baseObjectFilterRender == null && androidViewFilterRender == null) return false;
    float xPercent = motionEvent.getX() * 100 / view.getWidth();
    float yPercent = motionEvent.getY() * 100 / view.getHeight();
    PointF scale;
    PointF position;
    if (baseObjectFilterRender != null) {
      scale = baseObjectFilterRender.getScale();
      position = baseObjectFilterRender.getPosition();
    } else {
      scale = androidViewFilterRender.getScale();
      position = androidViewFilterRender.getPosition();
    }
    boolean xTouched = xPercent >= position.x && xPercent <= position.x + scale.x;
    boolean yTouched = yPercent >= position.y && yPercent <= position.y + scale.y;
    return xTouched && yTouched;
  }

  public void moveSprite(View view, MotionEvent motionEvent) {
    if (baseObjectFilterRender == null && androidViewFilterRender == null) return;
    if (motionEvent.getPointerCount() == 1) {
      float xPercent = motionEvent.getX() * 100 / view.getWidth();
      float yPercent = motionEvent.getY() * 100 / view.getHeight();
      PointF scale;
      if (baseObjectFilterRender != null) {
        scale = baseObjectFilterRender.getScale();
      } else {
        scale = androidViewFilterRender.getScale();
      }
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
        if (baseObjectFilterRender != null) {
          baseObjectFilterRender.setPosition(x, y);
        } else {
          androidViewFilterRender.setPosition(x, y);
        }
      } else {
        if (baseObjectFilterRender != null) {
          baseObjectFilterRender.setPosition(xPercent - scale.x / 2f, yPercent - scale.y / 2f);
        } else {
          androidViewFilterRender.setPosition(xPercent - scale.x / 2f, yPercent - scale.y / 2f);
        }
      }
    }
  }

  public void scaleSprite(MotionEvent motionEvent) {
    if (baseObjectFilterRender == null && androidViewFilterRender == null) return;
    if (motionEvent.getPointerCount() > 1) {
      float distance = CameraHelper.getFingerSpacing(motionEvent);
      float percent = distance >= lastDistance ? 1 : -1;
      PointF scale;
      if (baseObjectFilterRender != null) {
        scale = baseObjectFilterRender.getScale();
      } else {
        scale = androidViewFilterRender.getScale();
      }
      scale.x += percent;
      scale.y += percent;
      if (baseObjectFilterRender != null) {
        baseObjectFilterRender.setScale(scale.x, scale.y);
      } else {
        androidViewFilterRender.setScale(scale.x, scale.y);
      }
      lastDistance = distance;
    }
  }
}
