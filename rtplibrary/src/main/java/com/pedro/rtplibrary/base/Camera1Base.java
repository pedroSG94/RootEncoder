package com.pedro.rtplibrary.base;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PointF;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAacData;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.video.Camera1ApiManager;
import com.pedro.encoder.input.video.Camera1Facing;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.encoder.input.video.EffectManager;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.utils.gl.TextStreamObject;
import com.pedro.encoder.utils.gl.GifStreamObject;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.TranslateTo;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetH264Data;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtplibrary.view.OpenGlView;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pedro on 7/07/17.
 */

public abstract class Camera1Base
    implements GetAacData, GetCameraData, GetH264Data, GetMicrophoneData {

  private static final String TAG = "Camera1Base";

  private Context context;
  protected Camera1ApiManager cameraManager;
  protected VideoEncoder videoEncoder;
  protected MicrophoneManager microphoneManager;
  protected AudioEncoder audioEncoder;
  private OpenGlView openGlView;
  private boolean streaming;
  private boolean videoEnabled = true;
  //record
  private MediaMuxer mediaMuxer;
  private int videoTrack = -1;
  private int audioTrack = -1;
  private boolean recording = false;
  private boolean canRecord = false;
  private boolean onPreview = false;
  private MediaFormat videoFormat;
  private MediaFormat audioFormat;

  public Camera1Base(SurfaceView surfaceView) {
    cameraManager = new Camera1ApiManager(surfaceView, this);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
    streaming = false;
  }

  public Camera1Base(TextureView textureView) {
    cameraManager = new Camera1ApiManager(textureView, this);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
    streaming = false;
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public Camera1Base(OpenGlView openGlView) {
    context = openGlView.getContext();
    this.openGlView = openGlView;
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
    streaming = false;
  }

  public abstract void setAuthorization(String user, String password);

  public boolean prepareVideo(int width, int height, int fps, int bitrate, boolean hardwareRotation,
      int rotation) {
    if (onPreview) {
      stopPreview();
      onPreview = true;
    }
    int imageFormat = ImageFormat.NV21; //supported nv21 and yv12
    if (openGlView == null) {
      cameraManager.prepareCamera(width, height, fps, imageFormat);
      videoEncoder.setImageFormat(imageFormat);
      return videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation,
          hardwareRotation, FormatVideoEncoder.YUV420Dynamical);
    } else {
      return videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation,
          hardwareRotation, FormatVideoEncoder.SURFACE);
    }
  }

  protected abstract void prepareAudioRtp(boolean isStereo, int sampleRate);

  public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) {
    microphoneManager.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor);
    prepareAudioRtp(isStereo, sampleRate);
    return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo);
  }

  public boolean prepareVideo() {
    if (onPreview) {
      stopPreview();
      onPreview = true;
    }
    if (openGlView == null) {
      cameraManager.prepareCamera();
      return videoEncoder.prepareVideoEncoder();
    } else {
      int orientation = 0;
      if (context.getResources().getConfiguration().orientation == 1) {
        orientation = 90;
      }
      return videoEncoder.prepareVideoEncoder(640, 480, 30, 1200 * 1024, orientation, false,
          FormatVideoEncoder.SURFACE);
    }
  }

  public boolean prepareAudio() {
    microphoneManager.createMicrophone();
    return audioEncoder.prepareAudioEncoder();
  }

  /*Need be called while stream*/
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void startRecord(String path) throws IOException {
    if (streaming) {
      mediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      if (videoFormat != null) {
        videoTrack = mediaMuxer.addTrack(videoFormat);
      }
      if (audioFormat != null) {
        audioTrack = mediaMuxer.addTrack(audioFormat);
      }
      mediaMuxer.start();
      recording = true;
    } else {
      throw new IOException("Need be called while stream");
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void stopRecord() {
    recording = false;
    canRecord = false;
    if (mediaMuxer != null) {
      mediaMuxer.stop();
      mediaMuxer.release();
      mediaMuxer = null;
    }
    videoTrack = -1;
    audioTrack = -1;
  }

  public void startPreview(@Camera1Facing int cameraFacing, int width, int height) {
    if (!isStreaming() && !onPreview) {
      if (openGlView != null && Build.VERSION.SDK_INT >= 18) {
        openGlView.startGLThread();
        cameraManager =
            new Camera1ApiManager(openGlView.getSurfaceTexture(), openGlView.getContext());
      }
      cameraManager.prepareCamera();
      if (width == 0 || height == 0) {
        cameraManager.start(cameraFacing);
      } else {
        cameraManager.start(cameraFacing, width, height);
      }
      onPreview = true;
    } else {
      Log.e(TAG, "Streaming or preview started, ignored");
    }
  }

  public void startPreview(@Camera1Facing int cameraFacing) {
    startPreview(cameraFacing, 0, 0);
  }

  public void startPreview(int width, int height) {
    startPreview(Camera.CameraInfo.CAMERA_FACING_BACK, width, height);
  }

  public void startPreview() {
    startPreview(Camera.CameraInfo.CAMERA_FACING_BACK);
  }

  public void stopPreview() {
    if (!isStreaming() && onPreview) {
      if (openGlView != null && Build.VERSION.SDK_INT >= 18) {
        openGlView.stopGlThread();
      }
      cameraManager.stop();
      onPreview = false;
    } else {
      Log.e(TAG, "Streaming or preview stopped, ignored");
    }
  }

  public void setPreviewOrientation(int orientation) {
    cameraManager.setPreviewOrientation(orientation);
  }

  protected abstract void startStreamRtp(String url);

  public void startStream(String url) {
    if (openGlView != null && Build.VERSION.SDK_INT >= 18) {
      if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
        openGlView.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
      } else {
        openGlView.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
      }
      openGlView.startGLThread();
      openGlView.addMediaCodecSurface(videoEncoder.getInputSurface());
      cameraManager =
          new Camera1ApiManager(openGlView.getSurfaceTexture(), openGlView.getContext());
      cameraManager.prepareCamera(videoEncoder.getWidth(), videoEncoder.getHeight(),
          videoEncoder.getFps(), ImageFormat.NV21);
    }
    startStreamRtp(url);
    videoEncoder.start();
    audioEncoder.start();
    cameraManager.start();
    microphoneManager.start();
    streaming = true;
    onPreview = true;
  }

  protected abstract void stopStreamRtp();

  public void stopStream() {
    microphoneManager.stop();
    stopStreamRtp();
    videoEncoder.stop();
    audioEncoder.stop();
    if (openGlView != null && Build.VERSION.SDK_INT >= 18) {
      openGlView.stopGlThread();
      openGlView.removeMediaCodecSurface();
    }
    streaming = false;
  }

  public List<String> getResolutionsBack() {
    List<Camera.Size> list = cameraManager.getPreviewSizeBack();
    List<String> resolutions = new ArrayList<>();
    for (Camera.Size size : list) {
      resolutions.add(size.width + "X" + size.height);
    }
    return resolutions;
  }

  public List<String> getResolutionsFront() {
    List<Camera.Size> list = cameraManager.getPreviewSizeFront();
    List<String> resolutions = new ArrayList<>();
    for (Camera.Size size : list) {
      resolutions.add(size.width + "X" + size.height);
    }
    return resolutions;
  }

  public void disableAudio() {
    microphoneManager.mute();
  }

  public void enableAudio() {
    microphoneManager.unMute();
  }

  public boolean isAudioMuted() {
    return microphoneManager.isMuted();
  }

  public boolean isVideoEnabled() {
    return videoEnabled;
  }

  public void disableVideo() {
    videoEncoder.startSendBlackImage();
    videoEnabled = false;
  }

  public void enableVideo() {
    videoEncoder.stopSendBlackImage();
    videoEnabled = true;
  }

  public void switchCamera() throws CameraOpenException {
    if (isStreaming() || onPreview) {
      cameraManager.switchCamera();
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void setGifStreamObject(GifStreamObject gifStreamObject) throws RuntimeException {
    if (openGlView != null) {
      openGlView.setGif(gifStreamObject);
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set a gif");
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void setImageStreamObject(ImageStreamObject imageStreamObject) throws RuntimeException {
    if (openGlView != null) {
      openGlView.setImage(imageStreamObject);
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set an image");
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void setTextStreamObject(TextStreamObject textStreamObject) throws RuntimeException {
    if (openGlView != null) {
      openGlView.setText(textStreamObject);
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set a text");
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void clearStreamObject() throws RuntimeException {
    if (openGlView != null) {
      openGlView.clear();
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set a text");
    }
  }

  /**
   * @param alpha of the stream object on fly, 1.0f totally opaque and 0.0f totally transparent
   * @throws RuntimeException
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void setAlphaStreamObject(float alpha) throws RuntimeException {
    if (openGlView != null) {
      openGlView.setStreamObjectAlpha(alpha);
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set an alpha");
    }
  }

  /**
   * @param sizeX of the stream object in percent: 100 full screen to 1
   * @param sizeY of the stream object in percent: 100 full screen to 1
   * @throws RuntimeException
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void setSizeStreamObject(float sizeX, float sizeY) throws RuntimeException {
    if (openGlView != null) {
      openGlView.setStreamObjectSize(sizeX, sizeY);
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set a size");
    }
  }

  /**
   * @param x of the stream object in percent: 100 full screen left to 0 full right
   * @param y of the stream object in percent: 100 full screen top to 0 full bottom
   * @throws RuntimeException
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void setPositionStreamObject(float x, float y) throws RuntimeException {
    if (openGlView != null) {
      openGlView.setStreamObjectPosition(x, y);
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set a position");
    }
  }

  /**
   * @param translateTo pre determinate positions
   * @throws RuntimeException
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void setPositionStreamObject(TranslateTo translateTo) throws RuntimeException {
    if (openGlView != null) {
      openGlView.setStreamObjectPosition(translateTo);
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set a position");
    }
  }

  /**
   * @return scale in percent, 0 is stream not started
   * @throws RuntimeException
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public PointF getSizeStreamObject() throws RuntimeException {
    if (openGlView != null) {
      return openGlView.getScale();
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to get position");
    }
  }

  /**
   * @return position in percent, 0 is stream not started
   * @throws RuntimeException
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public PointF getPositionStreamObject() throws RuntimeException {
    if (openGlView != null) {
      return openGlView.getPosition();
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to get scale");
    }
  }

  /** need min API 19 */
  @RequiresApi(api = Build.VERSION_CODES.KITKAT)
  public void setVideoBitrateOnFly(int bitrate) {
    videoEncoder.setVideoBitrateOnFly(bitrate);
  }

  public boolean isStreaming() {
    return streaming;
  }

  public boolean isRecording() {
    return recording;
  }

  public void setEffect(EffectManager effect) {
    if (isStreaming()) {
      cameraManager.setEffect(effect);
    }
  }

  protected abstract void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

  @Override
  public void getAacData(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
        && recording
        && audioTrack != -1
        && canRecord) {
      mediaMuxer.writeSampleData(audioTrack, aacBuffer, info);
    }
    getAacDataRtp(aacBuffer, info);
  }

  protected abstract void onSPSandPPSRtp(ByteBuffer sps, ByteBuffer pps);

  @Override
  public void onSPSandPPS(ByteBuffer sps, ByteBuffer pps) {
    onSPSandPPSRtp(sps, pps);
  }

  protected abstract void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

  @Override
  public void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
        && recording
        && videoTrack != -1) {
      if (info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) canRecord = true;
      if (canRecord) {
        mediaMuxer.writeSampleData(videoTrack, h264Buffer, info);
      }
    }
    getH264DataRtp(h264Buffer, info);
  }

  @Override
  public void inputPCMData(byte[] buffer, int size) {
    audioEncoder.inputPCMData(buffer, size);
  }

  @Override
  public void inputYUVData(byte[] buffer) {
    videoEncoder.inputYUVData(buffer);
  }

  @Override
  public void onVideoFormat(MediaFormat mediaFormat) {
    videoFormat = mediaFormat;
  }

  @Override
  public void onAudioFormat(MediaFormat mediaFormat) {
    audioFormat = mediaFormat;
  }
}