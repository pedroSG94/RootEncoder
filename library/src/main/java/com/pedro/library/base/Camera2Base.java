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

package com.pedro.library.base;

import android.content.Context;
import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
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

import com.pedro.common.AudioCodec;
import com.pedro.common.VideoCodec;
import com.pedro.encoder.EncoderErrorCallback;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAacData;
import com.pedro.encoder.input.audio.CustomAudioEffect;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.audio.MicrophoneManagerManual;
import com.pedro.encoder.input.audio.MicrophoneMode;
import com.pedro.encoder.input.video.Camera2ApiManager;
import com.pedro.encoder.input.video.CameraCallbacks;
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.encoder.input.video.CameraOpenException;
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
import java.util.Arrays;
import java.util.List;

/**
 * Wrapper to stream with camera2 api and microphone. Support stream with SurfaceView, TextureView,
 * OpenGlView(Custom SurfaceView that use OpenGl) and Context(background mode). All views use
 * Surface to buffer encoding mode for H264.
 *
 * API requirements:
 * API 21+.
 *
 * Created by pedro on 7/07/17.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public abstract class Camera2Base {

  private static final String TAG = "Camera2Base";

  private final Context context;
  private Camera2ApiManager cameraManager;
  protected VideoEncoder videoEncoder;
  private MicrophoneManager microphoneManager;
  private AudioEncoder audioEncoder;
  private boolean streaming = false;
  private SurfaceView surfaceView;
  private TextureView textureView;
  private GlInterface glInterface;
  protected boolean audioInitialized = false;
  private boolean onPreview = false;
  private boolean isBackground = false;
  protected BaseRecordController recordController;
  private int previewWidth, previewHeight;
  private final FpsListener fpsListener = new FpsListener();

  /**
   * @deprecated This view produce rotations problems and could be unsupported in future versions.
   * Use {@link Camera2Base#Camera2Base(OpenGlView)}
   * instead.
   */
  @Deprecated
  public Camera2Base(SurfaceView surfaceView) {
    this.surfaceView = surfaceView;
    this.context = surfaceView.getContext();
    init(context);
  }

  /**
   * @deprecated This view produce rotations problems and could be unsupported in future versions.
   * Use {@link Camera2Base#Camera2Base(OpenGlView)}
   * instead.
   */
  @Deprecated
  public Camera2Base(TextureView textureView) {
    this.textureView = textureView;
    this.context = textureView.getContext();
    init(context);
  }

  public Camera2Base(OpenGlView openGlView) {
    context = openGlView.getContext();
    glInterface = openGlView;
    init(context);
  }

  public Camera2Base(Context context, boolean useOpengl) {
    this.context = context;
    if (useOpengl) {
      glInterface = new GlStreamInterface(context);
    }
    isBackground = true;
    init(context);
  }

  private void init(Context context) {
    cameraManager = new Camera2ApiManager(context);
    videoEncoder = new VideoEncoder(getVideoData);
    setMicrophoneMode(MicrophoneMode.ASYNC);
    recordController = new AndroidMuxerRecordController();
  }

  /**
   * Must be called before prepareAudio.
   *
   * @param microphoneMode mode to work accord to audioEncoder. By default ASYNC:
   * SYNC using same thread. This mode could solve choppy audio or AudioEncoder frame discarded.
   * ASYNC using other thread.
   */
  public void setMicrophoneMode(MicrophoneMode microphoneMode) {
    switch (microphoneMode) {
      case SYNC:
        microphoneManager = new MicrophoneManagerManual();
        audioEncoder = new AudioEncoder(getAacData);
        audioEncoder.setGetFrame(((MicrophoneManagerManual) microphoneManager).getGetFrame());
        audioEncoder.setTsModeBuffer(false);
        break;
      case ASYNC:
        microphoneManager = new MicrophoneManager(getMicrophoneData);
        audioEncoder = new AudioEncoder(getAacData);
        audioEncoder.setTsModeBuffer(false);
        break;
      case BUFFER:
        microphoneManager = new MicrophoneManager(getMicrophoneData);
        audioEncoder = new AudioEncoder(getAacData);
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
   * Enable EIS video stabilization
   * Warning: Turning both OIS and EIS modes on may produce undesirable interaction, so it is recommended not to enable both at the same time.
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
   * Enable OIS video stabilization
   * Warning: Turning both OIS and EIS modes on may produce undesirable interaction, so it is recommended not to enable both at the same time.
   * @return true if success, false if fail (not supported or called before start camera)
   */
  public boolean enableOpticalVideoStabilization() {
    return cameraManager.enableOpticalVideoStabilization();
  }

  public void disableOpticalVideoStabilization() {
    cameraManager.disableOpticalVideoStabilization();
  }

  public boolean isOpticalVideoStabilizationEnabled() {
    return cameraManager.isOpticalStabilizationEnabled();
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

  public boolean isLanternSupported() {
    return cameraManager.isLanternSupported();
  }

  public void enableAutoFocus() {
    cameraManager.enableAutoFocus();
  }

  public void disableAutoFocus() {
    cameraManager.disableAutoFocus();
  }

  public boolean isAutoFocusEnabled() {
    return cameraManager.isAutoFocusEnabled();
  }

  public void setFocusDistance(float distance) {
    cameraManager.setFocusDistance(distance);
  }

  /**
   * Call this method before use @startStream. If not you will do a stream without video.
   *
   * @param width resolution in px.
   * @param height resolution in px.
   * @param fps frames per second of the stream.
   * @param bitrate H264 in bps.
   * @param rotation could be 90, 180, 270 or 0 (Normally 0 if you are streaming in landscape or 90
   * @param profile codec value from MediaCodecInfo.CodecProfileLevel class
   * @param level codec value from MediaCodecInfo.CodecProfileLevel class
   * if you are streaming in Portrait). This only affect to stream result. NOTE: Rotation with
   * encoder is silence ignored in some devices.
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   */
  public boolean prepareVideo(int width, int height, int fps, int bitrate, int iFrameInterval,
      int rotation, int profile, int level) {
    if (onPreview && glInterface != null && (width != previewWidth || height != previewHeight
        || fps != videoEncoder.getFps() || rotation != videoEncoder.getRotation())) {
      stopPreview();
    }
    boolean result = videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation,
        iFrameInterval, FormatVideoEncoder.SURFACE, profile, level);
    prepareCameraManager();
    return result;
  }

  public boolean prepareVideo(int width, int height, int fps, int bitrate, int iFrameInterval,
      int rotation) {
    return prepareVideo(width, height, fps, bitrate, iFrameInterval, rotation, -1, -1);
  }

  /**
   * backward compatibility reason
   */
  public boolean prepareVideo(int width, int height, int fps, int bitrate, int rotation) {
    return prepareVideo(width, height, fps, bitrate, 2, rotation);
  }

  public boolean prepareVideo(int width, int height, int bitrate) {
    int rotation = CameraHelper.getCameraOrientation(context);
    return prepareVideo(width, height, 30, bitrate, 2, rotation);
  }

  protected abstract void prepareAudioRtp(boolean isStereo, int sampleRate);

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
    prepareAudioRtp(isStereo, sampleRate);
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
   * Same to call: isHardwareRotation = true; if (openGlVIew) isHardwareRotation = false;
   * prepareVideo(640, 480, 30, 1200 * 1024, isHardwareRotation, 90);
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
  public void startRecord(@NonNull String path, @Nullable RecordController.Listener listener)
      throws IOException {
    recordController.startRecord(path, listener);
    if (!streaming) {
      startEncoders();
    } else if (videoEncoder.isRunning()) {
      requestKeyFrame();
    }
  }

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
  public void stopRecord() {
    recordController.stopRecord();
    if (!streaming) stopStream();
  }

  public void replaceView(Context context) {
    isBackground = true;
    replaceGlInterface(new GlStreamInterface(context));
  }

  public void replaceView(OpenGlView openGlView) {
    isBackground = false;
    replaceGlInterface(openGlView);
  }

  /**
   * Replace glInterface used on fly. Ignored if you use SurfaceView, TextureView or context without
   * OpenGl.
   */
  private void replaceGlInterface(GlInterface glInterface) {
    if (this.glInterface != null && Build.VERSION.SDK_INT >= 18) {
      if (isStreaming() || isRecording() || isOnPreview()) {
        Point size = this.glInterface.getEncoderSize();
        cameraManager.closeCamera();
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
        cameraManager.openLastCamera();
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
   * @param rotation camera rotation (0, 90, 180, 270). Recommended: {@link
   * com.pedro.encoder.input.video.CameraHelper#getCameraOrientation(Context)}
   */
  public void startPreview(CameraHelper.Facing cameraFacing, int width, int height, int fps, int rotation) {
    startPreview(cameraManager.getCameraIdForFacing(cameraFacing), width, height, fps, rotation);
  }

  public void startPreview(CameraHelper.Facing cameraFacing, int width, int height, int rotation) {
    startPreview(cameraFacing, width, height, videoEncoder.getFps(), rotation);
  }

  public void startPreview(String cameraId, int width, int height, int rotation) {
    startPreview(cameraId, width, height, videoEncoder.getFps(), rotation);
  }

  public void startPreview(String cameraId, int width, int height, int fps, int rotation) {
    if (!isStreaming() && !onPreview && !isBackground) {
      previewWidth = width;
      previewHeight = height;
      videoEncoder.setFps(fps);
      videoEncoder.setRotation(rotation);
      if (surfaceView != null) {
        cameraManager.prepareCamera(surfaceView.getHolder().getSurface(), videoEncoder.getFps());
      } else if (textureView != null) {
        cameraManager.prepareCamera(new Surface(textureView.getSurfaceTexture()),
            videoEncoder.getFps());
      } else if (glInterface != null) {
        prepareGlView(width, height, rotation);
      }
      cameraManager.openCameraId(cameraId);
      onPreview = true;
    } else if (!isStreaming() && !onPreview && isBackground) {
      // if you are using background mode startPreview only work to indicate
      // that you want start with front or back camera
      cameraManager.setCameraId(cameraId);
    } else {
      Log.e(TAG, "Streaming or preview started, ignored");
    }
  }

  public void startPreview(CameraHelper.Facing cameraFacing, int width, int height) {
    startPreview(cameraManager.getCameraIdForFacing(cameraFacing),
            width, height, CameraHelper.getCameraOrientation(context));
  }

  public void startPreview(String cameraId, int width, int height) {
    startPreview(cameraId, width, height, CameraHelper.getCameraOrientation(context));
  }

  public void startPreview(String cameraId, int rotation) {
    startPreview(cameraId, videoEncoder.getWidth(), videoEncoder.getHeight(), rotation);
  }

  public void startPreview(CameraHelper.Facing cameraFacing, int rotation) {
    startPreview(cameraManager.getCameraIdForFacing(cameraFacing),
            videoEncoder.getWidth(), videoEncoder.getHeight(), rotation);
  }

  public void startPreview(String cameraId) {
    startPreview(cameraId, videoEncoder.getWidth(), videoEncoder.getHeight());
  }

  public void startPreview(CameraHelper.Facing cameraFacing) {
    startPreview(cameraManager.getCameraIdForFacing(cameraFacing),
            videoEncoder.getWidth(), videoEncoder.getHeight());
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
    if (!isStreaming() && !isRecording() && onPreview && !isBackground) {
      if (glInterface != null) {
        glInterface.stop();
      }
      cameraManager.closeCamera();
      onPreview = false;
      previewWidth = 0;
      previewHeight = 0;
    } else {
      Log.e(TAG, "Streaming or preview stopped, ignored");
    }
  }

  public void startStreamAndRecord(String url, String path, RecordController.Listener listener) throws IOException {
    startStream(url);
    recordController.startRecord(path, listener);
  }

  public void startStreamAndRecord(String url, String path) throws IOException {
    startStreamAndRecord(url, path, null);
  }

  protected abstract void startStreamRtp(String url);

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
    startStreamRtp(url);
    onPreview = true;
  }

  private void startEncoders() {
    videoEncoder.start();
    if (audioInitialized) audioEncoder.start();
    prepareGlView(videoEncoder.getWidth(), videoEncoder.getHeight(), videoEncoder.getRotation());
    if (audioInitialized) microphoneManager.start();
    if (glInterface == null && !cameraManager.isRunning() && videoEncoder.getWidth() != previewWidth
        || videoEncoder.getHeight() != previewHeight) {
      cameraManager.openLastCamera();
    }
    onPreview = true;
  }

  public void requestKeyFrame() {
    if (videoEncoder.isRunning()) {
      videoEncoder.requestKeyframe();
    }
  }

  private void prepareGlView(int width, int height, int rotation) {
    if (glInterface != null) {
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
      glInterface.setRotation(rotation == 0 ? 270 : rotation - 90);
      if (!glInterface.isRunning()) glInterface.start();
      if (videoEncoder.getInputSurface() != null && videoEncoder.isRunning()) {
        glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
      }
      cameraManager.prepareCamera(glInterface.getSurfaceTexture(), videoEncoder.getWidth(),
          videoEncoder.getHeight(), videoEncoder.getFps());
    }
  }

  protected abstract void stopStreamRtp();

  /**
   * Stop stream started with @startStream.
   */
  public void stopStream() {
    if (streaming) {
      streaming = false;
      stopStreamRtp();
    }
    if (!recordController.isRecording()) {
      onPreview = !isBackground;
      if (audioInitialized) microphoneManager.stop();
      if (glInterface != null) {
        glInterface.removeMediaCodecSurface();
        if (glInterface instanceof GlStreamInterface) {
          glInterface.stop();
          cameraManager.closeCamera();
        }
      } else {
        if (isBackground) {
          cameraManager.closeCamera();
          onPreview = false;
        } else {
          cameraManager.stopRepeatingEncoder();
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
  public List<Size> getResolutionsBack() {
    return Arrays.asList(cameraManager.getCameraResolutionsBack());
  }

  /**
   * Get supported resolutions of front camera in px.
   *
   * @return list of resolutions supported by front camera
   */
  public List<Size> getResolutionsFront() {
    return Arrays.asList(cameraManager.getCameraResolutionsFront());
  }

  /**
   * Get supported resolutions of cameraId in px.
   *
   * @return list of resolutions supported by cameraId
   */
  public List<Size> getResolutions(String cameraId) {
    return Arrays.asList(cameraManager.getCameraResolutions(cameraId));
  }

  public List<Range<Integer>> getSupportedFps() {
    return cameraManager.getSupportedFps(null, CameraHelper.Facing.BACK);
  }

  public List<Range<Integer>> getSupportedFps(Size size, CameraHelper.Facing facing) {
    return cameraManager.getSupportedFps(size, facing);
  }

  /**
   * Get supported properties of the camera
   *
   * @return CameraCharacteristics object
   */
  public CameraCharacteristics getCameraCharacteristics() {
    return cameraManager.getCameraCharacteristics();
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

  /**
   * Return zoom level range
   *
   * @return zoom level range
   */
  public Range<Float> getZoomRange() {
    return cameraManager.getZoomRange();
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
   * Set zoomIn or zoomOut to camera.
   * Use this method if you use a zoom slider.
   *
   * @param level Expected to be >= 1 and <= max zoom level
   * @see Camera2Base#getZoom()
   */
  public void setZoom(float level) {
    cameraManager.setZoom(level);
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
   * @Experimental
   * @return optical zoom values available
   */
  public float[] getOpticalZooms() {
    return cameraManager.getOpticalZooms();
  }

  /**
   * @Experimental
   * @param level value provided by getOpticalZooms method
   */
  public void setOpticalZoom(float level) {
    cameraManager.setOpticalZoom(level);
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
   * @return IDs of cameras available that can be used on startPreview of switchCamera. null If no cameras available
   */
  public String[] getCamerasAvailable() {
    return cameraManager.getCamerasAvailable();
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
      cameraManager.setCameraFacing(getCameraFacing() == CameraHelper.Facing.FRONT ? CameraHelper.Facing.BACK : CameraHelper.Facing.FRONT);
    }
  }

  /**
   * Choose a specific camera to use. Can be called anytime.
   *
   * @param cameraId Identifier of the camera to use.
   * @throws CameraOpenException
   */
  public void switchCamera(String cameraId) throws CameraOpenException {
    if (isStreaming() || onPreview) {
      cameraManager.reOpenCamera(cameraId);
    } else {
      cameraManager.setCameraId(cameraId);
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

  public void tapToFocus(MotionEvent event) {
    cameraManager.tapToFocus(event);
  }

  public GlInterface getGlInterface() {
    if (glInterface != null) {
      return glInterface;
    } else {
      throw new RuntimeException("You can't do it. You are not using Opengl");
    }
  }

  private void prepareCameraManager() {
    if (textureView != null) {
      cameraManager.prepareCamera(textureView, videoEncoder.getInputSurface(),
          videoEncoder.getFps());
    } else if (surfaceView != null) {
      cameraManager.prepareCamera(surfaceView, videoEncoder.getInputSurface(),
          videoEncoder.getFps());
    } else if (glInterface != null) {
    } else {
      cameraManager.prepareCamera(videoEncoder.getInputSurface(), videoEncoder.getFps());
    }
  }

  /**
   * Set video bitrate of H264 in bits per second while stream.
   *
   * @param bitrate H264 in bits per second.
   */
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

  public void addImageListener(int width, int height, int format, int maxImages, Camera2ApiManager.ImageCallback listener) {
    cameraManager.addImageListener(width, height, format, maxImages, true, listener);
  }

  public void addImageListener(int width, int height, int format, int maxImages, boolean autoClose, Camera2ApiManager.ImageCallback listener) {
    cameraManager.addImageListener(width, height, format, maxImages, autoClose, listener);
  }

  public void addImageListener(int format, int maxImages, Camera2ApiManager.ImageCallback listener) {
    if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
      addImageListener(videoEncoder.getHeight(), videoEncoder.getWidth(), format, maxImages, listener);
    } else {
      addImageListener(videoEncoder.getWidth(), videoEncoder.getHeight(), format, maxImages, listener);
    }
  }

  public void removeImageListener() {
    cameraManager.removeImageListener();
  }
  /**
   * Get preview state.
   *
   * @return true if enabled, false if disabled.
   */
  public boolean isOnPreview() {
    return onPreview;
  }

  protected abstract void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

  protected abstract void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps);

  protected abstract void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

  public void setRecordController(BaseRecordController recordController) {
    if (!isRecording()) this.recordController = recordController;
  }

  private final GetMicrophoneData getMicrophoneData = frame -> {
    audioEncoder.inputPCMData(frame);
  };

  private final GetAacData getAacData = new GetAacData() {
    @Override
    public void getAacData(@NonNull ByteBuffer aacBuffer, @NonNull MediaCodec.BufferInfo info) {
      recordController.recordAudio(aacBuffer, info);
      if (streaming) getAacDataRtp(aacBuffer, info);
    }

    @Override
    public void onAudioFormat(@NonNull MediaFormat mediaFormat) {
      recordController.setAudioFormat(mediaFormat);
    }
  };

  private final GetVideoData getVideoData = new GetVideoData() {
    @Override
    public void onSpsPpsVps(@NonNull ByteBuffer sps, @Nullable ByteBuffer pps, @Nullable ByteBuffer vps) {
      onSpsPpsVpsRtp(sps.duplicate(),  pps != null ? pps.duplicate(): null, vps != null ? vps.duplicate() : null);
    }

    @Override
    public void getVideoData(@NonNull ByteBuffer h264Buffer, @NonNull MediaCodec.BufferInfo info) {
      fpsListener.calculateFps();
      recordController.recordVideo(h264Buffer, info);
      if (streaming) getH264DataRtp(h264Buffer, info);
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
