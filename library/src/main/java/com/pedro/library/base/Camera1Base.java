/*
 * Copyright (C) 2024 pedroSG94.
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

package com.pedro.library.base;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.pedro.common.AudioCodec;
import com.pedro.common.VideoCodec;
import com.pedro.encoder.EncoderErrorCallback;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAudioData;
import com.pedro.encoder.input.audio.CustomAudioEffect;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.audio.MicrophoneManagerManual;
import com.pedro.encoder.input.audio.MicrophoneMode;
import com.pedro.encoder.input.video.Camera1ApiManager;
import com.pedro.encoder.input.video.CameraCallbacks;
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.input.video.facedetector.FaceDetectorCallback;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetVideoData;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.library.base.recording.BaseRecordController;
import com.pedro.library.base.recording.RecordController;
import com.pedro.library.util.AndroidMuxerRecordController;
import com.pedro.library.util.FpsListener;
import com.pedro.library.util.streamclient.StreamBaseClient;
import com.pedro.library.view.GlInterface;
import com.pedro.library.view.GlStreamInterface;
import com.pedro.library.view.OpenGlView;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Wrapper to stream with camera1 api and microphone. Support stream with SurfaceView, TextureView
 * and OpenGlView(Custom SurfaceView that use OpenGl). SurfaceView and TextureView use buffer to
 * buffer encoding mode for H264 and OpenGlView use Surface to buffer mode(This mode is generally
 * better because skip buffer processing).
 *
 * API requirements:
 * SurfaceView and TextureView mode: API 16+.
 * OpenGlView: API 18+.
 *
 * Created by pedro on 7/07/17.
 */

public abstract class Camera1Base {

  private static final String TAG = "Camera1Base";

  private final Context context;
  private final Camera1ApiManager cameraManager;
  protected VideoEncoder videoEncoder;
  private MicrophoneManager microphoneManager;
  private AudioEncoder audioEncoder;
  private GlInterface glInterface;
  private boolean streaming = false;
  protected boolean audioInitialized = false;
  private boolean onPreview = false;
  protected BaseRecordController recordController;
  private int previewWidth, previewHeight;
  private final FpsListener fpsListener = new FpsListener();

  public Camera1Base(SurfaceView surfaceView) {
    context = surfaceView.getContext();
    cameraManager = new Camera1ApiManager(surfaceView, getCameraData);
    init();
  }

  public Camera1Base(TextureView textureView) {
    context = textureView.getContext();
    cameraManager = new Camera1ApiManager(textureView, getCameraData);
    init();
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public Camera1Base(OpenGlView openGlView) {
    context = openGlView.getContext();
    this.glInterface = openGlView;
    cameraManager = new Camera1ApiManager(null, context);
    init();
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public Camera1Base(Context context) {
    this.context = context;
    glInterface = new GlStreamInterface(context);
    cameraManager = new Camera1ApiManager(null, context);
    init();
  }

  private void init() {
    videoEncoder = new VideoEncoder(getVideoData);
    setMicrophoneMode(MicrophoneMode.ASYNC);
    recordController = new AndroidMuxerRecordController();
  }

  /**
   * Must be called before prepareAudio.
   *
   * @param microphoneMode mode to work accord to audioEncoder. By default ASYNC:
   * SYNC using same thread. This mode could solve choppy audio or audio frame discarded.
   * ASYNC using other thread.
   */
  public void setMicrophoneMode(MicrophoneMode microphoneMode) {
    switch (microphoneMode) {
      case SYNC:
        microphoneManager = new MicrophoneManagerManual();
        audioEncoder = new AudioEncoder(getAudioData);
        audioEncoder.setGetFrame(((MicrophoneManagerManual) microphoneManager).getGetFrame());
        audioEncoder.setTsModeBuffer(false);
        break;
      case ASYNC:
        microphoneManager = new MicrophoneManager(getMicrophoneData);
        audioEncoder = new AudioEncoder(getAudioData);
        audioEncoder.setTsModeBuffer(false);
        break;
      case BUFFER:
        microphoneManager = new MicrophoneManager(getMicrophoneData);
        audioEncoder = new AudioEncoder(getAudioData);
        audioEncoder.setTsModeBuffer(true);
        break;
    }
  }

  public void setCameraCallbacks(CameraCallbacks callbacks) {
    cameraManager.setCameraCallbacks(callbacks);
  }

  /**
   * Set a callback to know errors related with Video/Audio encoders
   * @param encoderErrorCallback callback to use, null to remove
   */
  public void setEncoderErrorCallback(EncoderErrorCallback encoderErrorCallback) {
    videoEncoder.setEncoderErrorCallback(encoderErrorCallback);
    audioEncoder.setEncoderErrorCallback(encoderErrorCallback);
  }

  /**
   * Set an audio effect modifying microphone's PCM buffer.
   */
  public void setCustomAudioEffect(CustomAudioEffect customAudioEffect) {
    microphoneManager.setCustomAudioEffect(customAudioEffect);
  }

  /**
   * @param callback get fps while record or stream
   */
  public void setFpsListener(FpsListener.Callback callback) {
    fpsListener.setCallback(callback);
  }

  /**
   * @return true if success, false if fail (not supported or called before start camera)
   */
  public boolean enableFaceDetection(FaceDetectorCallback faceDetectorCallback) {
    return cameraManager.enableFaceDetection(faceDetectorCallback);
  }

  public void disableFaceDetection() {
    cameraManager.disableFaceDetection();
  }

  public boolean isFaceDetectionEnabled() {
    return cameraManager.isFaceDetectionEnabled();
  }

  /**
   * @return true if success, false if fail (not supported or called before start camera)
   */
  public boolean enableVideoStabilization() {
    return cameraManager.enableVideoStabilization();
  }

  public void disableVideoStabilization() {
    cameraManager.disableVideoStabilization();
  }

  public boolean isVideoStabilizationEnabled() {
    return cameraManager.isVideoStabilizationEnabled();
  }

  /**
   * Use getCameraFacing instead
   */
  @Deprecated
  public boolean isFrontCamera() {
    return cameraManager.getCameraFacing() == CameraHelper.Facing.FRONT;
  }

  public CameraHelper.Facing getCameraFacing() {
    return cameraManager.getCameraFacing();
  }

  public void enableLantern() throws Exception {
    cameraManager.enableLantern();
  }

  public void disableLantern() {
    cameraManager.disableLantern();
  }

  public boolean isLanternEnabled() {
    return cameraManager.isLanternEnabled();
  }

  public boolean enableAutoFocus() {
    return cameraManager.enableAutoFocus();
  }

  public boolean disableAutoFocus() {
    return cameraManager.disableAutoFocus();
  }

  public boolean isAutoFocusEnabled() {
    return cameraManager.isAutoFocusEnabled();
  }

  public boolean resetVideoEncoder() {
    if (glInterface != null) {
      glInterface.removeMediaCodecSurface();
      boolean result = videoEncoder.reset();
      if (!result) return false;
      glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
      return true;
    } else {
      return videoEncoder.reset();
    }
  }

  public boolean resetAudioEncoder() {
    return audioEncoder.reset();
  }

  /**
   * Call this method before use @startStream. If not you will do a stream without video. NOTE:
   * Rotation with encoder is silence ignored in some devices.
   *
   * @param width resolution in px.
   * @param height resolution in px.
   * @param fps frames per second of the stream.
   * @param bitrate H264 in bps.
   * @param rotation could be 90, 180, 270 or 0. You should use CameraHelper.getCameraOrientation
   * @param profile codec value from MediaCodecInfo.CodecProfileLevel class
   * @param level codec value from MediaCodecInfo.CodecProfileLevel class
   * with SurfaceView or TextureView and 0 with OpenGlView or LightOpenGlView. NOTE: Rotation with
   * encoder is silence ignored in some devices.
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   */
  public boolean prepareVideo(int width, int height, int fps, int bitrate, int iFrameInterval,
      int rotation, int profile, int level) {
    if (onPreview && width != previewWidth || height != previewHeight
        || fps != videoEncoder.getFps() || rotation != videoEncoder.getRotation()) {
      stopPreview();
    }
    FormatVideoEncoder formatVideoEncoder =
        glInterface == null ? FormatVideoEncoder.YUV420Dynamical : FormatVideoEncoder.SURFACE;
    return videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation, iFrameInterval,
        formatVideoEncoder, profile, level);
  }

  /**
   * backward compatibility reason
   */
  public boolean prepareVideo(int width, int height, int fps, int bitrate, int iFrameInterval,
      int rotation) {
    return prepareVideo(width, height, fps, bitrate, iFrameInterval, rotation, -1, -1);
  }

  public boolean prepareVideo(int width, int height, int fps, int bitrate, int rotation) {
    return prepareVideo(width, height, fps, bitrate, 2, rotation);
  }

  public boolean prepareVideo(int width, int height, int bitrate) {
    int rotation = CameraHelper.getCameraOrientation(context);
    return prepareVideo(width, height, 30, bitrate, 2, rotation);
  }

  protected abstract void onAudioInfoImp(boolean isStereo, int sampleRate);

  /**
   * Call this method before use @startStream. If not you will do a stream without audio.
   *
   * @param bitrate AAC in kb.
   * @param sampleRate of audio in hz. Can be 8000, 16000, 22500, 32000, 44100.
   * @param isStereo true if you want Stereo audio (2 audio channels), false if you want Mono audio
   * (1 audio channel).
   * @param echoCanceler true enable echo canceler, false disable.
   * @param noiseSuppressor true enable noise suppressor, false  disable.
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a AAC encoder).
   */
  public boolean prepareAudio(int audioSource, int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) {
     if (!microphoneManager.createMicrophone(audioSource, sampleRate, isStereo, echoCanceler, noiseSuppressor)) {
       return false;
     }
    onAudioInfoImp(isStereo, sampleRate);
    audioInitialized = audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo,
        microphoneManager.getMaxInputSize());
    return audioInitialized;
  }

  public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) {
    return prepareAudio(MediaRecorder.AudioSource.DEFAULT, bitrate, sampleRate, isStereo, echoCanceler,
        noiseSuppressor);
  }

  public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo) {
    return prepareAudio(bitrate, sampleRate, isStereo, false, false);
  }

  /**
   * Same to call: rotation = 0; if (Portrait) rotation = 90; prepareVideo(640, 480, 30, 1200 *
   * 1024, false, rotation);
   *
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   */
  public boolean prepareVideo() {
    int rotation = CameraHelper.getCameraOrientation(context);
    return prepareVideo(640, 480, 30, 1200 * 1024, rotation);
  }

  /**
   * Same to call: prepareAudio(64 * 1024, 32000, true, false, false);
   *
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a AAC encoder).
   */
  public boolean prepareAudio() {
    return prepareAudio(64 * 1024, 32000, true, false, false);
  }

  /**
   * @param codecTypeVideo force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
   * @param codecTypeAudio force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
   */
  public void forceCodecType(CodecUtil.CodecType codecTypeVideo, CodecUtil.CodecType codecTypeAudio) {
    videoEncoder.forceCodecType(codecTypeVideo);
    audioEncoder.forceCodecType(codecTypeAudio);
  }

  /**
   * Starts recording a MP4 video.
   *
   * @param path Where file will be saved.
   * @throws IOException If initialized before a stream.
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void startRecord(@NonNull final String path, @Nullable RecordController.Listener listener)
      throws IOException {
    recordController.startRecord(path, listener);
    if (!streaming) {
      startEncoders();
    } else if (videoEncoder.isRunning()) {
      requestKeyFrame();
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void startRecord(@NonNull final String path) throws IOException {
    startRecord(path, null);
  }

  /**
   * Starts recording a MP4 video.
   *
   * @param fd Where the file will be saved.
   * @throws IOException If initialized before a stream.
   */
  @RequiresApi(api = Build.VERSION_CODES.O)
  public void startRecord(@NonNull final FileDescriptor fd,
      @Nullable RecordController.Listener listener) throws IOException {
    recordController.startRecord(fd, listener);
    if (!streaming) {
      startEncoders();
    } else if (videoEncoder.isRunning()) {
      requestKeyFrame();
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  public void startRecord(@NonNull final FileDescriptor fd) throws IOException {
    startRecord(fd, null);
  }

  /**
   * Stop record MP4 video started with @startRecord. If you don't call it file will be unreadable.
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void stopRecord() {
    recordController.stopRecord();
    if (!streaming) stopStream();
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void replaceView(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      replaceGlInterface(new GlStreamInterface(context));
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void replaceView(OpenGlView openGlView) {
    replaceGlInterface(openGlView);
  }

  /**
   * Replace glInterface used on fly. Ignored if you use SurfaceView or TextureView
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  private void replaceGlInterface(GlInterface glInterface) {
    if (this.glInterface != null && Build.VERSION.SDK_INT >= 18) {
      if (isStreaming() || isRecording() || isOnPreview()) {
        Point size = this.glInterface.getEncoderSize();
        cameraManager.stop();
        this.glInterface.removeMediaCodecSurface();
        this.glInterface.stop();
        this.glInterface = glInterface;
        int w = size.x;
        int h = size.y;
        int rotation = videoEncoder.getRotation();
        if (rotation == 90 || rotation == 270) {
          h = size.x;
          w = size.y;
        }
        prepareGlView(w, h, rotation);
        cameraManager.setRotation(rotation);
        cameraManager.start(videoEncoder.getWidth(), videoEncoder.getHeight(),
            videoEncoder.getFps());
      } else {
        this.glInterface = glInterface;
      }
    }
  }

  /**
   * Start camera preview. Ignored, if stream or preview is started.
   *
   * @param cameraFacing front or back camera. Like: {@link com.pedro.encoder.input.video.CameraHelper.Facing#BACK}
   * {@link com.pedro.encoder.input.video.CameraHelper.Facing#FRONT}
   * @param width of preview in px.
   * @param height of preview in px.
   * @param rotation camera rotation (0, 90, 180, 270). Recommended: {@link
   * com.pedro.encoder.input.video.CameraHelper#getCameraOrientation(Context)}
   */
  public void startPreview(CameraHelper.Facing cameraFacing, int width, int height, int fps, int rotation) {
    if (!onPreview) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && (glInterface instanceof GlStreamInterface)) {
        // if you are using background mode startPreview only work to indicate
        // that you want start with front or back camera
        cameraManager.setCameraFacing(cameraFacing);
        return;
      }
      previewWidth = width;
      previewHeight = height;
      videoEncoder.setFps(fps);
      videoEncoder.setRotation(rotation);
      prepareGlView(width, height, rotation);
      cameraManager.setRotation(rotation);
      cameraManager.start(cameraFacing, width, height, videoEncoder.getFps());
      onPreview = true;
    } else {
      Log.e(TAG, "Streaming or preview started, ignored");
    }
  }

  /**
   * Start camera preview. Ignored, if stream or preview is started.
   *
   * @param cameraId camera id.
   * {@link com.pedro.encoder.input.video.CameraHelper.Facing#FRONT}
   * @param width of preview in px.
   * @param height of preview in px.
   * @param rotation camera rotation (0, 90, 180, 270). Recommended: {@link
   * com.pedro.encoder.input.video.CameraHelper#getCameraOrientation(Context)}
   */
  public void startPreview(int cameraId, int width, int height, int fps, int rotation) {
    if (!isStreaming() && !onPreview) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && (glInterface instanceof GlStreamInterface)) {
        // if you are using background mode startPreview only work to indicate
        // that you want start with front or back camera
        cameraManager.setCameraSelect(cameraId);
        return;
      }
      previewWidth = width;
      previewHeight = height;
      videoEncoder.setFps(fps);
      videoEncoder.setRotation(rotation);
      prepareGlView(width, height, rotation);
      cameraManager.setRotation(rotation);
      cameraManager.start(cameraId, width, height, videoEncoder.getFps());
      onPreview = true;
    } else {
      Log.e(TAG, "Streaming or preview started, ignored");
    }
  }

  public void startPreview(CameraHelper.Facing cameraFacing, int width, int height, int rotation) {
    startPreview(cameraFacing, width, height, videoEncoder.getFps(), rotation);
  }

  public void startPreview(int cameraFacing, int width, int height, int rotation) {
    startPreview(cameraFacing, width, height, videoEncoder.getFps(), rotation);
  }

  public void startPreview(CameraHelper.Facing cameraFacing, int width, int height) {
    startPreview(cameraFacing, width, height, CameraHelper.getCameraOrientation(context));
  }

  public void startPreview(int cameraFacing, int width, int height) {
    startPreview(cameraFacing, width, height, CameraHelper.getCameraOrientation(context));
  }

  public void startPreview(CameraHelper.Facing cameraFacing, int rotation) {
    startPreview(cameraFacing, videoEncoder.getWidth(), videoEncoder.getHeight(), rotation);
  }

  public void startPreview(CameraHelper.Facing cameraFacing) {
    startPreview(cameraFacing, videoEncoder.getWidth(), videoEncoder.getHeight());
  }

  public void startPreview(int cameraFacing) {
    startPreview(cameraFacing, videoEncoder.getWidth(), videoEncoder.getHeight());
  }

  public void startPreview(int width, int height) {
    startPreview(getCameraFacing(), width, height);
  }

  public void startPreview() {
    startPreview(getCameraFacing());
  }

  /**
   * Stop camera preview. Ignored if streaming or already stopped. You need call it after
   *
   * @stopStream to release camera properly if you will close activity.
   */
  public void stopPreview() {
    if (!isStreaming() && !isRecording()) {
      stopCamera();
    } else {
      Log.e(TAG, "Streaming or preview stopped, ignored");
    }
  }

  /**
   * Similar to stopPreview but you can do it while streaming or recording.
   */
  public void stopCamera() {
    if (onPreview) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && (glInterface instanceof GlStreamInterface)) {
        return;
      }
      if (glInterface != null && Build.VERSION.SDK_INT >= 18) {
        glInterface.stop();
      }
      cameraManager.stop();
      onPreview = false;
      previewWidth = 0;
      previewHeight = 0;
    } else {
      Log.e(TAG, "Preview stopped, ignored");
    }
  }

  /**
   * Change preview orientation can be called while stream.
   *
   * @param orientation of the camera preview. Could be 90, 180, 270 or 0.
   */
  public void setPreviewOrientation(int orientation) {
    cameraManager.setPreviewOrientation(orientation);
  }

  /**
   * Set zoomIn or zoomOut to camera.
   *
   * @param event motion event. Expected to get event.getPointerCount() > 1
   */
  public void setZoom(MotionEvent event) {
    cameraManager.setZoom(event);
  }

  /**
   * Set zoomIn or zoomOut to camera.
   * Use this method if you use a zoom slider.
   *
   * @param level Expected to be >= 1 and <= max zoom level
   * @see Camera2Base#getZoom()
   */
  public void setZoom(int level) {
    cameraManager.setZoom(level);
  }

  /**
   * Return current zoom level
   *
   * @return current zoom level
   */
  public float getZoom() {
    return cameraManager.getZoom();
  }

  /**
   * Return max zoom level
   *
   * @return max zoom level range
   */
  public int getMaxZoom() {
    return cameraManager.getMaxZoom();
  }

  /**
   * Return min zoom level
   *
   * @return min zoom level range
   */
  public int getMinZoom() {
    return cameraManager.getMinZoom();
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void startStreamAndRecord(String url, String path, RecordController.Listener listener) throws IOException {
    startStream(url);
    recordController.startRecord(path, listener);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void startStreamAndRecord(String url, String path) throws IOException {
    startStreamAndRecord(url, path, null);
  }

  protected abstract void startStreamImp(String url);

  /**
   * Need be called after @prepareVideo or/and @prepareAudio. This method override resolution of
   *
   * @param url of the stream like: protocol://ip:port/application/streamName
   *
   * RTSP: rtsp://192.168.1.1:1935/live/pedroSG94 RTSPS: rtsps://192.168.1.1:1935/live/pedroSG94
   * RTMP: rtmp://192.168.1.1:1935/live/pedroSG94 RTMPS: rtmps://192.168.1.1:1935/live/pedroSG94
   * @startPreview to resolution seated in @prepareVideo. If you never startPreview this method
   * startPreview for you to resolution seated in @prepareVideo.
   */
  public void startStream(String url) {
    streaming = true;
    if (!recordController.isRunning()) {
      startEncoders();
    } else {
      requestKeyFrame();
    }
    startStreamImp(url);
    onPreview = true;
  }

  private void startEncoders() {
    long startTs = System.nanoTime() / 1000;
    videoEncoder.start(startTs);
    if (audioInitialized) audioEncoder.start(startTs);
    prepareGlView(videoEncoder.getWidth(), videoEncoder.getHeight(), videoEncoder.getRotation());
    if (audioInitialized) microphoneManager.start();
    cameraManager.setRotation(videoEncoder.getRotation());
    if (!cameraManager.isRunning()) {
      cameraManager.start(videoEncoder.getWidth(), videoEncoder.getHeight(), videoEncoder.getFps());
    }
    onPreview = true;
  }

  public void requestKeyFrame() {
    if (videoEncoder.isRunning()) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        videoEncoder.requestKeyframe();
      } else {
        if (glInterface != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
          glInterface.removeMediaCodecSurface();
        }
        videoEncoder.reset();
        if (glInterface != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
          glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
        }
      }
    }
  }

  private void prepareGlView(int width, int height, int rotation) {
    if (glInterface != null && Build.VERSION.SDK_INT >= 18) {
      int w = width;
      int h = height;
      boolean isPortrait = false;
      if (rotation == 90 || rotation == 270) {
        h = width;
        w = height;
        isPortrait = true;
      }
      glInterface.setEncoderSize(w, h);
      if (glInterface instanceof GlStreamInterface glStreamInterface) {
        glStreamInterface.setPreviewResolution(w, h);
        glStreamInterface.setIsPortrait(isPortrait);
      }
      glInterface.setRotation(0);
      if (!glInterface.isRunning()) glInterface.start();
      if (videoEncoder.getInputSurface() != null && videoEncoder.isRunning()) {
        glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
      }
      cameraManager.setSurfaceTexture(glInterface.getSurfaceTexture());
    }
  }

  protected abstract void stopStreamImp();

  /**
   * Stop stream started with @startStream.
   */
  public void stopStream() {
    if (streaming) {
      streaming = false;
      stopStreamImp();
    }
    if (!recordController.isRecording()) {
      if (audioInitialized) microphoneManager.stop();
      if (glInterface != null && Build.VERSION.SDK_INT >= 18) {
        glInterface.removeMediaCodecSurface();
        if (glInterface instanceof GlStreamInterface) {
          glInterface.stop();
          cameraManager.stop();
          onPreview = false;
        }
      }
      videoEncoder.stop();
      if (audioInitialized) audioEncoder.stop();
      recordController.resetFormats();
    }
  }

  /**
   * Get supported resolutions of back camera in px.
   *
   * @return list of resolutions supported by back camera
   */
  public List<Camera.Size> getResolutionsBack() {
    return cameraManager.getPreviewSizeBack();
  }

  /**
   * Get supported resolutions of front camera in px.
   *
   * @return list of resolutions supported by front camera
   */
  public List<Camera.Size> getResolutionsFront() {
    return cameraManager.getPreviewSizeFront();
  }

  public List<int[]> getSupportedFps() {
    return cameraManager.getSupportedFps();
  }

  /**
   * Set a custom size of audio buffer input.
   * If you set 0 or less you can disable it to use library default value.
   * Must be called before of prepareAudio method.
   *
   * @param size in bytes. Recommended multiple of 1024 (2048, 4096, 8196, etc)
   */
  public void setAudioMaxInputSize(int size) {
    microphoneManager.setMaxInputSize(size);
  }

  /**
   * Mute microphone, can be called before, while and after stream.
   */
  public void disableAudio() {
    microphoneManager.mute();
  }

  /**
   * Enable a muted microphone, can be called before, while and after stream.
   */
  public void enableAudio() {
    microphoneManager.unMute();
  }

  /**
   * Get mute state of microphone.
   *
   * @return true if muted, false if enabled
   */
  public boolean isAudioMuted() {
    return microphoneManager.isMuted();
  }

  public int getBitrate() {
    return videoEncoder.getBitRate();
  }

  public int getResolutionValue() {
    return videoEncoder.getWidth() * videoEncoder.getHeight();
  }

  public int getStreamWidth() {
    return videoEncoder.getWidth();
  }

  public int getStreamHeight() {
    return videoEncoder.getHeight();
  }

  /**
   * Switch camera used. Can be called anytime
   *
   * @throws CameraOpenException If the other camera doesn't support same resolution.
   */
  public void switchCamera() throws CameraOpenException {
    if (isStreaming() || isRecording() || onPreview) {
      cameraManager.switchCamera();
    } else {
      cameraManager.setCameraFacing(getCameraFacing() ==  CameraHelper.Facing.FRONT ? CameraHelper.Facing.BACK : CameraHelper.Facing.FRONT);
    }
  }

  public void switchCamera(int cameraId) throws CameraOpenException {
    if (isStreaming() || onPreview) {
      cameraManager.switchCamera(cameraId);
    } else {
      cameraManager.setCameraSelect(cameraId);
    }
  }

  public void setExposure(int value) {
    cameraManager.setExposure(value);
  }

  public int getExposure() {
    return cameraManager.getExposure();
  }

  public int getMaxExposure() {
    return cameraManager.getMaxExposure();
  }

  public int getMinExposure() {
    return cameraManager.getMinExposure();
  }

  public boolean tapToFocus(View view, MotionEvent event) {
    return cameraManager.tapToFocus(view, event);
  }

  public GlInterface getGlInterface() {
    if (glInterface != null) {
      return glInterface;
    } else {
      throw new RuntimeException("You can't do it. You are not using Opengl");
    }
  }

  /**
   * Set video bitrate of H264 in bits per second while stream.
   *
   * @param bitrate H264 in bits per second.
   */
  @RequiresApi(api = Build.VERSION_CODES.KITKAT)
  public void setVideoBitrateOnFly(int bitrate) {
    videoEncoder.setVideoBitrateOnFly(bitrate);
  }

  /**
   * Force stream to work with fps selected in prepareVideo method. Must be called before prepareVideo.
   * This is not recommend because could produce fps problems.
   *
   * @param enabled true to enabled, false to disable, disabled by default.
   */
  public void forceFpsLimit(boolean enabled) {
    int fps = enabled ? videoEncoder.getFps() : 0;
    videoEncoder.setForceFps(fps);
    if (glInterface != null) glInterface.forceFpsLimit(fps);
  }

  /**
   * Get stream state.
   *
   * @return true if streaming, false if not streaming.
   */
  public boolean isStreaming() {
    return streaming;
  }

  /**
   * Get preview state.
   *
   * @return true if enabled, false if disabled.
   */
  public boolean isOnPreview() {
    return onPreview;
  }

  /**
   * Get record state.
   *
   * @return true if recording, false if not recoding.
   */
  public boolean isRecording() {
    return recordController.isRunning();
  }

  public void pauseRecord() {
    recordController.pauseRecord();
  }

  public void resumeRecord() {
    recordController.resumeRecord();
  }

  public RecordController.Status getRecordStatus() {
    return recordController.getStatus();
  }

  protected abstract void getAudioDataImp(ByteBuffer audioBuffer, MediaCodec.BufferInfo info);

  protected abstract void onVideoInfoImp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps);

  protected abstract void getVideoDataImp(ByteBuffer videoBuffer, MediaCodec.BufferInfo info);

  public void setRecordController(BaseRecordController recordController) {
    if (!isRecording()) this.recordController = recordController;
  }

  private final GetCameraData getCameraData = frame -> {
    videoEncoder.inputYUVData(frame);
  };

  private final GetMicrophoneData getMicrophoneData = frame -> {
    audioEncoder.inputPCMData(frame);
  };

  private final GetAudioData getAudioData = new GetAudioData() {
    @Override
    public void getAudioData(@NonNull ByteBuffer audioBuffer, @NonNull MediaCodec.BufferInfo info) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        recordController.recordAudio(audioBuffer, info);
      }
      if (streaming) getAudioDataImp(audioBuffer, info);
    }

    @Override
    public void onAudioFormat(@NonNull MediaFormat mediaFormat) {
      recordController.setAudioFormat(mediaFormat);
    }
  };

  private final GetVideoData getVideoData = new GetVideoData() {
    @Override
    public void onVideoInfo(@NonNull ByteBuffer sps, @Nullable ByteBuffer pps, @Nullable ByteBuffer vps) {
      onVideoInfoImp(sps.duplicate(), pps != null ? pps.duplicate(): null, vps != null ? vps.duplicate() : null);
    }

    @Override
    public void getVideoData(@NonNull ByteBuffer videoBuffer, @NonNull MediaCodec.BufferInfo info) {
      fpsListener.calculateFps();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        recordController.recordVideo(videoBuffer, info);
      }
      if (streaming) getVideoDataImp(videoBuffer, info);
    }

    @Override
    public void onVideoFormat(@NonNull MediaFormat mediaFormat) {
      recordController.setVideoFormat(mediaFormat, !audioInitialized);
    }
  };

  public abstract StreamBaseClient getStreamClient();

  public void setVideoCodec(VideoCodec codec) {
    setVideoCodecImp(codec);
    recordController.setVideoCodec(codec);
    String type = switch (codec) {
      case H264 -> CodecUtil.H264_MIME;
      case H265 -> CodecUtil.H265_MIME;
      case AV1 -> CodecUtil.AV1_MIME;
    };
    videoEncoder.setType(type);
  }

  public void setAudioCodec(AudioCodec codec) {
    setAudioCodecImp(codec);
    recordController.setAudioCodec(codec);
    String type = switch (codec) {
      case G711 -> CodecUtil.G711_MIME;
      case AAC -> CodecUtil.AAC_MIME;
      case OPUS -> CodecUtil.OPUS_MIME;
    };
    audioEncoder.setType(type);
  }

  protected abstract void setVideoCodecImp(VideoCodec codec);
  protected abstract void setAudioCodecImp(AudioCodec codec);
}