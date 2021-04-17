package com.pedro.encoder.utils;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.camera2.params.Face;
import android.os.Build;
import androidx.annotation.RequiresApi;

import android.util.Log;
import android.view.View;

import com.pedro.encoder.input.video.CameraHelper;

/**
 * Created by pedro on 17/10/18.
 */

public class FaceDetectorUtil {

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public static FaceParsed camera2Parse(Face face, Rect scaleSensor, int sensorOrientation, int rotation, CameraHelper.Facing facing) {
    return cameraParse(face.getBounds(), scaleSensor, sensorOrientation, rotation, facing, 100);
  }

  public static FaceParsed camera1Parse(Camera.Face face, Rect scaleSensor, int sensorOrientation, int rotation, CameraHelper.Facing facing) {
    return cameraParse(face.rect, scaleSensor, sensorOrientation, rotation, facing, 50);
  }

  /**
   * Parse bounds from camera1 and camera2 api to scale and position used in OpenGlView filters.
   */
  private static FaceParsed cameraParse(Rect face, Rect scaleSensor, int sensorOrientation, int streamRotation,
    CameraHelper.Facing facing, float translate) {
    // Face Detection Matrix
    Matrix matrix = new Matrix();
    // Need mirror for front camera.
    matrix.setScale(facing == CameraHelper.Facing.FRONT ? -1 : 1, 1);
    matrix.postRotate(streamRotation);
    // 100f because we are doing scale value by percent to work with filters.
    float s1 = 100f / scaleSensor.width();
    float s2 = 100f / scaleSensor.height();
    // Camera2 sensor could be rotated. We need check it and fix.
    if (sensorOrientation == 90 || sensorOrientation == 270) {
      matrix.postScale(s2, s1);
    } else {
      matrix.postScale(s1, s2);
    }
    matrix.postTranslate(translate, translate);

    Rect uRect = new Rect(face);
    RectF rectF = new RectF(uRect);
    matrix.mapRect(rectF);
    uRect = new Rect((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
    // Filter is draw from top left corner. Move position from center to corner.
    PointF positionParsed = new PointF(uRect.centerX() - uRect.width() / 2f, uRect.centerY()  - uRect.height() / 2f);
    PointF sizeParsed = new PointF(uRect.width(), uRect.height());
    return new FaceParsed(positionParsed, sizeParsed);
  }
}
