package com.pedro.encoder.utils;

import android.graphics.PointF;

/**
 * Created by pedro on 18/04/21.
 */
public class FaceParsed {
  private PointF position;
  private PointF scale;

  public FaceParsed(PointF position, PointF scale) {
    this.position = position;
    this.scale = scale;
  }

  public PointF getPosition() {
    return position;
  }

  public void setPosition(PointF position) {
    this.position = position;
  }

  public PointF getScale() {
    return scale;
  }

  public void setScale(PointF scale) {
    this.scale = scale;
  }

  @Override
  public String toString() {
    return "FaceParsed{" +
        "position=" + position +
        ", scale=" + scale +
        '}';
  }
}