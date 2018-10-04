package com.pedro.rtplibrary.view;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.hardware.Camera;
import android.view.View;

public class FaceDetector {

  public PointF camera1FaceParse(Camera.Face face, View view, PointF scale, boolean isFrontCamera) {
    RectF rect = mapCameraCoord(face, view, isFrontCamera);
    float xPercent = rect.centerX() * 100 / view.getWidth();
    float yPercent = rect.centerY() * 100 / view.getHeight();
    return new PointF((100 - yPercent) - scale.x / 2,
        (100 - xPercent) - scale.y / 2);
  }

  private RectF mapCameraCoord(Camera.Face face, View view, boolean isFrontCamera) {
    RectF rectF = new RectF(face.rect);
    Matrix matrix = new Matrix();
    matrix.setScale(isFrontCamera ? 1 : -1, 1);
    matrix.postScale(view.getWidth() / 2000f, view.getHeight() / 2000f);
    matrix.postTranslate(view.getWidth() / 2f, view.getHeight() / 2f);
    matrix.mapRect(rectF);
    return rectF;
  }
}
