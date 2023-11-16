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

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import com.pedro.encoder.Frame;
import com.pedro.encoder.input.video.facedetector.FaceDetectorCallback;
import com.pedro.encoder.input.video.facedetector.UtilsKt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by pedro on 20/01/17.
 *
 * This class need use same resolution, fps and imageFormat that VideoEncoder
 * Tested with YV12 and NV21.
 * <p>
 * Advantage = you can control fps of the stream.
 * Disadvantages = you cant use all resolutions, only resolution that your camera support.
 * <p>
 * If you want use all resolutions. You can use libYuv for resize images in OnPreviewFrame:
 * https://chromium.googlesource.com/libyuv/libyuv/
 */

public class Camera1ApiManager implements Camera.PreviewCallback, Camera.FaceDetectionListener {

  private String TAG = "Camera1ApiManager";
  private Camera camera = null;
  private SurfaceView surfaceView;
  private TextureView textureView;
  private SurfaceTexture surfaceTexture;
  private GetCameraData getCameraData;
  private boolean running = false;
  private boolean lanternEnable = false;
  private boolean videoStabilizationEnable = false;
  private boolean autoFocusEnabled = false;
  private int cameraSelect;
  private CameraHelper.Facing facing = CameraHelper.Facing.BACK;
  private boolean isPortrait = false;
  private Context context;

  //default parameters for camera
  private int width = 640;
  private int height = 480;
  private int fps = 30;
  private int rotation = 0;
  private int imageFormat = ImageFormat.NV21;
  private byte[] yuvBuffer;
  private List<Camera.Size> previewSizeBack;
  private List<Camera.Size> previewSizeFront;
  private float distance;
  private CameraCallbacks cameraCallbacks;
  private final int focusAreaSize = 100;

  private final int sensorOrientation = 0;
  //Value obtained from Camera.Face documentation api about bounds
  private final Rect faceSensorScale = new Rect(-1000, -1000, 1000, 1000);
  private FaceDetectorCallback faceDetectorCallback;

  public Camera1ApiManager(SurfaceView surfaceView, GetCameraData getCameraData) {
    this.surfaceView = surfaceView;
    this.getCameraData = getCameraData;
    this.context = surfaceView.getContext();
    init();
  }

  public Camera1ApiManager(TextureView textureView, GetCameraData getCameraData) {
    this.textureView = textureView;
    this.getCameraData = getCameraData;
    this.context = textureView.getContext();
    init();
  }

  public Camera1ApiManager(SurfaceTexture surfaceTexture, Context context) {
    this.surfaceTexture = surfaceTexture;
    this.context = context;
    init();
  }

  private void init() {
    cameraSelect = selectCameraBack();
    previewSizeBack = getPreviewSize();
    cameraSelect = selectCameraFront();
    previewSizeFront = getPreviewSize();
  }

  public void setRotation(int rotation) {
    this.rotation = rotation;
  }

  public void setSurfaceTexture(SurfaceTexture surfaceTexture) {
    this.surfaceTexture = surfaceTexture;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public void setCameraFacing(CameraHelper.Facing cameraFacing) {
    facing = cameraFacing;
  }

  public void setCameraSelect(int cameraFacing) {
    cameraSelect = cameraFacing;
  }

  public void start(CameraHelper.Facing cameraFacing, int width, int height, int fps) {
    int facing = cameraFacing == CameraHelper.Facing.BACK ? Camera.CameraInfo.CAMERA_FACING_BACK
        : Camera.CameraInfo.CAMERA_FACING_FRONT;
    this.width = width;
    this.height = height;
    this.fps = fps;
    cameraSelect =
        facing == Camera.CameraInfo.CAMERA_FACING_BACK ? selectCameraBack() : selectCameraFront();
    start();
  }

  public void start(int facing, int width, int height, int fps) {
    this.width = width;
    this.height = height;
    this.fps = fps;
    cameraSelect = facing;
    selectCamera(facing);
    start();
  }

  public void start(int width, int height, int fps) {
    start(cameraSelect, width, height, fps);
  }

  private void start() {
    if (!checkCanOpen()) {
      throw new CameraOpenException("This camera resolution cant be opened");
    }
    yuvBuffer = new byte[width * height * 3 / 2];
    try {
      camera = Camera.open(cameraSelect);
      Camera.CameraInfo info = new Camera.CameraInfo();
      Camera.getCameraInfo(cameraSelect, info);
      facing = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? CameraHelper.Facing.FRONT : CameraHelper.Facing.BACK;
      isPortrait = context.getResources().getConfiguration().orientation
          == Configuration.ORIENTATION_PORTRAIT;
      Camera.Parameters parameters = camera.getParameters();
      parameters.setPreviewSize(width, height);
      parameters.setPreviewFormat(imageFormat);
      int[] range = adaptFpsRange(fps, parameters.getSupportedPreviewFpsRange());
      Log.i(TAG, "fps: " + range[0] + " - " + range[1]);
      parameters.setPreviewFpsRange(range[0], range[1]);

      List<String> supportedFocusModes = parameters.getSupportedFocusModes();
      if (supportedFocusModes != null && !supportedFocusModes.isEmpty()) {
        if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
          parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
          autoFocusEnabled = true;
        } else if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
          parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
          autoFocusEnabled = true;
        } else {
          parameters.setFocusMode(supportedFocusModes.get(0));
          autoFocusEnabled = false;
        }
      }
      camera.setParameters(parameters);
      camera.setDisplayOrientation(rotation);
      if (surfaceView != null) {
        camera.setPreviewDisplay(surfaceView.getHolder());
        camera.addCallbackBuffer(yuvBuffer);
        camera.setPreviewCallbackWithBuffer(this);
      } else if (textureView != null) {
        camera.setPreviewTexture(textureView.getSurfaceTexture());
        camera.addCallbackBuffer(yuvBuffer);
        camera.setPreviewCallbackWithBuffer(this);
      } else {
        camera.setPreviewTexture(surfaceTexture);
      }
      camera.startPreview();
      running = true;
      if (cameraCallbacks != null) {
        cameraCallbacks.onCameraOpened();
        cameraCallbacks.onCameraChanged(facing);
      }
      Log.i(TAG, width + "X" + height);
    } catch (IOException e) {
      if (cameraCallbacks != null) cameraCallbacks.onCameraError("Error: " + e.getMessage());
      Log.e(TAG, "Error", e);
    }
  }

  public void setPreviewOrientation(final int orientation) {
    this.rotation = orientation;
    if (camera != null && running) {
      camera.stopPreview();
      camera.setDisplayOrientation(orientation);
      camera.startPreview();
    }
  }

  public void setZoom(int level) {
    try {
      if (camera != null && running && camera.getParameters() != null && camera.getParameters()
          .isZoomSupported()) {
        android.hardware.Camera.Parameters params = camera.getParameters();
        int maxZoom = params.getMaxZoom();
        if (level > maxZoom) level = maxZoom;
        else if (level < getMinZoom()) level = getMinZoom();
        params.setZoom(level);
        camera.setParameters(params);
      }
    } catch (Exception e) {
      Log.e(TAG, "Error", e);
    }
  }

  public int getZoom() {
    try {
      if (camera != null && running && camera.getParameters() != null && camera.getParameters()
          .isZoomSupported()) {
        android.hardware.Camera.Parameters params = camera.getParameters();
        return params.getZoom();
      } else {
        return getMinZoom();
      }
    } catch (Exception e) {
      Log.e(TAG, "Error", e);
      return getMinZoom();
    }
  }

  public int getMaxZoom() {
    try {
      if (camera != null && running && camera.getParameters() != null && camera.getParameters()
          .isZoomSupported()) {
        android.hardware.Camera.Parameters params = camera.getParameters();
        return params.getMaxZoom();
      } else {
        return getMinZoom();
      }
    } catch (Exception e) {
      Log.e(TAG, "Error", e);
      return getMinZoom();
    }
  }

  public int getMinZoom() { return 0; }

  public void setZoom(MotionEvent event) {
    try {
      if (camera != null && running && camera.getParameters() != null && camera.getParameters()
          .isZoomSupported()) {
        android.hardware.Camera.Parameters params = camera.getParameters();
        int maxZoom = params.getMaxZoom();
        int zoom = params.getZoom();
        float newDist = CameraHelper.getFingerSpacing(event);

        if (newDist > distance) {
          if (zoom < maxZoom) {
            zoom++;
          }
        } else if (newDist < distance) {
          if (zoom > 0) {
            zoom--;
          }
        }

        distance = newDist;
        params.setZoom(zoom);
        camera.setParameters(params);
      }
    } catch (Exception e) {
      Log.e(TAG, "Error", e);
    }
  }

  public void setExposure(int value) {
    if (camera != null && camera.getParameters() != null) {
      android.hardware.Camera.Parameters params = camera.getParameters();
      if (value > params.getMaxExposureCompensation()) value = params.getMaxExposureCompensation();
      else if (value < params.getMinExposureCompensation()) value = params.getMinExposureCompensation();
      params.setExposureCompensation(value);
      camera.setParameters(params);
    }
  }

  public int getExposure() {
    if (camera != null && camera.getParameters() != null) {
      android.hardware.Camera.Parameters params = camera.getParameters();
      return params.getExposureCompensation();
    }
    return 0;
  }

  public int getMaxExposure() {
    if (camera != null && camera.getParameters() != null) {
      android.hardware.Camera.Parameters params = camera.getParameters();
      return params.getMaxExposureCompensation();
    }
    return 0;
  }

  public int getMinExposure() {
    if (camera != null && camera.getParameters() != null) {
      android.hardware.Camera.Parameters params = camera.getParameters();
      return params.getMinExposureCompensation();
    }
    return 0;
  }

  private int selectCameraBack() {
    return selectCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
  }

  private int selectCameraFront() {
    return selectCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
  }

  private int selectCamera(int facing) {
    int number = Camera.getNumberOfCameras();
    for (int i = 0; i < number; i++) {
      Camera.CameraInfo info = new Camera.CameraInfo();
      Camera.getCameraInfo(i, info);
      if (info.facing == facing) return i;
    }
    return 0;
  }

  public void stop() {
    if (camera != null) {
      camera.stopPreview();
      camera.setPreviewCallback(null);
      camera.setPreviewCallbackWithBuffer(null);
      camera.release();
      camera = null;
    }
    running = false;
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
        int curMeasure = Math.abs(((range[0] + range[1]) / 2) - expectedFps);
        if (curMeasure < measure) {
          closestRange = range;
          measure = curMeasure;
        } else if (curMeasure == measure) {
          if (Math.abs(range[0] - expectedFps) < Math.abs(closestRange[1] - expectedFps)) {
            closestRange = range;
            measure = curMeasure;
          }
        }
      }
    }
    return closestRange;
  }

  @Override
  public void onPreviewFrame(byte[] data, Camera camera) {
    long timeStamp = System.nanoTime() / 1000;
    getCameraData.inputYUVData(new Frame(data, rotation, facing == CameraHelper.Facing.FRONT && isPortrait, imageFormat, timeStamp));
    camera.addCallbackBuffer(yuvBuffer);
  }

  public Camera.Size getCameraSize(int width, int height) {
    if (camera != null) {
      return camera.new Size(width, height);
    } else {
      camera = Camera.open(cameraSelect);
      Camera.Size size = camera.new Size(width, height);
      camera.release();
      camera = null;
      return size;
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

  private List<Camera.Size> getPreviewSize() {
    List<Camera.Size> previewSizes;
    Camera.Size maxSize;
    if (camera != null) {
      maxSize = getMaxEncoderSizeSupported(cameraSelect);
      previewSizes = camera.getParameters().getSupportedPreviewSizes();
    } else {
      camera = Camera.open(cameraSelect);
      maxSize = getMaxEncoderSizeSupported(cameraSelect);
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

  public List<Camera.Size> getPreviewSizeBack() {
    return previewSizeBack;
  }

  public List<Camera.Size> getPreviewSizeFront() {
    return previewSizeFront;
  }

  /**
   * @return max size that device can record.
   */
  private Camera.Size getMaxEncoderSizeSupported(int cameraId) {
    if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_2160P)) {
      return camera.new Size(3840, 2160);
    } else if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
      return camera.new Size(1920, 1080);
    } else if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
      return camera.new Size(1280, 720);
    } else {
      return camera.new Size(640, 480);
    }
  }

  public CameraHelper.Facing getCameraFacing() {
    return facing;
  }

  public void switchCamera() throws CameraOpenException {
    if (camera != null) {
      int oldCamera = cameraSelect;
      int number = Camera.getNumberOfCameras();
      for (int i = 0; i < number; i++) {
        if (cameraSelect != i) {
          cameraSelect = i;
          if (!checkCanOpen()) {
            cameraSelect = oldCamera;
            throw new CameraOpenException("This camera resolution cant be opened");
          }
          stop();
          start();
          return;
        }
      }
    }
  }

  public void switchCamera(int cameraId) throws CameraOpenException {
    if (camera != null) {
      int oldCamera = cameraSelect;
      cameraSelect = cameraId;
      if (!checkCanOpen()) {
        cameraSelect = oldCamera;
        throw new CameraOpenException("This camera resolution cant be opened");
      }
      stop();
      start();
      return;
    }
  }

  private boolean checkCanOpen() {
    List<Camera.Size> previews;
    if (cameraSelect == selectCameraBack()) {
      previews = previewSizeBack;
    } else {
      previews = previewSizeFront;
    }
    for (Camera.Size size : previews) {
      if (size.width == width && size.height == height) {
        return true;
      }
    }
    return false;
  }

  public boolean isLanternEnabled() {
    return lanternEnable;
  }

  /**
   * @required: <uses-permission android:name="android.permission.FLASHLIGHT"/>
   */
  public void enableLantern() throws Exception {
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
          throw new Exception("Lantern unsupported");
        }
      }
    }
  }

  public List<int[]> getSupportedFps() {
    List<int[]> supportedFps;
    if (camera != null) {
      supportedFps = camera.getParameters().getSupportedPreviewFpsRange();
    } else {
      camera = Camera.open(cameraSelect);
      supportedFps = camera.getParameters().getSupportedPreviewFpsRange();
      camera.release();
      camera = null;
    }
    for (int[] range : supportedFps) {
      range[0] /= 1000;
      range[1] /= 1000;
    }
    return supportedFps;
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
  
  private Camera.AutoFocusCallback autoFocusTakePictureCallback = new Camera.AutoFocusCallback() {
    @Override
    public void onAutoFocus(boolean success, Camera camera) {
      if (success) {
        Log.i(TAG, "tapToFocus success");
      } else {
        Log.e(TAG, "tapToFocus failed");
      }
    }
  };

  public void tapToFocus(View view, MotionEvent event) {
    if (camera != null && camera.getParameters() != null) {
      Camera.Parameters parameters = camera.getParameters();
      if (parameters.getMaxNumMeteringAreas() > 0) {
        Rect rect = calculateFocusArea(event.getX(), event.getY(), view.getWidth(), view.getHeight());
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        List<Camera.Area> meteringAreas = new ArrayList<>();
        meteringAreas.add(new Camera.Area(rect, 800));
        parameters.setFocusAreas(meteringAreas);
        try {
          camera.setParameters(parameters);
        }catch (Exception e) {
          Log.i(TAG, "tapToFocus error: " + e.getMessage());
        }
      }
      camera.autoFocus(autoFocusTakePictureCallback);
    }
  }

  public void enableAutoFocus() {
    if (camera != null) {
      Camera.Parameters parameters = camera.getParameters();
      List<String> supportedFocusModes = parameters.getSupportedFocusModes();
      if (supportedFocusModes != null && !supportedFocusModes.isEmpty()) {
        if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
          parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
          autoFocusEnabled = true;
        } else if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
          parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
          autoFocusEnabled = true;
        } else {
          autoFocusEnabled = false;
          parameters.setFocusMode(supportedFocusModes.get(0));
        }
      }
      camera.setParameters(parameters);
    }
  }

  public void disableAutoFocus() {
    if (camera != null) {
      Camera.Parameters parameters = camera.getParameters();
      List<String> supportedFocusModes = parameters.getSupportedFocusModes();
      if (supportedFocusModes != null && !supportedFocusModes.isEmpty()) {
        if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
          parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
        } else {
          parameters.setFocusMode(supportedFocusModes.get(0));
        }
      }
      autoFocusEnabled = false;
      camera.setParameters(parameters);
    }
  }

  public boolean isAutoFocusEnabled() {
    return autoFocusEnabled;
  }

  public void enableRecordingHint() {
    if (camera != null) {
      Camera.Parameters parameters = camera.getParameters();
      parameters.setRecordingHint(true);
      camera.setParameters(parameters);
    }
  }

  public void disableRecordingHint() {
    if (camera != null) {
      Camera.Parameters parameters = camera.getParameters();
      parameters.setRecordingHint(false);
      camera.setParameters(parameters);
    }
  }

  public boolean enableFaceDetection(FaceDetectorCallback faceDetectorCallback) {
    try {
      if (camera != null) {
        this.faceDetectorCallback = faceDetectorCallback;
        camera.setFaceDetectionListener(this);
        camera.startFaceDetection();
        return true;
      } else {
        Log.e(TAG, "face detection called with camera stopped");
        return false;
      }
    } catch (IllegalArgumentException e) {
      Log.e(TAG, "face detection unsupported");
      return false;
    }
  }

  public void disableFaceDetection() {
    if (camera != null) {
      faceDetectorCallback = null;
      camera.stopFaceDetection();
      camera.setFaceDetectionListener(null);
    }
  }

  public boolean enableVideoStabilization() {
    if (camera != null) {
      Camera.Parameters parameters = camera.getParameters();
      if (parameters.isVideoStabilizationSupported()) {
        parameters.setVideoStabilization(true);
        videoStabilizationEnable = true;
      }
    }
    return videoStabilizationEnable;
  }

  public void disableVideoStabilization() {
    if (camera != null) {
      Camera.Parameters parameters = camera.getParameters();
      if (parameters.isVideoStabilizationSupported()) {
        parameters.setVideoStabilization(false);
        videoStabilizationEnable = false;
      }
    }
  }

  public boolean isVideoStabilizationEnabled() {
    return videoStabilizationEnable;
  }

  public void setCameraCallbacks(CameraCallbacks cameraCallbacks) {
    this.cameraCallbacks = cameraCallbacks;
  }

  public boolean isFaceDetectionEnabled() {
    return faceDetectorCallback != null;
  }

  @Override
  public void onFaceDetection(Camera.Face[] faces, Camera camera) {
    if (faceDetectorCallback != null) faceDetectorCallback.onGetFaces(UtilsKt.mapCamera1Faces(faces), faceSensorScale, sensorOrientation);
  }

  private Rect calculateFocusArea(float x, float y, float previewWidth, float previewHeight) {
    int left = clamp((int) (x / previewWidth * 2000f - 1000f), focusAreaSize);
    int top = clamp((int) (y / previewHeight * 2000f - 1000f), focusAreaSize);
    return new Rect(left, top, left + focusAreaSize, top + focusAreaSize);
  }

  private int clamp(int touchCoordinateInCameraReper, int focusAreaSize) {
    int result;
    if (Math.abs(touchCoordinateInCameraReper) + focusAreaSize / 2 > 1000){
      if (touchCoordinateInCameraReper > 0){
        result = 1000 - focusAreaSize / 2;
      } else {
        result = -1000 + focusAreaSize / 2;
      }
    } else{
      result = touchCoordinateInCameraReper - focusAreaSize / 2;
    }
    return result;
  }
}
