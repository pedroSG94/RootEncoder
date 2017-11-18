package com.pedro.encoder.input.gl;

import android.graphics.PointF;
import android.graphics.RectF;
import com.pedro.encoder.utils.gl.TranslateTo;

/**
 * Created by pedro on 17/11/17.
 */

class Sprite {
  private float angle;
  private float scale;
  private RectF base;
  private PointF translation;

  public Sprite() {
    base = new RectF(0f, 0f, 1f, 1f);
    // Initial translation
    translation = new PointF(0f, 0f);
    // Initial size
    scale = 5f; //this is 100 / 5 = 20% of the OpenGlView
    // Initial angle
    angle = 0f;
  }

  public void translate(float deltaX, float deltaY) {
    translation.x = deltaX;
    translation.y = deltaY;
  }

  public void translate(TranslateTo translation) {
    float percent = 100 / scale;
    switch (translation) {
      case CENTER:
        this.translation.x -= scale / 2f;
        this.translation.x -= this.translation.x * percent / 100;
        this.translation.y -= scale / 2f;
        this.translation.y -= this.translation.y * percent / 100;
        break;
      case BOTTOM:
        this.translation.x = 0f;
        this.translation.y -= scale / 2f;
        this.translation.y -= this.translation.y * percent / 100;
        break;
      case TOP:
        this.translation.x -= scale - 1;
        this.translation.y -= scale / 2f;
        this.translation.y -= this.translation.y * percent / 100;
        break;
      case LEFT:
        this.translation.x -= scale / 2f;
        this.translation.x -= this.translation.x * percent / 100;
        this.translation.y -= scale - 1;
        break;
      case RIGHT:
        this.translation.x -= scale / 2f;
        this.translation.x -= this.translation.x * percent / 100;
        this.translation.y = 0f;
        break;
      case TOP_LEFT:
        this.translation.x -= scale - 1;
        this.translation.y -= scale - 1;
        break;
      case TOP_RIGHT:
        this.translation.x -= scale - 1;
        this.translation.y = 0f;
        break;
      case BOTTOM_LEFT:
        this.translation.x = 0f;
        this.translation.y -= scale - 1;
        break;
      case BOTTOM_RIGHT:
        this.translation.x = 0f;
        this.translation.y = 0f;
        break;
      default:
        break;
    }
  }

  public void scale(float delta) {
    scale = delta;
  }

  public void rotate(float delta) {
    angle = delta;
  }

  public float[] getTransformedVertices() {
    // Start with scaling
    float x1 = base.left * scale;
    float x2 = base.right * scale;
    float y1 = base.bottom * scale;
    float y2 = base.top * scale;

    // We now detach from our Rect because when rotating,
    // we need the seperate points, so we do so in opengl order
    PointF one = new PointF(x1, y1);
    PointF two = new PointF(x1, y2);
    PointF three = new PointF(x2, y1);
    PointF four = new PointF(x2, y2);

    // We create the sin and cos function once,
    // so we do not have calculate them each time.
    float s = (float) Math.sin(angle);
    float c = (float) Math.cos(angle);

    // Then we rotate each point
    one.x = x1 * c - y1 * s;
    one.y = x1 * s + y1 * c;
    two.x = x1 * c - y2 * s;
    two.y = x1 * s + y2 * c;
    three.x = x2 * c - y1 * s;
    three.y = x2 * s + y1 * c;
    four.x = x2 * c - y2 * s;
    four.y = x2 * s + y2 * c;

    // Finally we translate the sprite to its correct position.
    one.x += translation.x;
    one.y += translation.y;
    two.x += translation.x;
    two.y += translation.y;
    three.x += translation.x;
    three.y += translation.y;
    four.x += translation.x;
    four.y += translation.y;

    // We now return our float array of vertices.
    return new float[] {
        one.x, one.y, two.x, two.y, three.x, three.y, four.x, four.y,
    };
  }
}
