package com.pedro.encoder.input.gl;

import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class SpriteGestureController
    implements View.OnTouchListener, ScaleGestureDetector.OnScaleGestureListener {

  private ScaleGestureDetector gestureScale;
  private Sprite sprite;
  private UpdateGestureCallback updateGestureCallback;

  public interface UpdateGestureCallback {
    void onUpdate();
  }

  public SpriteGestureController(Sprite sprite, UpdateGestureCallback updateGestureCallback) {
    this.sprite = sprite;
    this.updateGestureCallback = updateGestureCallback;
  }

  public void setListeners(View view) {
    if (view != null) {
      gestureScale = new ScaleGestureDetector(view.getContext(), this);
      view.setOnTouchListener(this);
    }
  }

  public void releaseListeners(View view) {
    if (view != null) view.setOnTouchListener(null);
    gestureScale = null;
  }

  @Override
  public boolean onTouch(View view, MotionEvent motionEvent) {
    if (gestureScale != null) gestureScale.onTouchEvent(motionEvent);
    float xPercent = motionEvent.getX() * 100 / view.getWidth();
    float yPercent = motionEvent.getY() * 100 / view.getHeight();
    PointF scale = sprite.getScale();
    sprite.translate(xPercent - scale.x / 2f, yPercent - scale.y / 2f);
    updateGestureCallback.onUpdate();
    return true;
  }

  @Override
  public boolean onScale(ScaleGestureDetector detector) {
    float factor = detector.getScaleFactor() - 1;
    PointF scale = sprite.getScale();
    float percent = factor >= 0 ? 1 : -1;
    scale.x += percent;
    scale.y += percent;
    sprite.scale(scale.x, scale.y);
    updateGestureCallback.onUpdate();
    return true;
  }

  @Override
  public boolean onScaleBegin(ScaleGestureDetector detector) {
    return true;
  }

  @Override
  public void onScaleEnd(ScaleGestureDetector detector) {
  }
}
