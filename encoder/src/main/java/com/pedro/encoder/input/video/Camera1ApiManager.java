package com.pedro.encoder.input.video;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.List;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * Created by pedro on 20/01/17.
 * This class need use same resolution, fps and rotation that VideoEncoder
 * Tested with YV12 and NV21.
 *
 * Advantage = you can control fps of the stream.
 * Disadvantages = you cant use all resolutions, only resolution that your camera support.
 *
 * If you want use all resolutions. You can use libYuv for resize images in OnPreviewFrame:
 * https://chromium.googlesource.com/libyuv/libyuv/
 */

public class Camera1ApiManager implements Camera.PreviewCallback {

  private String TAG = "Camera1ApiManager";
  private Camera camera = null;
  private SurfaceView surfaceView;
  private GetCameraData getCameraData;
  private boolean running = false;
  private boolean lanternEnable = false;
  private int cameraSelect;

  //default parameters for camera
  private int width = 1280;
  private int height = 720;
  private int fps = 30;
  private int orientation = 90;
  private int imageFormat = ImageFormat.NV21;
  private FpsController fpsController;

  public Camera1ApiManager(SurfaceView surfaceView, GetCameraData getCameraData) {
    this.surfaceView = surfaceView;
    this.getCameraData = getCameraData;
    cameraSelect = selectCamera();
  }

  public void prepareCamera(int width, int height, int fps, int orientation, int imageFormat) {
    this.width = width;
    this.height = height;
    this.fps = fps;
    this.orientation = orientation;
    this.imageFormat = imageFormat;
  }

  public void prepareCamera() {
    prepareCamera(width, height, fps, orientation, imageFormat);
  }

  public void start() {
    if (camera == null) {
      try {
        camera = Camera.open(cameraSelect);
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewSize(width, height);
        parameters.setPreviewFormat(imageFormat);
        int[] range = adaptFpsRange(fps, parameters.getSupportedPreviewFpsRange());
        parameters.setPreviewFpsRange(range[0], range[1]);
        camera.setParameters(parameters);
        camera.setDisplayOrientation(orientation);
        camera.setPreviewDisplay(surfaceView.getHolder());
        camera.setPreviewCallback(this);
        camera.startPreview();
        running = true;
        fpsController = new FpsController(fps, camera);
        Log.i(TAG, width + "X" + height);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private int selectCamera() {
    int number = Camera.getNumberOfCameras();
    for (int i = 0; i < number; i++) {
      Camera.CameraInfo info = new Camera.CameraInfo();
      Camera.getCameraInfo(i, info);
      if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
        return i;
      } else {
        cameraSelect = i;
      }
    }
    return cameraSelect;
  }

  public void stop() {
    if (camera != null) {
      camera.setPreviewCallback(null);
      camera.stopPreview();
      camera.release();
      camera = null;
      clearSurface(surfaceView.getHolder().getSurface());
      running = false;
    }
  }

  /**
   * clear data from surface using opengl
   */
  private void clearSurface(Surface texture) {
    EGL10 egl = (EGL10) EGLContext.getEGL();
    EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
    egl.eglInitialize(display, null);

    int[] attribList = {
        EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
        EGL10.EGL_ALPHA_SIZE, 8, EGL10.EGL_RENDERABLE_TYPE, EGL10.EGL_WINDOW_BIT, EGL10.EGL_NONE, 0,
        // placeholder for recordable [@-3]
        EGL10.EGL_NONE
    };
    EGLConfig[] configs = new EGLConfig[1];
    int[] numConfigs = new int[1];
    egl.eglChooseConfig(display, attribList, configs, configs.length, numConfigs);
    EGLConfig config = configs[0];
    EGLContext context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, new int[] {
        12440, 2, EGL10.EGL_NONE
    });
    EGLSurface eglSurface = egl.eglCreateWindowSurface(display, config, texture, new int[] {
        EGL10.EGL_NONE
    });

    egl.eglMakeCurrent(display, eglSurface, eglSurface, context);
    GLES20.glClearColor(0, 0, 0, 1);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    egl.eglSwapBuffers(display, eglSurface);
    egl.eglDestroySurface(display, eglSurface);
    egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
    egl.eglDestroyContext(display, context);
    egl.eglTerminate(display);
  }

  public boolean isRunning() {
    return running;
  }

  private int[] adaptFpsRange(int expectedFps, List<int[]> fpsRanges) {
    expectedFps *= 1000;
    int[] closestRange = fpsRanges.get(0);
    int measure = Math.abs(closestRange[0] - expectedFps) + Math.abs(closestRange[1] - expectedFps);
    for (int[] range : fpsRanges) {
      if (range[0] <= expectedFps && range[1] >= expectedFps) {
        int curMeasure = Math.abs(range[0] - expectedFps) + Math.abs(range[1] - expectedFps);
        if (curMeasure < measure) {
          closestRange = range;
          measure = curMeasure;
        }
      }
    }
    return closestRange;
  }

  @Override
  public void onPreviewFrame(byte[] data, Camera camera) {
    if (fpsController.fpsIsValid()) {
      if (imageFormat == ImageFormat.YV12) {
        getCameraData.inputYv12Data(data, width, height);
      } else if (imageFormat == ImageFormat.NV21) {
        getCameraData.inputNv21Data(data, width, height);
      }
    }
  }

  /**
   * call it after start()
   * See: https://developer.android.com/reference/android/graphics/ImageFormat.html to know name of
   * constant values
   * Example: 842094169 -> YV12, 17 -> NV21
   */
  public List<Integer> getCameraPreviewImageFormatSupported() {
    if(camera != null) {
      List<Integer> formats = camera.getParameters().getSupportedPreviewFormats();
      for (Integer i : formats) {
        Log.i(TAG, "camera format supported: " + i);
      }
      return formats;
    } else{
      return null;
    }
  }

  /**
   * call if after start()
   */
  public List<Camera.Size> getPreviewSize() {
    if(camera != null) {
      List<Camera.Size> previewSizes = camera.getParameters().getSupportedPreviewSizes();
      for (Camera.Size size : previewSizes) {
        Log.i(TAG, size.width + "X" + size.height);
      }
      return previewSizes;
    } else{
      return null;
    }
  }

  public void setEffect(EffectManager effect) {
    if(camera != null) {
      Camera.Parameters parameters = camera.getParameters();
      parameters.setColorEffect(effect.getEffect());
      try {
        camera.setParameters(parameters);
      } catch (RuntimeException e) {
        Log.e(TAG, "Unsupported effect");
        e.printStackTrace();
      }
    }
  }

  public void switchCamera() {
    if (camera != null) {
      int number = Camera.getNumberOfCameras();
      for (int i = 0; i < number; i++) {
        if (cameraSelect != i) {
          cameraSelect = i;
          stop();
          start();
          return;
        }
      }
    }
  }

  public boolean isLanternEnable() {
    return lanternEnable;
  }

  /**
   * @required: <uses-permission android:name="android.permission.FLASHLIGHT"/>
   */
  public void enableLantern() {
    Camera.Parameters parameters = camera.getParameters();
    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
    try {
      camera.setParameters(parameters);
      lanternEnable = true;
    } catch (RuntimeException e) {
      Log.e(TAG, "lantern unsupported");
      e.printStackTrace();
    }
  }

  /**
   * @required: <uses-permission android:name="android.permission.FLASHLIGHT"/>
   */
  public void disableLantern() {
    Camera.Parameters parameters = camera.getParameters();
    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
    try {
      camera.setParameters(parameters);
      lanternEnable = false;
    } catch (RuntimeException e) {
      Log.e(TAG, "lantern unsupported");
      e.printStackTrace();
    }
  }
}
