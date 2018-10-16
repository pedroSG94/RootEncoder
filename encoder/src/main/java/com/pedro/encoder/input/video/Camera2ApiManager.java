package com.pedro.encoder.input.video;

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
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;

/**
 * Created by pedro on 4/03/17.
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
  private CameraManager cameraManager;
  private Handler cameraHandler;
  private CameraCaptureSession cameraCaptureSession;
  private boolean prepared = false;
  private int cameraId = -1;
  private Surface preview;
  private boolean isOpenGl = false;
  private boolean isFrontCamera = false;
  private CameraCharacteristics cameraCharacteristics;
  private CaptureRequest.Builder builderPreview;
  private CaptureRequest.Builder builderInputSurface;
  private float fingerSpacing = 0;
  private int zoomLevel = 1;

  //Face detector
  public interface FaceDetectorCallback {
    void onGetFaces(Face[] faces);
  }

  private FaceDetectorCallback faceDetectorCallback;
  private boolean faceDetectionEnabled = false;
  private int faceDetectionMode;

  public Camera2ApiManager(Context context) {
    cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
  }

  public void prepareCamera(SurfaceView surfaceView, Surface surface) {
    this.surfaceView = surfaceView;
    this.surfaceEncoder = surface;
    prepared = true;
    isOpenGl = false;
  }

  public void prepareCamera(TextureView textureView, Surface surface) {
    this.textureView = textureView;
    this.surfaceEncoder = surface;
    prepared = true;
    isOpenGl = false;
  }

  public void prepareCamera(Surface surface) {
    this.surfaceEncoder = surface;
    prepared = true;
    isOpenGl = false;
  }

  public void prepareCamera(SurfaceTexture surfaceTexture, int width, int height) {
    surfaceTexture.setDefaultBufferSize(width, height);
    this.surfaceEncoder = new Surface(surfaceTexture);
    prepared = true;
    isOpenGl = true;
  }

  public boolean isPrepared() {
    return prepared;
  }

  private void startPreview(CameraDevice cameraDevice) {
    try {
      List<Surface> listSurfaces = new ArrayList<>();
      preview = addPreviewSurface();
      if (preview != null) {
        listSurfaces.add(preview);
      }
      if (surfaceEncoder != null) {
        listSurfaces.add(surfaceEncoder);
      }
      cameraDevice.createCaptureSession(listSurfaces, new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
          Camera2ApiManager.this.cameraCaptureSession = cameraCaptureSession;
          try {
            if (surfaceView != null || textureView != null) {
              cameraCaptureSession.setRepeatingBurst(
                  Arrays.asList(drawPreview(preview), drawInputSurface(surfaceEncoder)),
                  faceDetectionEnabled ? cb : null, cameraHandler);
            } else {
              cameraCaptureSession.setRepeatingBurst(
                  Collections.singletonList(drawInputSurface(surfaceEncoder)),
                  faceDetectionEnabled ? cb : null, cameraHandler);
            }
            Log.i(TAG, "Camera configured");
          } catch (CameraAccessException | NullPointerException e) {
            Log.e(TAG, "Error", e);
          }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
          cameraCaptureSession.close();
          Log.e(TAG, "Configuration failed");
        }
      }, null);
    } catch (CameraAccessException e) {
      Log.e(TAG, "Error", e);
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

  private CaptureRequest drawPreview(Surface surface) {
    try {
      builderPreview = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      builderPreview.addTarget(surface);
      builderPreview.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
      return builderPreview.build();
    } catch (CameraAccessException e) {
      Log.e(TAG, "Error", e);
      return null;
    }
  }

  private CaptureRequest drawInputSurface(Surface surface) {
    try {
      builderInputSurface = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      builderInputSurface.addTarget(surface);
      return builderInputSurface.build();
    } catch (CameraAccessException | IllegalStateException e) {
      Log.e(TAG, "Error", e);
      return null;
    }
  }

  public void openCamera() {
    openCameraBack();
  }

  public void openCameraBack() {
    openCameraFacing(LENS_FACING_BACK);
  }

  public void openCameraFront() {
    openCameraFacing(LENS_FACING_FRONT);
  }

  public void openLastCamera() {
    if (cameraId == -1) {
      openCameraBack();
    } else {
      openCameraId(cameraId);
    }
  }

  public Size[] getCameraResolutionsBack() {
    try {
      cameraCharacteristics = cameraManager.getCameraCharacteristics("0");
      if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
          != CameraCharacteristics.LENS_FACING_BACK) {
        cameraCharacteristics = cameraManager.getCameraCharacteristics("1");
      }
      StreamConfigurationMap streamConfigurationMap =
          cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      return streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
    } catch (CameraAccessException e) {
      Log.e(TAG, "Error", e);
      return new Size[0];
    }
  }

  public Size[] getCameraResolutionsFront() {
    try {
      cameraCharacteristics = cameraManager.getCameraCharacteristics("0");
      if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
          != CameraCharacteristics.LENS_FACING_FRONT) {
        cameraCharacteristics = cameraManager.getCameraCharacteristics("1");
      }
      StreamConfigurationMap streamConfigurationMap =
          cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      return streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
    } catch (CameraAccessException e) {
      Log.e(TAG, "Error", e);
      return new Size[0];
    }
  }

  /**
   * Select camera facing
   *
   * @param cameraFacing - CameraCharacteristics.LENS_FACING_FRONT, CameraCharacteristics.LENS_FACING_BACK,
   * CameraCharacteristics.LENS_FACING_EXTERNAL
   */
  public void openCameraFacing(@Camera2Facing int cameraFacing) {
    try {
      cameraCharacteristics =
          cameraManager.getCameraCharacteristics("0");
      if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == cameraFacing) {
        openCameraId(0);
      } else {
        openCameraId(1);
      }
    } catch (CameraAccessException e) {
      Log.e(TAG, "Error", e);
    }
  }

  public void enableFaceDetection(FaceDetectorCallback faceDetectorCallback) {
    int[] fd = cameraCharacteristics.get(
        CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
    int maxFD = cameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
    if (fd.length > 0) {
      List<Integer> fdList = new ArrayList<>();
      for (int FaceD : fd) {
        fdList.add(FaceD);
      }
      if (maxFD > 0) {
        this.faceDetectorCallback = faceDetectorCallback;
        faceDetectionEnabled = true;
        faceDetectionMode = Collections.max(fdList);
        if (builderPreview != null) setFaceDetect(builderPreview, faceDetectionMode);
        setFaceDetect(builderInputSurface, faceDetectionMode);
        prepareFaceDetectionCallback();
      } else {
        Log.e(TAG, "No face detection");
      }
    } else {
      Log.e(TAG, "No face detection");
    }
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

  private void prepareFaceDetectionCallback() {
    try {
      cameraCaptureSession.stopRepeating();
      if (builderPreview != null) {
        cameraCaptureSession.setRepeatingRequest(builderPreview.build(),
            faceDetectionEnabled ? cb : null, null);
      }
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
          if (faceDetectorCallback != null) {
            faceDetectorCallback.onGetFaces(faces);
          }
        }
      };

  @SuppressLint("MissingPermission")
  public void openCameraId(Integer cameraId) {
    this.cameraId = cameraId;
    if (prepared) {
      HandlerThread cameraHandlerThread = new HandlerThread(TAG + " Id = " + cameraId);
      cameraHandlerThread.start();
      cameraHandler = new Handler(cameraHandlerThread.getLooper());
      try {
        cameraManager.openCamera(cameraId.toString(), this, cameraHandler);
        cameraCharacteristics = cameraManager.getCameraCharacteristics(Integer.toString(cameraId));
        isFrontCamera =
            (LENS_FACING_FRONT == cameraCharacteristics.get(CameraCharacteristics.LENS_FACING));
      } catch (CameraAccessException | SecurityException e) {
        Log.e(TAG, "Error", e);
      }
    } else {
      Log.e(TAG, "Camera2ApiManager need be prepared, Camera2ApiManager not enabled");
    }
  }

  public void switchCamera() {
    if (cameraDevice != null) {
      int cameraId = Integer.parseInt(cameraDevice.getId()) == 1 ? 0 : 1;
      closeCamera(false);
      prepared = true;
      openCameraId(cameraId);
    }
  }

  public void setZoom(MotionEvent event) {
    try {
      float maxZoom =
          (cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)) * 10;
      Rect m = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
      float currentFingerSpacing;

      if (event.getPointerCount() > 1) {
        // Multi touch logic
        currentFingerSpacing = CameraHelper.getFingerSpacing(event);
        if (fingerSpacing != 0) {
          if (currentFingerSpacing > fingerSpacing && maxZoom > zoomLevel) {
            zoomLevel++;
          } else if (currentFingerSpacing < fingerSpacing && zoomLevel > 1) {
            zoomLevel--;
          }
          int minW = (int) (m.width() / maxZoom);
          int minH = (int) (m.height() / maxZoom);
          int difW = m.width() - minW;
          int difH = m.height() - minH;
          int cropW = difW / 100 * zoomLevel;
          int cropH = difH / 100 * zoomLevel;
          cropW -= cropW & 3;
          cropH -= cropH & 3;
          Rect zoom = new Rect(cropW, cropH, m.width() - cropW, m.height() - cropH);
          if (builderPreview != null) builderPreview.set(CaptureRequest.SCALER_CROP_REGION, zoom);
          builderInputSurface.set(CaptureRequest.SCALER_CROP_REGION, zoom);
        }
        fingerSpacing = currentFingerSpacing;
      }
      if (builderPreview != null) {
        cameraCaptureSession.setRepeatingRequest(builderPreview.build(),
            faceDetectionEnabled ? cb : null, null);
      }
      cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
          faceDetectionEnabled ? cb : null, null);
    } catch (CameraAccessException e) {
      Log.e(TAG, "Error", e);
    }
  }

  public boolean isFrontCamera() {
    return isFrontCamera;
  }

  public void closeCamera(boolean reOpen) {
    if (reOpen) {
      try {
        cameraCaptureSession.stopRepeating();
        if (surfaceView != null || textureView != null) {
          cameraCaptureSession.setRepeatingBurst(Collections.singletonList(drawPreview(preview)),
              null, cameraHandler);
        } else if (surfaceEncoder != null && isOpenGl) {
          cameraCaptureSession.setRepeatingBurst(
              Collections.singletonList(drawPreview(surfaceEncoder)), null, cameraHandler);
        }
      } catch (Exception e) {
        Log.e(TAG, "Error", e);
      }
    } else {
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
      prepared = false;
      builderPreview = null;
      builderInputSurface = null;
    }
  }

  @Override
  public void onOpened(@NonNull CameraDevice cameraDevice) {
    this.cameraDevice = cameraDevice;
    startPreview(cameraDevice);
    Log.i(TAG, "Camera opened");
  }

  @Override
  public void onDisconnected(@NonNull CameraDevice cameraDevice) {
    cameraDevice.close();
    Log.i(TAG, "Camera disconnected");
  }

  @Override
  public void onError(@NonNull CameraDevice cameraDevice, int i) {
    cameraDevice.close();
    Log.e(TAG, "Open failed");
  }
}