/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.library.util;

import android.content.Context;
import android.hardware.SensorManager;
import android.view.OrientationEventListener;
import com.pedro.encoder.input.video.CameraHelper;

public class SensorRotationManager {

  private final OrientationEventListener listener;
  private int currentOrientation = -1;

  public interface RotationChangedListener {
    void onRotationChanged(int rotation, boolean isPortrait);
  }

  public SensorRotationManager(
      Context context,
      boolean avoidDuplicated,
      boolean followUI,
      final RotationChangedListener rotationListener
  ) {
    this.listener = new OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
      @Override
      public void onOrientationChanged(int sensorOrientation) {
        final int rotation = ((sensorOrientation + 45) / 90) % 4;
        final int rotationDegrees = rotation * 90;
        if (avoidDuplicated) {
          if (currentOrientation == rotationDegrees) return;
        }
        if (followUI) {
          int uiOrientation = getUiOrientation(context);
          if (uiOrientation != rotationDegrees) return;
        }
        currentOrientation = rotationDegrees;
        boolean isPortrait = rotationDegrees == 0 || rotationDegrees == 180;
        rotationListener.onRotationChanged(rotationDegrees, isPortrait);
      }
    };
  }

  public void start() {
    if (listener.canDetectOrientation()) {
      currentOrientation = -1;
      listener.enable();
    }
  }

  public void stop() {
    listener.disable();
    currentOrientation = -1;
  }

  private int getUiOrientation(Context context) {
    int orientation = CameraHelper.getCameraOrientation(context);
    return orientation == 0 ? 270 : orientation - 90;
  }
}