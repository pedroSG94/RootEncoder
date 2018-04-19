package com.pedro.encoder.input.video;

import android.content.Context;
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
  //private boolean faceDetectionSupported = false;
  //private Integer faceDetectionMode;

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
                  Arrays.asList(drawPreview(preview), drawInputSurface(surfaceEncoder)), null,
                  cameraHandler);
            } else {
              cameraCaptureSession.setRepeatingBurst(
                  Collections.singletonList(drawInputSurface(surfaceEncoder)), null, cameraHandler);
            }
            Log.i(TAG, "camera configured");
          } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();
          }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
          cameraCaptureSession.close();
          Log.e(TAG, "configuration failed");
        }
      }, null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
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
      CaptureRequest.Builder captureRequestBuilder =
          cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      captureRequestBuilder.addTarget(surface);
      captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
      return captureRequestBuilder.build();
    } catch (CameraAccessException e) {
      e.printStackTrace();
      return null;
    }
  }

  private CaptureRequest drawInputSurface(Surface surface) {
    try {
      CaptureRequest.Builder builder =
          cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
      builder.addTarget(surface);
      //setFaceDetect(builder, faceDetectionMode);
      return builder.build();
    } catch (CameraAccessException | IllegalStateException e) {
      Log.e(TAG, e.getMessage());
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
      CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics("0");
      if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
          != CameraCharacteristics.LENS_FACING_BACK) {
        cameraCharacteristics = cameraManager.getCameraCharacteristics("1");
      }
      StreamConfigurationMap streamConfigurationMap =
          cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      return streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
    } catch (CameraAccessException e) {
      Log.e(TAG, e.getMessage());
      return new Size[0];
    }
  }

  public Size[] getCameraResolutionsFront() {
    try {
      CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics("0");
      if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
          != CameraCharacteristics.LENS_FACING_FRONT) {
        cameraCharacteristics = cameraManager.getCameraCharacteristics("1");
      }
      StreamConfigurationMap streamConfigurationMap =
          cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      return streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
    } catch (CameraAccessException e) {
      Log.e(TAG, e.getMessage());
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
      final CameraCharacteristics cameraCharacteristics =
          cameraManager.getCameraCharacteristics("0");
      if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == cameraFacing) {
        openCameraId(0);
      } else {
        openCameraId(1);
      }
      //int[] FD = cameraCharacteristics.get(
      //    CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
      //int maxFD = cameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
      //if (FD.length > 0) {
      //  List<Integer> fdList = new ArrayList<>();
      //  for (int FaceD : FD) {
      //    fdList.add(FaceD);
      //  }
      //  if (maxFD > 0) {
      //    faceDetectionSupported = true;
      //    faceDetectionMode = Collections.max(fdList);
      //  }
      //}
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  //private void setFaceDetect(CaptureRequest.Builder requestBuilder, int faceDetectMode) {
  //  if (faceDetectionSupported) {
  //    requestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, faceDetectMode);
  //  }
  //}

  //new CameraCaptureSession.CaptureCallback() {
  //  @Override
  //  public void onCaptureCompleted(@NonNull CameraCaptureSession session,
  //      @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
  //    Face face[] = result.get(CaptureResult.STATISTICS_FACES);
  //    Log.e("Pedro", "faces: " + face.length);
  //  }
  //}

  public void openCameraId(Integer cameraId) {
    this.cameraId = cameraId;
    if (prepared) {
      HandlerThread cameraHandlerThread = new HandlerThread(TAG + " Id = " + cameraId);
      cameraHandlerThread.start();
      cameraHandler = new Handler(cameraHandlerThread.getLooper());
      try {
        cameraManager.openCamera(cameraId.toString(), this, cameraHandler);
        final CameraCharacteristics cameraCharacteristics =
            cameraManager.getCameraCharacteristics(Integer.toString(cameraId));
        isFrontCamera = (LENS_FACING_FRONT == cameraCharacteristics.get(CameraCharacteristics.LENS_FACING));
      } catch (CameraAccessException | SecurityException e) {
        e.printStackTrace();
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
        e.printStackTrace();
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
    }
  }

  @Override
  public void onOpened(@NonNull CameraDevice cameraDevice) {
    this.cameraDevice = cameraDevice;
    startPreview(cameraDevice);
    Log.i(TAG, "camera opened");
  }

  @Override
  public void onDisconnected(@NonNull CameraDevice cameraDevice) {
    cameraDevice.close();
    Log.i(TAG, "camera disconnected");
  }

  @Override
  public void onError(@NonNull CameraDevice cameraDevice, int i) {
    cameraDevice.close();
    Log.e(TAG, "open failed");
  }
}