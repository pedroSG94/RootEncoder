package com.pedro.encoder.input.video;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.opengl.GLES20;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import com.pedro.encoder.utils.YUVUtil;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * Created by pedro on 20/01/17.
 * This class need use same resolution, fps and imageFormat that VideoEncoder
 * Tested with YV12 and NV21.
 *
 * Advantage = you can control fps of the stream.
 * Disadvantages = you cant use all resolutions, only resolution that your camera support.
 *
 * If you want use all resolutions. You can use libYuv for resize images in OnPreviewFrame:
 * https://chromium.googlesource.com/libyuv/libyuv/
 */

public class Camera1ApiManager implements Camera.PreviewCallback {

  private String TAG = "Camera1ApiManagerGl";
  private Camera camera = null;
  private SurfaceView surfaceView;
  private TextureView textureView;
  private SurfaceTexture surfaceTexture;
  private GetCameraData getCameraData;
  private boolean running = false;
  private boolean prepared = false;
  private boolean lanternEnable = false;
  private int cameraSelect;
  private boolean isFrontCamera = false;

  //default parameters for camera
  private int width = 640;
  private int height = 480;
  private int fps = 30;
  private int orientation = 0;
  private int imageFormat = ImageFormat.NV21;
  private FpsController fpsController;

  public Camera1ApiManager(SurfaceView surfaceView, GetCameraData getCameraData) {
    this.surfaceView = surfaceView;
    this.getCameraData = getCameraData;
    if (surfaceView.getContext().getResources().getConfiguration().orientation == 1) {
      orientation = 90;
    }
    cameraSelect = selectCamera();
  }

  public Camera1ApiManager(TextureView textureView, GetCameraData getCameraData) {
    this.textureView = textureView;
    this.getCameraData = getCameraData;
    if (textureView.getContext().getResources().getConfiguration().orientation == 1) {
      orientation = 90;
    }
    cameraSelect = selectCamera();
  }

  public Camera1ApiManager(SurfaceTexture surfaceTexture, Context context) {
    this.surfaceTexture = surfaceTexture;
    if (context.getResources().getConfiguration().orientation == 1) {
      orientation = 90;
    }
    cameraSelect = selectCamera();
  }

  public void prepareCamera(int width, int height, int fps, int imageFormat) {
    this.width = width;
    this.height = height;
    this.fps = fps;
    this.imageFormat = imageFormat;
    prepared = true;
  }

  public void prepareCamera() {
    prepareCamera(width, height, fps, imageFormat);
  }

  public void start() {
    if (camera == null && prepared) {
      try {
        camera = Camera.open(cameraSelect);
        if (!checkCanOpen()) {
          throw new CameraOpenException("This camera resolution cant be opened");
        }
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraSelect, info);
        isFrontCamera = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;

        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewSize(width, height);
        parameters.setPreviewFormat(imageFormat);
        int[] range = adaptFpsRange(fps, parameters.getSupportedPreviewFpsRange());
        parameters.setPreviewFpsRange(range[0], range[1]);

        List<String> supportedFocusModes = parameters.getSupportedFocusModes();
        if (supportedFocusModes != null && !supportedFocusModes.isEmpty()) {
          if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
          } else if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            camera.autoFocus(null);
          } else {
            parameters.setFocusMode(supportedFocusModes.get(0));
          }
        }

        camera.setParameters(parameters);
        camera.setDisplayOrientation(orientation);
        if (surfaceView != null) {
          camera.setPreviewDisplay(surfaceView.getHolder());
          camera.setPreviewCallback(this);
        } else if (textureView != null){
          camera.setPreviewTexture(textureView.getSurfaceTexture());
          camera.setPreviewCallback(this);
        } else {
          camera.setPreviewTexture(surfaceTexture);
        }
        camera.startPreview();
        running = true;
        fpsController = new FpsController(fps, camera);
        Log.i(TAG, width + "X" + height);
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      Log.e(TAG, "Camera1ApiManager need be prepared, Camera1ApiManager not enabled");
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
      if (surfaceView != null) {
        clearSurface(surfaceView.getHolder());
      } else if (textureView != null){
        clearSurface(textureView.getSurfaceTexture());
      } else  {
        clearSurface(surfaceTexture);
      }
      running = false;
      prepared = false;
    }
  }

  /**
   * clear data from surface using opengl
   */
  private void clearSurface(Object texture) {
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

  public boolean isPrepared() {
    return prepared;
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
        getCameraData.inputYv12Data(data);
      } else if (imageFormat == ImageFormat.NV21) {
        if (isFrontCamera) data = YUVUtil.rotateNV21(data, width, height, 180);
        getCameraData.inputNv21Data(data);
      }
    }
  }

  /**
   * See: https://developer.android.com/reference/android/graphics/ImageFormat.html to know name of
   * constant values
   * Example: 842094169 -> YV12, 17 -> NV21
   */
  public List<Integer> getCameraPreviewImageFormatSupported() {
    List<Integer> formats;
    if (camera != null) {
      formats = camera.getParameters().getSupportedPreviewFormats();
      for (Integer i : formats) {
        Log.i(TAG, "camera format supported: " + i);
      }
    } else {
      camera = Camera.open(cameraSelect);
      formats = camera.getParameters().getSupportedPreviewFormats();
      camera.release();
      camera = null;
    }
    return formats;
  }

  public List<Camera.Size> getPreviewSize() {
    List<Camera.Size> previewSizes;
    Camera.Size maxSize;
    if (camera != null) {
      maxSize = getMaxEncoderSizeSupported();
      previewSizes = camera.getParameters().getSupportedPreviewSizes();
    } else {
      camera = Camera.open(cameraSelect);
      maxSize = getMaxEncoderSizeSupported();
      previewSizes = camera.getParameters().getSupportedPreviewSizes();
      camera.release();
      camera = null;
    }
    //discard preview more high than device can record
    Iterator<Camera.Size> iterator = previewSizes.iterator();
    while (iterator.hasNext()) {
      Camera.Size size = iterator.next();
      if (size.width > maxSize.width || size.height > maxSize.height) {
        Log.i(TAG, size.width + "X" + size.height + ", not supported for encoder");
        iterator.remove();
      }
    }
    return previewSizes;
  }

  /**
   * @return max size that device can record.
   */
  private Camera.Size getMaxEncoderSizeSupported() {
    if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_2160P)) {
      return camera.new Size(3840, 2160);
    } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P)) {
      return camera.new Size(1920, 1080);
    } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
      return camera.new Size(1280, 720);
    } else {
      return camera.new Size(640, 480);
    }
  }

  public void setEffect(EffectManager effect) {
    if (camera != null) {
      Camera.Parameters parameters = camera.getParameters();
      parameters.setColorEffect(effect.getEffect());
      try {
        camera.setParameters(parameters);
      } catch (RuntimeException e) {
        Log.e(TAG, "Unsupported effect: ", e);
      }
    }
  }

  public void switchCamera() throws CameraOpenException {
    if (camera != null) {
      int number = Camera.getNumberOfCameras();
      for (int i = 0; i < number; i++) {
        if (cameraSelect != i) {
          cameraSelect = i;
          stop();
          prepared = true;
          start();
          return;
        }
      }
    }
  }

  private boolean checkCanOpen() {
    for (Camera.Size size : getPreviewSize()) {
      if (size.width == width && size.height == height) {
        return true;
      }
    }
    return false;
  }

  public boolean isLanternEnable() {
    return lanternEnable;
  }

  /**
   * @required: <uses-permission android:name="android.permission.FLASHLIGHT"/>
   */
  public void enableLantern() {
    if (camera != null) {
      Camera.Parameters parameters = camera.getParameters();
      List<String> supportedFlashModes = parameters.getSupportedFlashModes();
      if (supportedFlashModes != null && !supportedFlashModes.isEmpty()) {
        if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
          parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
          camera.setParameters(parameters);
          lanternEnable = true;
        } else {
          Log.e(TAG, "Lantern unsupported");
        }
      }
    }
  }

  /**
   * @required: <uses-permission android:name="android.permission.FLASHLIGHT"/>
   */
  public void disableLantern() {
    if (camera != null) {
      Camera.Parameters parameters = camera.getParameters();
      parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
      camera.setParameters(parameters);
      lanternEnable = false;
    }
  }
}
