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

package com.pedro.encoder.input.video;

import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static com.pedro.encoder.input.video.CameraHelper.Facing;
import static com.pedro.encoder.input.video.CameraHelper.getFingerSpacing;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.pedro.encoder.input.video.facedetector.FaceDetectorCallback;
import com.pedro.encoder.input.video.facedetector.UtilsKt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Created by pedro on 4/03/17.
 *
 * <p>
 * Class for use surfaceEncoder to buffer encoder.
 * Advantage = you can use all resolutions.
 * Disadvantages = you cant control fps of the stream, because you cant know when the inputSurface
 * was renderer.
 * <p>
 * Note: you can use opengl for surfaceEncoder to buffer encoder on devices 21 < API > 16:
 * https://github.com/google/grafika
 */

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Camera2ApiManager extends CameraDevice.StateCallback {

  private final String TAG = "Camera2ApiManager";

  private CameraDevice cameraDevice;
  private SurfaceView surfaceView;
  private TextureView textureView;
  private Surface surfaceEncoder; //input surfaceEncoder from videoEncoder
  private final CameraManager cameraManager;
  private Handler cameraHandler;
  private CameraCaptureSession cameraCaptureSession;
  private boolean prepared = false;
  private String cameraId = null;
  private CameraHelper.Facing facing = Facing.BACK;
  private CaptureRequest.Builder builderInputSurface;
  private float fingerSpacing = 0;
  private float zoomLevel = 0f;
  private boolean lanternEnable = false;
  private boolean videoStabilizationEnable = false;
  private boolean opticalVideoStabilizationEnable = false;
  private boolean autoFocusEnabled = true;
  private boolean running = false;
  private int fps = 30;
  private final Semaphore semaphore = new Semaphore(0);
  private CameraCallbacks cameraCallbacks;

  public interface ImageCallback {
    void onImageAvailable(Image image);
  }

  private int sensorOrientation = 0;
  private Rect faceSensorScale;
  private FaceDetectorCallback faceDetectorCallback;
  private boolean faceDetectionEnabled = false;
  private int faceDetectionMode;
  private ImageReader imageReader;

  public Camera2ApiManager(Context context) {
    cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
  }

  public void prepareCamera(SurfaceView surfaceView, Surface surface, int fps) {
    this.surfaceView = surfaceView;
    this.surfaceEncoder = surface;
    this.fps = fps;
    prepared = true;
  }

  public void prepareCamera(TextureView textureView, Surface surface, int fps) {
    this.textureView = textureView;
    this.surfaceEncoder = surface;
    this.fps = fps;
    prepared = true;
  }

  public void prepareCamera(Surface surface, int fps) {
    this.surfaceEncoder = surface;
    this.fps = fps;
    prepared = true;
  }

  public void prepareCamera(SurfaceTexture surfaceTexture, int width, int height, int fps) {
    Size optimalResolution = Camera2ResolutionCalculator.INSTANCE.getOptimalResolution(new Size(width, height), getCameraResolutions(facing));
    Log.i(TAG, "optimal resolution set to: " + optimalResolution.getWidth() + "x" + optimalResolution.getHeight());
    surfaceTexture.setDefaultBufferSize(optimalResolution.getWidth(), optimalResolution.getHeight());
    this.surfaceEncoder = new Surface(surfaceTexture);
    this.fps = fps;
    prepared = true;
  }

  public void prepareCamera(SurfaceTexture surfaceTexture, int width, int height, int fps, Facing facing) {
    this.facing = facing;
    prepareCamera(surfaceTexture, width, height, fps);
  }

  public void prepareCamera(SurfaceTexture surfaceTexture, int width, int height, int fps, String cameraId) {
    this.facing = getFacingByCameraId(cameraManager, cameraId);
    prepareCamera(surfaceTexture, width, height, fps);
  }

  public boolean isPrepared() {
    return prepared;
  }

  private void startPreview(CameraDevice cameraDevice) {
    try {
      final List<Surface> listSurfaces = new ArrayList<>();
      Surface preview = addPreviewSurface();
      if (preview != null) listSurfaces.add(preview);
      if (surfaceEncoder != preview && surfaceEncoder != null) listSurfaces.add(surfaceEncoder);
      if (imageReader != null) listSurfaces.add(imageReader.getSurface());
      cameraDevice.createCaptureSession(listSurfaces, new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
          Camera2ApiManager.this.cameraCaptureSession = cameraCaptureSession;
          try {
            CaptureRequest captureRequest = drawSurface(listSurfaces);
            if (captureRequest != null) {
              cameraCaptureSession.setRepeatingRequest(captureRequest,
                  faceDetectionEnabled ? cb : null, cameraHandler);
              Log.i(TAG, "Camera configured");
            } else {
              Log.e(TAG, "Error, captureRequest is null");
            }
          } catch (CameraAccessException | NullPointerException e) {
            Log.e(TAG, "Error", e);
          } catch (IllegalStateException e) {
            reOpenCamera(cameraId != null ? cameraId : "0");
          }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
          cameraCaptureSession.close();
          if (cameraCallbacks != null) cameraCallbacks.onCameraError("Configuration failed");
          Log.e(TAG, "Configuration failed");
        }
      }, cameraHandler);
    } catch (CameraAccessException | IllegalArgumentException e) {
      if (cameraCallbacks != null) {
        cameraCallbacks.onCameraError("Create capture session failed: " + e.getMessage());
      }
      Log.e(TAG, "Error", e);
    } catch (IllegalStateException e) {
      reOpenCamera(cameraId != null ? cameraId : "0");
    }
  }

  private Surface addPreviewSurface() {
    Surface surface = null;
    if (surfaceView != null) {
      surface = surfaceView.getHolder().getSurface();
    } else if (textureView != null) {
      final SurfaceTexture texture = textureView.getSurfaceTexture();
      surface = new Surface(texture);
    }
    return surface;
  }

  private CaptureRequest drawSurface(List<Surface> surfaces) {
    try {
      builderInputSurface = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      for (Surface surface : surfaces) if (surface != null) builderInputSurface.addTarget(surface);
      setModeAuto(builderInputSurface);
      adaptFpsRange(fps, builderInputSurface);
      return builderInputSurface.build();
    } catch (CameraAccessException | IllegalStateException e) {
      Log.e(TAG, "Error", e);
      return null;
    }
  }

  private void setModeAuto(CaptureRequest.Builder builderInputSurface) {
    try {
      builderInputSurface.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    } catch (Exception ignored) { }
  }

  private void adaptFpsRange(int expectedFps, CaptureRequest.Builder builderInputSurface) {
    List<Range<Integer>> fpsRanges = getSupportedFps(null, Facing.BACK);
    if (fpsRanges != null && fpsRanges.size() > 0) {
      Range<Integer> closestRange = fpsRanges.get(0);
      int measure = Math.abs(closestRange.getLower() - expectedFps) + Math.abs(
          closestRange.getUpper() - expectedFps);
      for (Range<Integer> range : fpsRanges) {
        if (CameraHelper.discardCamera2Fps(range, facing)) continue;
        if (range.getLower() <= expectedFps && range.getUpper() >= expectedFps) {
          int curMeasure = Math.abs(((range.getLower() + range.getUpper()) / 2) - expectedFps);
          if (curMeasure < measure) {
            closestRange = range;
            measure = curMeasure;
          } else if (curMeasure == measure) {
            if (Math.abs(range.getUpper() - expectedFps) < Math.abs(closestRange.getUpper() - expectedFps)) {
              closestRange = range;
              measure = curMeasure;
            }
          }
        }
      }
      Log.i(TAG, "fps: " + closestRange.getLower() + " - " + closestRange.getUpper());
      builderInputSurface.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, closestRange);
    }
  }

  public List<Range<Integer>> getSupportedFps(Size size, Facing facing) {
    try {
      CameraCharacteristics characteristics = null;
      try {
        characteristics = getCharacteristicsForFacing(cameraManager, facing);
      } catch (CameraAccessException ignored) { }
      if (characteristics == null) return null;
      Range<Integer>[] fpsSupported = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
      if (size != null) {
        StreamConfigurationMap streamConfigurationMap =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        List<Range<Integer>> list = new ArrayList<>();
        long fd = streamConfigurationMap.getOutputMinFrameDuration(SurfaceTexture.class, size);
        int maxFPS = (int)(10f / Float.parseFloat("0." + fd));
        for (Range<Integer> r : fpsSupported) {
          if (r.getUpper() <= maxFPS) {
            list.add(r);
          }
        }
        return list;
      } else {
        return Arrays.asList(fpsSupported);
      }
    } catch (IllegalStateException e) {
      Log.e(TAG, "Error", e);
      return null;
    }
  }

  public int getLevelSupported() {
    try {
      CameraCharacteristics characteristics = getCameraCharacteristics();
      if (characteristics == null) return -1;
      Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
      if (level == null) return -1;
      return level;
    } catch (IllegalStateException e) {
      Log.e(TAG, "Error", e);
      return -1;
    }
  }

  public void openCamera() {
    openCameraBack();
  }

  public void openCameraBack() {
    openCameraFacing(Facing.BACK);
  }

  public void openCameraFront() {
    openCameraFacing(Facing.FRONT);
  }

  public void openLastCamera() {
    if (cameraId == null) {
      openCameraBack();
    } else {
      openCameraId(cameraId);
    }
  }

  public void setCameraFacing(CameraHelper.Facing cameraFacing) {
    try {
      String cameraId = getCameraIdForFacing(cameraManager, cameraFacing);
      if (cameraId != null) {
        facing = cameraFacing;
        this.cameraId = cameraId;
      }
    } catch (CameraAccessException e) {
      Log.e(TAG, "Error", e);
    }
  }

  public void setCameraId(String cameraId) {
    this.cameraId = cameraId;
  }

  public CameraHelper.Facing getCameraFacing() {
    return facing;
  }

  public Size[] getCameraResolutionsBack() {
    return getCameraResolutions(Facing.BACK);
  }

  public Size[] getCameraResolutionsFront() {
    return getCameraResolutions(Facing.FRONT);
  }

  public Size[] getCameraResolutions(Facing facing) {
    try {
      CameraCharacteristics characteristics = getCharacteristicsForFacing(cameraManager, facing);
      if (characteristics == null) {
        return new Size[0];
      }

      StreamConfigurationMap streamConfigurationMap =
          characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      if (streamConfigurationMap == null) return new Size[0];
      Size[] outputSizes = streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
      return outputSizes != null ? outputSizes : new Size[0];
    } catch (CameraAccessException | NullPointerException e) {
      Log.e(TAG, "Error", e);
      return new Size[0];
    }
  }

  public Size[] getCameraResolutions(String cameraId) {
    try {
      CameraCharacteristics characteristics = getCharacteristicsForId(cameraManager, cameraId);
      if (characteristics == null) {
        return new Size[0];
      }

      StreamConfigurationMap streamConfigurationMap =
          characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      if (streamConfigurationMap == null) return new Size[0];
      Size[] outputSizes = streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
      return outputSizes != null ? outputSizes : new Size[0];
    } catch (CameraAccessException | NullPointerException e) {
      Log.e(TAG, "Error", e);
      return new Size[0];
    }
  }

  @Nullable
  public CameraCharacteristics getCameraCharacteristics() {
    try {
      return cameraId != null ? cameraManager.getCameraCharacteristics(cameraId) : null;
    } catch (CameraAccessException e) {
      Log.e(TAG, "Error", e);
      return null;
    }
  }

  public boolean enableVideoStabilization() {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return false;
    int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
    List<Integer> videoStabilizationList = new ArrayList<>();
    for (int vsMode : modes) {
      videoStabilizationList.add(vsMode);
    }
    if (!videoStabilizationList.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)) {
      Log.e(TAG, "video stabilization unsupported");
      return false;
    }

    if (builderInputSurface != null) {
      builderInputSurface.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
      videoStabilizationEnable = true;
    }
    return videoStabilizationEnable;
  }

  public void disableVideoStabilization() {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return;
    int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
    List<Integer> videoStabilizationList = new ArrayList<>();
    for (int vsMode : modes) {
      videoStabilizationList.add(vsMode);
    }
    if (!videoStabilizationList.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)) {
      Log.e(TAG, "video stabilization unsupported");
      return;
    }
    if (builderInputSurface != null) {
      builderInputSurface.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
      videoStabilizationEnable = false;
    }
  }

  public boolean isVideoStabilizationEnabled() {
    return videoStabilizationEnable;
  }

  public boolean enableOpticalVideoStabilization() {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return false;

    int[] opticalStabilizationModes = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
    List<Integer> opticalStabilizationList = new ArrayList<>();
    for (int vsMode : opticalStabilizationModes) {
      opticalStabilizationList.add(vsMode);
    }

    if (!opticalStabilizationList.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
      Log.e(TAG, "OIS video stabilization unsupported");
      return false;
    }
    if (builderInputSurface != null) {
      builderInputSurface.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
              CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
      opticalVideoStabilizationEnable = true;
    }
    return opticalVideoStabilizationEnable;
  }

  public void disableOpticalVideoStabilization() {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return;
    int[] modes = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
    List<Integer> videoStabilizationList = new ArrayList<>();
    for (int vsMode : modes) {
      videoStabilizationList.add(vsMode);
    }
    if (!videoStabilizationList.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
      Log.e(TAG, "OIS video stabilization unsupported");
      return;
    }
    if (builderInputSurface != null) {
      builderInputSurface.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
              CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
      opticalVideoStabilizationEnable = false;
    }
  }

  public boolean isOpticalStabilizationEnabled() {
    return opticalVideoStabilizationEnable;
  }

  public void setFocusDistance(float distance) {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return;
    if (builderInputSurface != null) {
      try {
        if (distance < 0) distance = 0f; //avoid invalid value
        builderInputSurface.set(CaptureRequest.LENS_FOCUS_DISTANCE, distance);
        cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
            faceDetectionEnabled ? cb : null, null);
      } catch (Exception e) {
        Log.e(TAG, "Error", e);
      }
    }
  }

  public void setExposure(int value) {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return;
    Range<Integer> supportedExposure =
        characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
    if (supportedExposure != null && builderInputSurface != null) {
      if (value > supportedExposure.getUpper()) value = supportedExposure.getUpper();
      if (value < supportedExposure.getLower()) value = supportedExposure.getLower();
      try {
        builderInputSurface.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, value);
        cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
            faceDetectionEnabled ? cb : null, null);
      } catch (Exception e) {
        Log.e(TAG, "Error", e);
      }
    }
  }

  public int getExposure() {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return 0;
    if (builderInputSurface != null) {
      try {
        return builderInputSurface.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION);
      } catch (Exception e) {
        Log.e(TAG, "Error", e);
      }
    }
    return 0;
  }



  public int getMaxExposure() {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return 0;
    Range<Integer> supportedExposure =
        characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
    if (supportedExposure != null) {
      return supportedExposure.getUpper();
    }
    return 0;
  }

  public int getMinExposure() {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return 0;
    Range<Integer> supportedExposure =
        characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
    if (supportedExposure != null) {
      return supportedExposure.getLower();
    }
    return 0;
  }

  public void tapToFocus(MotionEvent event) {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return;
    int pointerId = event.getPointerId(0);
    int pointerIndex = event.findPointerIndex(pointerId);
    // Get the pointer's current position
    float x = event.getX(pointerIndex);
    float y = event.getY(pointerIndex);
    if (x < 100 || y < 100) return;

    Rect touchRect = new Rect((int) (x - 100), (int) (y - 100),
        (int) (x + 100), (int) (y + 100));
    MeteringRectangle focusArea = new MeteringRectangle(touchRect, MeteringRectangle.METERING_WEIGHT_DONT_CARE);
    if (builderInputSurface != null) {
      try {
        //cancel any existing AF trigger (repeated touches, etc.)
        builderInputSurface.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
            faceDetectionEnabled ? cb : null, null);
        builderInputSurface.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusArea});
        builderInputSurface.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        builderInputSurface.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
            faceDetectionEnabled ? cb : null, null);
      } catch (Exception e) {
        Log.e(TAG, "Error", e);
      }
    }
  }

  /**
   * Select camera facing
   *
   * @param selectedCameraFacing - CameraCharacteristics.LENS_FACING_FRONT,
   * CameraCharacteristics.LENS_FACING_BACK,
   * CameraCharacteristics.LENS_FACING_EXTERNAL
   */
  public void openCameraFacing(Facing selectedCameraFacing) {
    try {
      String cameraId = getCameraIdForFacing(cameraManager, selectedCameraFacing);
      if (cameraId != null) {
        openCameraId(cameraId);
      } else {
        Log.e(TAG, "Camera not supported"); // TODO maybe we want to throw some exception here?
      }
    } catch (CameraAccessException e) {
      Log.e(TAG, "Error", e);
    }
  }

  public boolean isLanternSupported() {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return false;
    Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
    if (available == null) return false;
    return available;
  }

  public boolean isLanternEnabled() {
    return lanternEnable;
  }

  /**
   * @required: <uses-permission android:name="android.permission.FLASHLIGHT"/>
   */
  public void enableLantern() throws Exception {
    if (isLanternSupported()) {
      if (builderInputSurface != null) {
        try {
          builderInputSurface.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
          cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
              faceDetectionEnabled ? cb : null, null);
          lanternEnable = true;
        } catch (Exception e) {
          Log.e(TAG, "Error", e);
        }
      }
    } else {
      Log.e(TAG, "Lantern unsupported");
      throw new Exception("Lantern unsupported");
    }
  }

  /**
   * @required: <uses-permission android:name="android.permission.FLASHLIGHT"/>
   */
  public void disableLantern() {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return;
    Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
    if (available == null) return;
    if (available) {
      if (builderInputSurface != null) {
        try {
          builderInputSurface.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
          cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
              faceDetectionEnabled ? cb : null, null);
          lanternEnable = false;
        } catch (Exception e) {
          Log.e(TAG, "Error", e);
        }
      }
    }
  }

  public void enableAutoFocus() {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return;
    int[] supportedFocusModes =
        characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
    if (supportedFocusModes != null) {
      List<Integer> focusModesList = new ArrayList<>();
      for (int i : supportedFocusModes) focusModesList.add(i);
      if (builderInputSurface != null) {
        try {
          if (!focusModesList.isEmpty()) {
            //cancel any existing AF trigger
            builderInputSurface.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
                faceDetectionEnabled ? cb : null, null);
            if (focusModesList.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
              builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE,
                  CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
              cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
                  faceDetectionEnabled ? cb : null, null);
              autoFocusEnabled = true;
            } else if (focusModesList.contains(CaptureRequest.CONTROL_AF_MODE_AUTO)) {
              builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE,
                  CaptureRequest.CONTROL_AF_MODE_AUTO);
              cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
                  faceDetectionEnabled ? cb : null, null);
              autoFocusEnabled = true;
            } else {
              builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE, focusModesList.get(0));
              cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
                  faceDetectionEnabled ? cb : null, null);
              autoFocusEnabled = false;
            }
          }
        } catch (Exception e) {
          Log.e(TAG, "Error", e);
        }
      }
    }
  }

  public void disableAutoFocus() {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return;
    int[] supportedFocusModes =
        characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
    if (supportedFocusModes != null) {
      if (builderInputSurface != null) {
        for (int mode : supportedFocusModes) {
          try {
            if (mode == CaptureRequest.CONTROL_AF_MODE_OFF) {
              builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE,
                  CaptureRequest.CONTROL_AF_MODE_OFF);
              cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
                  faceDetectionEnabled ? cb : null, null);
              autoFocusEnabled = false;
              return;
            }
          } catch (Exception e) {
            Log.e(TAG, "Error", e);
          }
        }
      }
    }
  }

  public boolean isAutoFocusEnabled() {
    return autoFocusEnabled;
  }

  public boolean enableFaceDetection(FaceDetectorCallback faceDetectorCallback) {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) {
      Log.e(TAG, "face detection called with camera stopped");
      return false;
    }
    faceSensorScale = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
    sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
    int[] fd = characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
    if (fd == null || fd.length == 0) {
      Log.e(TAG, "face detection unsupported");
      return false;
    }
    Integer maxFD = characteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
    if (maxFD == null || maxFD <= 0) {
      Log.e(TAG, "face detection unsupported");
      return false;
    }
    List<Integer> fdList = new ArrayList<>();
    for (int FaceD : fd) {
      fdList.add(FaceD);
    }
    this.faceDetectorCallback = faceDetectorCallback;
    faceDetectionEnabled = true;
    faceDetectionMode = Collections.max(fdList);
    setFaceDetect(builderInputSurface, faceDetectionMode);
    prepareFaceDetectionCallback();
    return true;
  }

  public void disableFaceDetection() {
    if (faceDetectionEnabled) {
      faceDetectorCallback = null;
      faceDetectionEnabled = false;
      faceDetectionMode = 0;
      prepareFaceDetectionCallback();
    }
  }

  public boolean isFaceDetectionEnabled() {
    return faceDetectorCallback != null;
  }

  private void setFaceDetect(CaptureRequest.Builder requestBuilder, int faceDetectMode) {
    if (faceDetectionEnabled) {
      requestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, faceDetectMode);
    }
  }

  public void setCameraCallbacks(CameraCallbacks cameraCallbacks) {
    this.cameraCallbacks = cameraCallbacks;
  }

  private void prepareFaceDetectionCallback() {
    try {
      cameraCaptureSession.stopRepeating();
      cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
          faceDetectionEnabled ? cb : null, null);
    } catch (CameraAccessException e) {
      Log.e(TAG, "Error", e);
    }
  }

  private final CameraCaptureSession.CaptureCallback cb =
      new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
            @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
          Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
          if (faceDetectorCallback != null && faces != null) {
            faceDetectorCallback.onGetFaces(UtilsKt.mapCamera2Faces(faces), faceSensorScale, sensorOrientation);
          }
        }
      };

  @SuppressLint("MissingPermission")
  public void openCameraId(String cameraId) {
    this.cameraId = cameraId;
    if (prepared) {
      HandlerThread cameraHandlerThread = new HandlerThread(TAG + " Id = " + cameraId);
      cameraHandlerThread.start();
      cameraHandler = new Handler(cameraHandlerThread.getLooper());
      try {
        cameraManager.openCamera(cameraId, this, cameraHandler);
        semaphore.acquireUninterruptibly();
        CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
        running = true;
        Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing == null) return;
        this.facing = LENS_FACING_FRONT == facing ? CameraHelper.Facing.FRONT : CameraHelper.Facing.BACK;
        if (cameraCallbacks != null) {
          cameraCallbacks.onCameraChanged(this.facing);
        }
      } catch (CameraAccessException | SecurityException e) {
        if (cameraCallbacks != null) {
          cameraCallbacks.onCameraError("Open camera " + cameraId + " failed");
        }
        Log.e(TAG, "Error", e);
      }
    } else {
      Log.e(TAG, "Camera2ApiManager need be prepared, Camera2ApiManager not enabled");
    }
  }

  public String[] getCamerasAvailable() {
    try {
      return cameraManager.getCameraIdList();
    } catch (CameraAccessException e) {
      return null;
    }
  }

  public boolean isRunning() {
    return running;
  }

  public void switchCamera() {
    try {
      String cameraId;
      if (cameraDevice == null || facing == Facing.FRONT) {
        cameraId = getCameraIdForFacing(cameraManager, Facing.BACK);
      } else {
        cameraId = getCameraIdForFacing(cameraManager, Facing.FRONT);
      }
      if (cameraId == null) cameraId = "0";
      reOpenCamera(cameraId);
    } catch (CameraAccessException e) {
      Log.e(TAG, "Error", e);
    }
  }

  public void reOpenCamera(String cameraId) {
    if (cameraDevice != null) {
      closeCamera(false);
      if (textureView != null) {
        prepareCamera(textureView, surfaceEncoder, fps);
      } else if (surfaceView != null) {
        prepareCamera(surfaceView, surfaceEncoder, fps);
      } else {
        prepareCamera(surfaceEncoder, fps);
      }
      openCameraId(cameraId);
    }
  }

  public Range<Float> getZoomRange() {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return new Range<>(1f, 1f);
    Range<Float> zoomRanges = null;
    //only camera limited or better support this feature.
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
        getLevelSupported() != CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
      zoomRanges = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
    }
    if (zoomRanges == null) {
      Float maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
      if (maxZoom == null) maxZoom = 1f;
      zoomRanges = new Range<>(1f, maxZoom);
    }
    return zoomRanges;
  }

  public Float getZoom() {
    return zoomLevel;
  }

  public float[] getOpticalZooms() {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return null;
    return characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
  }

  public void setOpticalZoom(float level) {
    CameraCharacteristics characteristics = getCameraCharacteristics();
    if (characteristics == null) return;
    if (builderInputSurface != null) {
      try {
        builderInputSurface.set(CaptureRequest.LENS_FOCAL_LENGTH, level);
        cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
            faceDetectionEnabled ? cb : null, null);
      } catch (Exception e) {
        Log.e(TAG, "Error", e);
      }
    }
  }

  public void setZoom(float level) {
    try {
      Range<Float> zoomRange = getZoomRange();
      //Avoid out range level
      if (level <= zoomRange.getLower()) level = zoomRange.getLower();
      else if (level > zoomRange.getUpper()) level = zoomRange.getUpper();

      CameraCharacteristics characteristics = getCameraCharacteristics();
      if (characteristics == null) return;

      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
          getLevelSupported() != CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
        builderInputSurface.set(CaptureRequest.CONTROL_ZOOM_RATIO, level);
      } else {
        Rect rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (rect == null) return;
        //This ratio is the ratio of cropped Rect to Camera's original(Maximum) Rect
        float ratio = 1f / level;
        //croppedWidth and croppedHeight are the pixels cropped away, not pixels after cropped
        int croppedWidth = rect.width() - Math.round((float) rect.width() * ratio);
        int croppedHeight = rect.height() - Math.round((float) rect.height() * ratio);
        //Finally, zoom represents the zoomed visible area
        Rect zoom = new Rect(croppedWidth / 2, croppedHeight / 2, rect.width() - croppedWidth / 2,
            rect.height() - croppedHeight / 2);
        builderInputSurface.set(CaptureRequest.SCALER_CROP_REGION, zoom);
      }
      cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
          faceDetectionEnabled ? cb : null, null);
      zoomLevel = level;
    } catch (CameraAccessException e) {
      Log.e(TAG, "Error", e);
    }
  }

  public void setZoom(MotionEvent event) {
    float currentFingerSpacing;
    if (event.getPointerCount() > 1) {
      currentFingerSpacing = getFingerSpacing(event);
      float delta = 0.1f;
      if (fingerSpacing != 0) {
        float newLevel = zoomLevel;
        if (currentFingerSpacing > fingerSpacing) {
          newLevel += delta;
        } else if (currentFingerSpacing < fingerSpacing) {
          newLevel -= delta;
        }
        //This method avoid out of range
        setZoom(newLevel);
      }
      fingerSpacing = currentFingerSpacing;
    }
  }

  private void resetCameraValues() {
    lanternEnable = false;
    zoomLevel = 1.0f;
  }

  public void stopRepeatingEncoder() {
    if (cameraCaptureSession != null) {
      try {
        cameraCaptureSession.stopRepeating();
        surfaceEncoder = null;
        Surface preview = addPreviewSurface();
        if (preview != null) {
          CaptureRequest captureRequest = drawSurface(Collections.singletonList(preview));
          if (captureRequest != null) {
            cameraCaptureSession.setRepeatingRequest(captureRequest, null, cameraHandler);
          }
        } else {
          Log.e(TAG, "preview surface is null");
        }
      } catch (CameraAccessException | IllegalStateException e) {
        Log.e(TAG, "Error", e);
      }
    }
  }

  public void closeCamera() {
    closeCamera(true);
  }

  public void closeCamera(boolean resetSurface) {
    resetCameraValues();
    if (cameraCaptureSession != null) {
      cameraCaptureSession.close();
      cameraCaptureSession = null;
    }
    if (cameraDevice != null) {
      cameraDevice.close();
      cameraDevice = null;
    }
    if (cameraHandler != null) {
      cameraHandler.getLooper().quitSafely();
      cameraHandler = null;
    }
    if (resetSurface) {
      surfaceEncoder = null;
      builderInputSurface = null;
    }
    prepared = false;
    running = false;
  }

  public void addImageListener(int width, int height, int format, int maxImages, boolean autoClose, ImageCallback listener) {
    boolean wasRunning = running;
    closeCamera(false);
    if (wasRunning) closeCamera(false);
    if (imageReader != null) removeImageListener();
    HandlerThread imageThread = new HandlerThread(TAG + " imageThread");
    imageThread.start();
    imageReader = ImageReader.newInstance(width, height, format, maxImages);
    imageReader.setOnImageAvailableListener(reader -> {
      Image image = reader.acquireLatestImage();
      if (image != null) {
        listener.onImageAvailable(image);
        if (autoClose) image.close();
      }
    }, new Handler(imageThread.getLooper()));
    if (wasRunning) {
      if (textureView != null) {
        prepareCamera(textureView, surfaceEncoder, fps);
      } else if (surfaceView != null) {
        prepareCamera(surfaceView, surfaceEncoder, fps);
      } else {
        prepareCamera(surfaceEncoder, fps);
      }
      openLastCamera();
    }
  }

  public void removeImageListener() {
    boolean wasRunning = running;
    if (wasRunning) closeCamera(false);
    if (imageReader != null) {
      imageReader.close();
      imageReader = null;
    }
    if (wasRunning) {
      if (textureView != null) {
        prepareCamera(textureView, surfaceEncoder, fps);
      } else if (surfaceView != null) {
        prepareCamera(surfaceView, surfaceEncoder, fps);
      } else {
        prepareCamera(surfaceEncoder, fps);
      }
      openLastCamera();
    }
  }

  @Override
  public void onOpened(@NonNull CameraDevice cameraDevice) {
    this.cameraDevice = cameraDevice;
    startPreview(cameraDevice);
    semaphore.release();
    if (cameraCallbacks != null) cameraCallbacks.onCameraOpened();
    Log.i(TAG, "Camera opened");
  }

  @Override
  public void onDisconnected(@NonNull CameraDevice cameraDevice) {
    cameraDevice.close();
    semaphore.release();
    if (cameraCallbacks != null) cameraCallbacks.onCameraDisconnected();
    Log.i(TAG, "Camera disconnected");
  }

  @Override
  public void onError(@NonNull CameraDevice cameraDevice, int i) {
    cameraDevice.close();
    semaphore.release();
    if (cameraCallbacks != null) cameraCallbacks.onCameraError("Open camera failed: " + i);
    Log.e(TAG, "Open failed: " + i);
  }

  @Nullable
  private String getCameraIdForFacing(CameraManager cameraManager, CameraHelper.Facing facing)
      throws CameraAccessException {
    int selectedFacing = getFacing(facing);
    for (String cameraId : cameraManager.getCameraIdList()) {
      Integer cameraFacing =
          cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING);
      if (cameraFacing != null && cameraFacing == selectedFacing) {
        return cameraId;
      }
    }
    return null;
  }

  private Facing getFacingByCameraId(CameraManager cameraManager, String cameraId) {
    try {
      for (String id : cameraManager.getCameraIdList()) {
        if (id.equals(cameraId)) {
          Integer cameraFacing = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING);
          if (cameraFacing == CameraMetadata.LENS_FACING_BACK) return Facing.BACK;
          else return Facing.FRONT;
        }
      }
      return Facing.BACK;
    } catch (CameraAccessException e) {
      return Facing.BACK;
    }
  }

  @Nullable
  public String getCameraIdForFacing(CameraHelper.Facing facing) {
    try {
      return getCameraIdForFacing(cameraManager, facing);
    } catch (Exception e) {
      return null;
    }
  }

  @Nullable
  private CameraCharacteristics getCharacteristicsForFacing(CameraManager cameraManager,
      CameraHelper.Facing facing) throws CameraAccessException {
    String cameraId = getCameraIdForFacing(cameraManager, facing);
    return getCharacteristicsForId(cameraManager, cameraId);
  }

  @Nullable
  private CameraCharacteristics getCharacteristicsForId(CameraManager cameraManager,
      String cameraId) throws CameraAccessException {
    return cameraId != null ? cameraManager.getCameraCharacteristics(cameraId) : null;
  }

  private static int getFacing(CameraHelper.Facing facing) {
    return facing == CameraHelper.Facing.BACK ? CameraMetadata.LENS_FACING_BACK
        : CameraMetadata.LENS_FACING_FRONT;
  }
}