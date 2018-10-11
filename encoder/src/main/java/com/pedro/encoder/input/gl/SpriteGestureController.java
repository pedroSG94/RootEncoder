package com.pedro.encoder.input.gl;

import android.graphics.PointF;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.MotionEvent;
import android.view.View;
import com.pedro.encoder.input.gl.render.filters.object.BaseObjectFilterRender;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class SpriteGestureController {

  private BaseObjectFilterRender baseObjectFilterRender;
  private float lastDistance;

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
      baseObjectFilterRender.setPosition(xPercent - scale.x / 2f, yPercent - scale.y / 2f);
    }
  }

  public void scaleSprite(MotionEvent motionEvent) {
    if (baseObjectFilterRender == null) return;
    if (motionEvent.getPointerCount() > 1) {
      float distance = getFingerSpacing(motionEvent);
      float percent = distance >= lastDistance ? 1 : -1;
      PointF scale = baseObjectFilterRender.getScale();
      scale.x += percent;
      scale.y += percent;
      baseObjectFilterRender.setScale(scale.x, scale.y);
      lastDistance = distance;
    }
  }

  private float getFingerSpacing(MotionEvent event) {
    float x = event.getX(0) - event.getX(1);
    float y = event.getY(0) - event.getY(1);
    return (float) Math.sqrt(x * x + y * y);
  }
}
