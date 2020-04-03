package com.pedro.rtplibrary.util;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class RotationSensor implements SensorEventListener {

  private SensorManager sensorManager;
  private Context activity;
  private GetRotation getRotation;

  public interface GetRotation {
    void getRotation(int rotation);
  }

  public RotationSensor(Context activity, GetRotation getRotation) {
    this.activity = activity;
    this.getRotation = getRotation;
  }

  public void prepare() {
    sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
  }

  public void resume() {
    sensorManager.registerListener(this,
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
        SensorManager.SENSOR_DELAY_NORMAL);
  }

  public void pause() {
    sensorManager.unregisterListener(this);
  }

  @Override
  public void onSensorChanged(SensorEvent sensorEvent) {
    if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
      getAccelerometer(sensorEvent);
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int i) {

  }

  private void getAccelerometer(SensorEvent event) {
    float[] values = event.values;
    // Movement
    float x = values[0];
    float y = values[1];
    float z = values[2];
    float accelationSquareRoot = (x * x + y * y + z * z)
        / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH);
    Log.e("Pedro", "x: " + x + ", y: " + y + ", z:" + z + ", ASR: " + accelationSquareRoot);
    if (Math.abs(y) < 5) { // horizontal
      getRotation.getRotation(270);
    } else {
      getRotation.getRotation(0);
    }
  }
}