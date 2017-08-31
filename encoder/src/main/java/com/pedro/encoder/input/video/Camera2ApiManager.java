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
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by pedro on 4/03/17.
 * <p>
 * Class for use surface to buffer encoder.
 * Advantage = you can use all resolutions.
 * Disadvantages = you cant control fps of the stream, because you cant know when the inputSurface
 * was renderer.
 * <p>
 * Note: you can use opengl for surface to buffer encoder on devices 21 < API > 16:
 * https://github.com/google/grafika
 */

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Camera2ApiManager extends CameraDevice.StateCallback {

  private final String TAG = "Camera2ApiManager";

  private CameraDevice cameraDevice;
  private SurfaceView surfaceView;
  private TextureView textureView;
  private Surface surface; //input surface from videoEncoder
  private CameraManager cameraManager;
  private Handler cameraHandler;

  public Camera2ApiManager(SurfaceView surfaceView, Surface surface, Context context) {
    this.surfaceView = surfaceView;
    this.surface = surface;
    cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
  }

  public Camera2ApiManager(TextureView textureView, Surface surface, Context context) {
    this.textureView = textureView;
    this.surface = surface;
    cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
  }

  public Camera2ApiManager(Surface surface, Context context) {
    this.surface = surface;
    cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
  }

  private void startPreview(CameraDevice cameraDevice) {
    try {
      List<Surface> listSurfaces = new ArrayList<>();
      final Surface previewSurface = addPreviewSurface();
      if (previewSurface != null) {
        listSurfaces.add(previewSurface);
      }
      listSurfaces.add(surface);
      cameraDevice.createCaptureSession(listSurfaces, new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
          try {
            if (surfaceView != null || textureView != null) {
              cameraCaptureSession.setRepeatingBurst(
                  Arrays.asList(drawPreview(previewSurface), drawInputSurface(surface)), null,
                  cameraHandler);
            } else {
              cameraCaptureSession.setRepeatingBurst(
                  Collections.singletonList(drawInputSurface(surface)), null, cameraHandler);
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
          cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      builder.addTarget(surface);
      return builder.build();
    } catch (CameraAccessException e) {
      Log.e(TAG, e.getMessage());
      return null;
    }
  }

  public void openCamera() {
    openCameraId(0);
  }

  public void openCameraId(Integer cameraId) {
    HandlerThread cameraHandlerThread = new HandlerThread(TAG + " Id = " + cameraId);
    cameraHandlerThread.start();
    cameraHandler = new Handler(cameraHandlerThread.getLooper());
    try {
      cameraManager.openCamera(cameraId.toString(), this, cameraHandler);
    } catch (CameraAccessException | SecurityException e) {
      e.printStackTrace();
    }
  }

  public void openCameraFront() {
    try {
      if (cameraManager.getCameraCharacteristics("0").get(CameraCharacteristics.LENS_FACING)
          == CameraCharacteristics.LENS_FACING_FRONT) {
        openCameraId(0);
      } else {
        openCameraId(1);
      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  public void openCameraBack() {
    try {
      if (cameraManager.getCameraCharacteristics("0").get(CameraCharacteristics.LENS_FACING)
          == CameraCharacteristics.LENS_FACING_BACK) {
        openCameraId(0);
      } else {
        openCameraId(1);
      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  public void switchCamera() {
    if (cameraDevice != null) {
      int cameraId = Integer.parseInt(cameraDevice.getId()) == 1 ? 0 : 1;
      closeCamera();
      openCameraId(cameraId);
    }
  }

  public void closeCamera() {
    if (cameraDevice != null) {
      cameraDevice.close();
      cameraDevice = null;
    }
    cameraHandler.getLooper().quitSafely();
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