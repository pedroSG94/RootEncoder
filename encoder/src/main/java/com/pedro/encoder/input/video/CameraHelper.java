/*
 * Copyright (C) 2021 pedroSG94.
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

package com.pedro.encoder.input.video;

import android.content.Context;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.WindowManager;

/**
 * Created by pedro on 17/12/18.
 */
public class CameraHelper {

  private static final float[] verticesData = {
      // X, Y, Z, U, V
      -1f, -1f, 0f, 0f, 0f,
      1f, -1f, 0f, 1f, 0f,
      -1f, 1f, 0f, 0f, 1f,
      1f, 1f, 0f, 1f, 1f,
  };

  public static float[] getVerticesData() {
    return verticesData;
  }

  public static int getCameraOrientation(Context context) {
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

  public static boolean isPortrait(Context context) {
    int orientation = getCameraOrientation(context);
    return orientation == 90 || orientation == 270;
  }

  public static float getFingerSpacing(MotionEvent event) {
    float x = event.getX(0) - event.getX(1);
    float y = event.getY(0) - event.getY(1);
    return (float) Math.sqrt(x * x + y * y);
  }

  public enum Facing {
    BACK, FRONT
  }
}
