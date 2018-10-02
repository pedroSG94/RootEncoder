package com.pedro.encoder.input.video;

import android.content.Context;
import android.view.Surface;
import android.view.WindowManager;

public class CameraHelper {

  //Rotation matrix 0 degrees
  private static final float[] vertex0 = {
      // X, Y, Z, U, V
      -1f, -1f, 0f, 0f, 0f,
      1f, -1f, 0f, 1f, 0f,
      -1f, 1f, 0f, 0f, 1f,
      1f, 1f, 0f, 1f, 1f,
  };

  //Rotation matrix 90 degrees
  private static final float[] vertex90 = {
      // X, Y, Z, U, V
      -1f, -1f, 0f, 1f, 0f,
      1f, -1f, 0f, 1f, 1f,
      -1f, 1f, 0f, 0f, 0f,
      1f, 1f, 0f, 0f, 1f,
  };

  //Rotation matrix 180 degrees
  private static final float[] vertex180 = {
      // X, Y, Z, U, V
      -1f, -1f, 0f, 1f, 1f,
      1f, -1f, 0f, 0f, 1f,
      -1f, 1f, 0f, 1f, 0f,
      1f, 1f, 0f, 0f, 0f,
  };

  //Rotation matrix 270 degrees
  private static final float[] vertex270 = {
      // X, Y, Z, U, V
      -1f, -1f, 0f, 0f, 1f,
      1f, -1f, 0f, 0f, 0f,
      -1f, 1f, 0f, 1f, 1f,
      1f, 1f, 0f, 1f, 0f,
  };

  public static float[] getVertex(int rotation) {
    switch (rotation) {
      case 0:
        return vertex0;
      case 90:
        return vertex90;
      case 180:
        return vertex180;
      case 270:
        return vertex270;
      default:
        return vertex0;
    }
  }

  public static int getCamera1Orientation(Context context) {
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    if (windowManager != null) {
      int orientation = windowManager.getDefaultDisplay().getRotation();
      switch (orientation) {
        case Surface.ROTATION_0: //portrait
          return 90;
        case Surface.ROTATION_90: //landscape
          return 0;
        case Surface.ROTATION_180: //reverse portrait
          return 270;
        case Surface.ROTATION_270: //reverse landscape
          return 180;
        default:
          return 0;
      }
    } else {
      return 0;
    }
  }

  public static int getCamera2Orientation(Context context) {
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    if (windowManager != null) {
      int orientation = windowManager.getDefaultDisplay().getRotation();
      switch (orientation) {
        case Surface.ROTATION_0: //portrait
          return 0;
        case Surface.ROTATION_90: //landscape
          return 270;
        case Surface.ROTATION_180: //reverse portrait
          return 180;
        case Surface.ROTATION_270: //reverse landscape
          return 90;
        default:
          return 0;
      }
    } else {
      return 0;
    }
  }
}
