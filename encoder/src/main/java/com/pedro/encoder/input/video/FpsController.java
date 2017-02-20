package com.pedro.encoder.input.video;

import android.hardware.Camera;

/**
 * Created by pedro on 20/02/17.
 */

public class FpsController {

  private int ignoredFps = 0;
  private int cont = 0;

  public FpsController(int fps, Camera camera) {
    int[] fpsCamera = new int[2];
    camera.getParameters().getPreviewFpsRange(fpsCamera);
    ignoredFps = (fpsCamera[0] / 1000) / fps;
  }

  public boolean fpsIsValid(){
    if(cont++ < ignoredFps){
      return false;
    }
    cont = 0;
    return true;
  }
}
