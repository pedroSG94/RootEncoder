package com.pedro.rtplibrary.base;

import android.content.Context;
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
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.pedro.encoder.Frame;
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
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetVideoData;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtplibrary.util.FpsListener;
import com.pedro.rtplibrary.util.RecordController;
import com.pedro.rtplibrary.view.GlInterface;
import com.pedro.rtplibrary.view.LightOpenGlView;
import com.pedro.rtplibrary.view.OffScreenGlThread;
import com.pedro.rtplibrary.view.OpenGlView;

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
public abstract class Camera2Base implements GetAacData, GetVideoData, GetMicrophoneData {

  private static final String TAG = "Camera2Base";

  protected Context context;
  private Camera2ApiManager cameraManager;
  protected VideoEncoder videoEncoder;
  private MicrophoneManager microphoneManager;
  private AudioEncoder audioEncoder;
  private boolean streaming = false;
  private SurfaceView surfaceView;
  private TextureView textureView;
  private GlInterface glInterface;
  private boolean videoEnabled = false;
  private boolean audioInitialized = false;
  private boolean onPreview = false;
  private boolean isBackground = false;
  protected RecordController recordController;
  private int previewWidth, previewHeight;
  private FpsListener fpsListener = new FpsListener();

  /**
   * @deprecated This view produce rotations problems and could be unsupported in future versions.
   * Use {@link Camera2Base#Camera2Base(OpenGlView)} or {@link Camera2Base#Camera2Base(LightOpenGlView)}
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
   * Use {@link Camera2Base#Camera2Base(OpenGlView)} or {@link Camera2Base#Camera2Base(LightOpenGlView)}
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
    glInterface.init();
    init(context);
  }

  public Camera2Base(LightOpenGlView lightOpenGlView) {
    this.context = lightOpenGlView.getContext();
    glInterface = lightOpenGlView;
    glInterface.init();
    init(context);
  }

  public Camera2Base(Context context, boolean useOpengl) {
    this.context = context;
    if (useOpengl) {
      glInterface = new OffScreenGlThread(context);
      glInterface.init();
    }
    isBackground = true;
    init(context);
  }

  private void init(Context context) {
    cameraManager = new Camera2ApiManager(context);
    videoEncoder = new VideoEncoder(this);
    setMicrophoneMode(MicrophoneMode.ASYNC);
    recordController = new RecordController();
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
        audioEncoder = new AudioEncoder(this);
        audioEncoder.setGetFrame(((MicrophoneManagerManual) microphoneManager).getGetFrame());
        break;
      case ASYNC:
        microphoneManager = new MicrophoneManager(this);
        audioEncoder = new AudioEncoder(this);
        break;
    }
  }

  public void setCameraCallbacks(CameraCallbacks callbacks) {
    cameraManager.setCameraCallbacks(callbacks);
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
   * Experimental
   */
  public void enableFaceDetection(Camera2ApiManager.FaceDetectorCallback faceDetectorCallback) {
    cameraManager.enableFaceDetection(faceDetectorCallback);
  }

  /**
   * Experimental
   */
  public void disableFaceDetection() {
    cameraManager.disableFaceDetection();
  }

  /**
   * Experimental
   */
  public boolean isFaceDetectionEnabled() {
    return cameraManager.isFaceDetectionEnabled();
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

  /**
   * Basic auth developed to work with Wowza. No tested with other server
   *
   * @param user auth.
   * @param password auth.
   */
  public abstract void setAuthorization(String user, String password);

  /**
   * Call this method before use @startStream. If not you will do a stream without video.
   *
   * @param width resolution in px.
   * @param height resolution in px.
   * @param fps frames per second of the stream.
   * @param bitrate H264 in bps.
   * @param rotation could be 90, 180, 270 or 0 (Normally 0 if you are streaming in landscape or 90
   * if you are streaming in Portrait). This only affect to stream result. NOTE: Rotation with
   * encoder is silence ignored in some devices.
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   */
  public boolean prepareVideo(int width, int height, int fps, int bitrate, int iFrameInterval,
      int rotation, int avcProfile, int avcProfileLevel) {
    if (onPreview && !(glInterface != null && width == previewWidth && height == previewHeight)) {
      stopPreview();
      onPreview = true;
    }
    boolean result =
        videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation, iFrameInterval,
            FormatVideoEncoder.SURFACE, avcProfile, avcProfileLevel);
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
    return prepareVideo(1280, 720, 30, 1200 * 1024, rotation);
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
   * @param forceVideo force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
   * @param forceAudio force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
   */
  public void setForce(CodecUtil.Force forceVideo, CodecUtil.Force forceAudio) {
    videoEncoder.setForce(forceVideo);
    audioEncoder.setForce(forceAudio);
  }

  /**
   * Starts recording an MP4 video. Needs to be called while streaming.
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
      resetVideoEncoder(false);
    }
  }

  public void startRecord(@NonNull final String path) throws IOException {
    startRecord(path, null);
  }

  /**
   * Starts recording an MP4 video. Needs to be called while streaming.
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
      resetVideoEncoder(false);
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
    replaceGlInterface(new OffScreenGlThread(context));
  }

  public void replaceView(OpenGlView openGlView) {
    isBackground = false;
    replaceGlInterface(openGlView);
  }

  public void replaceView(LightOpenGlView lightOpenGlView) {
    isBackground = false;
    replaceGlInterface(lightOpenGlView);
  }

  /**
   * Replace glInterface used on fly. Ignored if you use SurfaceView, TextureView or context without
   * OpenGl.
   */
  private void replaceGlInterface(GlInterface glInterface) {
    if (this.glInterface != null && Build.VERSION.SDK_INT >= 18) {
      if (isStreaming() || isRecording() || isOnPreview()) {
        cameraManager.closeCamera();
        this.glInterface.removeMediaCodecSurface();
        this.glInterface.stop();
        this.glInterface = glInterface;
        this.glInterface.init();
        boolean isPortrait = CameraHelper.isPortrait(context);
        if (isPortrait) {
          this.glInterface.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
        } else {
          this.glInterface.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
        }
        this.glInterface.setRotation(
            videoEncoder.getRotation() == 0 ? 270 : videoEncoder.getRotation() - 90);
        this.glInterface.start();
        if (isStreaming() || isRecording()) {
          this.glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
        }
        cameraManager.prepareCamera(this.glInterface.getSurfaceTexture(), videoEncoder.getWidth(),
            videoEncoder.getHeight(), videoEncoder.getFps());
        cameraManager.openLastCamera();
      } else {
        this.glInterface = glInterface;
        this.glInterface.init();
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
  public void startPreview(CameraHelper.Facing cameraFacing, int width, int height, int rotation) {
    if (!isStreaming() && !onPreview && !isBackground) {
      previewWidth = width;
      previewHeight = height;
      if (surfaceView != null) {
        cameraManager.prepareCamera(surfaceView.getHolder().getSurface(), videoEncoder.getFps());
      } else if (textureView != null) {
        cameraManager.prepareCamera(new Surface(textureView.getSurfaceTexture()),
            videoEncoder.getFps());
      } else if (glInterface != null) {
        boolean isPortrait = CameraHelper.isPortrait(context);
        if (isPortrait) {
          glInterface.setEncoderSize(height, width);
        } else {
          glInterface.setEncoderSize(width, height);
        }
        glInterface.setRotation(rotation == 0 ? 270 : rotation - 90);
        glInterface.start();
        cameraManager.prepareCamera(glInterface.getSurfaceTexture(), width, height,
            videoEncoder.getFps());
      }
      cameraManager.openCameraFacing(cameraFacing);
      onPreview = true;
    } else if (!isStreaming() && !onPreview && isBackground) {
      // if you are using background mode startPreview only work to indicate
      // that you want start with front or back camera
      cameraManager.setCameraFacing(cameraFacing);
    } else {
      Log.e(TAG, "Streaming or preview started, ignored");
    }
  }

  public void startPreview(CameraHelper.Facing cameraFacing, int width, int height) {
    startPreview(cameraFacing, width, height, CameraHelper.getCameraOrientation(context));
  }

  public void startPreview(CameraHelper.Facing cameraFacing, int rotation) {
    startPreview(cameraFacing, videoEncoder.getWidth(), videoEncoder.getHeight(), rotation);
  }

  public void startPreview(CameraHelper.Facing cameraFacing) {
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
      resetVideoEncoder(true);
    }
    startStreamRtp(url);
    onPreview = true;
  }

  private void startEncoders() {
    videoEncoder.start();
    if (audioInitialized) audioEncoder.start();
    prepareGlView();
    if (audioInitialized) microphoneManager.start();
    if (glInterface == null && !cameraManager.isRunning() && videoEncoder.getWidth() != previewWidth
        || videoEncoder.getHeight() != previewHeight) {
      cameraManager.openLastCamera();
    }
    onPreview = true;
  }

  private void resetVideoEncoder(boolean reset) {
    if (glInterface != null) {
      glInterface.removeMediaCodecSurface();
    }
    if (reset) videoEncoder.reset(); else videoEncoder.forceKeyFrame();
    if (glInterface != null) {
      glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
    } else {
      cameraManager.closeCamera();
      cameraManager.prepareCamera(videoEncoder.getInputSurface(), videoEncoder.getFps());
      cameraManager.openLastCamera();
    }
  }

  private void prepareGlView() {
    if (glInterface != null && videoEnabled) {
      if (glInterface instanceof OffScreenGlThread) {
        glInterface.init();
      }
      glInterface.setFps(videoEncoder.getFps());
      if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
        glInterface.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
      } else {
        glInterface.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
      }
      int rotation = videoEncoder.getRotation();
      glInterface.setRotation(rotation == 0 ? 270 : rotation - 90);
      if (!cameraManager.isRunning() && videoEncoder.getWidth() != previewWidth
          || videoEncoder.getHeight() != previewHeight) {
        glInterface.start();
      }
      if (videoEncoder.getInputSurface() != null) {
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
        if (glInterface instanceof OffScreenGlThread) {
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
   * Retries to connect with the given delay. You can pass an optional backupUrl
   * if you'd like to connect to your backup server instead of the original one.
   * Given backupUrl replaces the original one.
   */
  public boolean reTry(long delay, String reason, @Nullable String backupUrl) {
    boolean result = shouldRetry(reason);
    if (result) {
      resetVideoEncoder(true);
      reConnect(delay, backupUrl);
    }
    return result;
  }

  public boolean reTry(long delay, String reason) {
    return reTry(delay, reason, null);
  }

  protected abstract boolean shouldRetry(String reason);

  public abstract void setReTries(int reTries);

  protected abstract void reConnect(long delay, @Nullable String backupUrl);

  //cache control
  public abstract boolean hasCongestion();

  public abstract void resizeCache(int newSize) throws RuntimeException;

  public abstract int getCacheSize();

  public abstract long getSentAudioFrames();

  public abstract long getSentVideoFrames();

  public abstract long getDroppedAudioFrames();

  public abstract long getDroppedVideoFrames();

  public abstract void resetSentAudioFrames();

  public abstract void resetSentVideoFrames();

  public abstract void resetDroppedAudioFrames();

  public abstract void resetDroppedVideoFrames();

  /**
   * Get supported preview resolutions of back camera in px.
   *
   * @return list of preview resolutions supported by back camera
   */
  public List<Size> getResolutionsBack() {
    return Arrays.asList(cameraManager.getCameraResolutionsBack());
  }

  /**
   * Get supported preview resolutions of front camera in px.
   *
   * @return list of preview resolutions supported by front camera
   */
  public List<Size> getResolutionsFront() {
    return Arrays.asList(cameraManager.getCameraResolutionsFront());
  }

  public Range<Integer>[] getSupportedFps() {
    return cameraManager.getSupportedFps();
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
   * Mute microphone, can be called before, while and after stream.
   */
  public void disableAudio() {
    if (audioInitialized) microphoneManager.mute();
  }

  /**
   * Enable a muted microphone, can be called before, while and after stream.
   */
  public void enableAudio() {
    if (audioInitialized) microphoneManager.unMute();
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
   * Get video camera state
   *
   * @return true if disabled, false if enabled
   */
  public boolean isVideoEnabled() {
    return videoEnabled;
  }

  /**
   * Return max zoom level
   *
   * @return max zoom level
   */
  public float getMaxZoom() {
    return cameraManager.getMaxZoom();
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
   * @see Camera2Base#getMaxZoom()
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
    if (isStreaming() || onPreview) {
      cameraManager.switchCamera();
    } else {
      cameraManager.setCameraFacing(getCameraFacing() == CameraHelper.Facing.FRONT ? CameraHelper.Facing.BACK : CameraHelper.Facing.FRONT);
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
    videoEnabled = true;
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
   * Set limit FPS while stream. This will be override when you call to prepareVideo method. This
   * could produce a change in iFrameInterval.
   *
   * @param fps frames per second
   */
  public void setLimitFPSOnFly(int fps) {
    videoEncoder.setFps(fps);
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

  /**
   * Get preview state.
   *
   * @return true if enabled, false if disabled.
   */
  public boolean isOnPreview() {
    return onPreview;
  }

  protected abstract void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

  @Override
  public void getAacData(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    recordController.recordAudio(aacBuffer, info);
    if (streaming) getAacDataRtp(aacBuffer, info);
  }

  protected abstract void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps);

  @Override
  public void onSpsPps(ByteBuffer sps, ByteBuffer pps) {
    if (streaming) onSpsPpsVpsRtp(sps, pps, null);
  }

  @Override
  public void onSpsPpsVps(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
    if (streaming) onSpsPpsVpsRtp(sps, pps, vps);
  }

  protected abstract void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

  @Override
  public void getVideoData(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    fpsListener.calculateFps();
    recordController.recordVideo(h264Buffer, info);
    if (streaming) getH264DataRtp(h264Buffer, info);
  }

  @Override
  public void inputPCMData(Frame frame) {
    audioEncoder.inputPCMData(frame);
  }

  @Override
  public void onVideoFormat(MediaFormat mediaFormat) {
    recordController.setVideoFormat(mediaFormat);
  }

  @Override
  public void onAudioFormat(MediaFormat mediaFormat) {
    recordController.setAudioFormat(mediaFormat);
  }

  public abstract void setLogs(boolean enable);
}
