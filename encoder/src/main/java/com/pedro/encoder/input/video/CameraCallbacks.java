package com.pedro.encoder.input.video;

public interface CameraCallbacks {
  void onCameraChanged(boolean isFrontCamera);
  void onCameraError(String error);
}
