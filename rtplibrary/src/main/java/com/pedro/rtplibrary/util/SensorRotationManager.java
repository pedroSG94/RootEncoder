package com.pedro.rtplibrary.util;

import android.content.Context;
import android.hardware.SensorManager;
import android.view.OrientationEventListener;

public class SensorRotationManager {

  private final OrientationEventListener listener;

  public interface RotationChangedListener {
    void onRotationChanged(int rotation);
  }

  public SensorRotationManager(Context context, final RotationChangedListener rotationListener) {
    this.listener = new OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
      @Override
      public void onOrientationChanged(int sensorOrientation) {
        final int rotation = ((sensorOrientation + 45) / 90) % 4;
        final int rotationDegrees = rotation * 90;
        rotationListener.onRotationChanged(rotationDegrees);
      }
    };
  }

  public void start() {
    if (listener.canDetectOrientation()) listener.enable();
  }

  public void stop() {
    listener.disable();
  }
}