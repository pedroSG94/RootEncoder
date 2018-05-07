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
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.input.video.Camera1ApiManager;
import com.pedro.encoder.input.video.Camera1Facing;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.encoder.input.video.EffectManager;
import com.pedro.encoder.input.video.Frame;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.utils.gl.GifStreamObject;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.TextStreamObject;
import com.pedro.encoder.utils.gl.TranslateTo;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetH264Data;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtplibrary.view.LightOpenGlView;
import com.pedro.rtplibrary.view.OpenGlView;
import com.pedro.rtplibrary.view.OpenGlViewBase;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Wrapper to stream with camera1 api and microphone.
 * Support stream with SurfaceView, TextureView and OpenGlView(Custom SurfaceView that use OpenGl).
 * SurfaceView and TextureView use buffer to buffer encoding mode for H264 and OpenGlView use
 * Surface to buffer mode(This mode is generally better because skip buffer processing).
 *
 * API requirements:
 * SurfaceView and TextureView mode: API 16+.
 * OpenGlView: API 18+.
 *
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
  private OpenGlViewBase openGlViewBase;
  private SurfaceView surfaceView;
  private TextureView textureView;
  private boolean streaming = false;
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
    this.surfaceView = surfaceView;
    context = surfaceView.getContext();
    cameraManager = new Camera1ApiManager(surfaceView, this);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
  }

  public Camera1Base(TextureView textureView) {
    this.textureView = textureView;
    context = textureView.getContext();
    cameraManager = new Camera1ApiManager(textureView, this);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public Camera1Base(OpenGlView openGlView) {
    context = openGlView.getContext();
    this.openGlView = openGlView;
    this.openGlViewBase = openGlView;
    this.openGlView.init();
    cameraManager = new Camera1ApiManager(openGlView.getSurfaceTexture(), openGlView.getContext());
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public Camera1Base(LightOpenGlView lightOpenGlView) {
    context = lightOpenGlView.getContext();
    this.openGlViewBase = lightOpenGlView;
    this.openGlViewBase.init();
    cameraManager =
        new Camera1ApiManager(lightOpenGlView.getSurfaceTexture(), lightOpenGlView.getContext());
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
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
   * NOTE: Rotation with encoder is silence ignored in some devices.
   *
   * @param width resolution in px.
   * @param height resolution in px.
   * @param fps frames per second of the stream.
   * @param bitrate H264 in kb.
   * @param hardwareRotation true if you want rotate using encoder, false if you want rotate with
   * software if you are using a SurfaceView or TextureView or with OpenGl if you are using
   * OpenGlView.
   * @param rotation could be 90, 180, 270 or 0 (Normally 0 if you are streaming in landscape or 90
   * if you are streaming in Portrait). This only affect to stream result.
   * NOTE: Rotation with encoder is silence ignored in some devices.
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   */
  public boolean prepareVideo(int width, int height, int fps, int bitrate, boolean hardwareRotation,
      int iFrameInterval, int rotation) {
    if (onPreview) {
      stopPreview();
      onPreview = true;
    }
    int imageFormat = ImageFormat.NV21; //supported nv21 and yv12
    if (openGlViewBase == null) {
      cameraManager.prepareCamera(width, height, fps, imageFormat);
      return videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation,
          hardwareRotation, iFrameInterval, FormatVideoEncoder.YUV420Dynamical);
    } else {
      return videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation,
          hardwareRotation, iFrameInterval, FormatVideoEncoder.SURFACE);
    }
  }

  /**
   * backward compatibility reason
   */
  public boolean prepareVideo(int width, int height, int fps, int bitrate, boolean hardwareRotation,
      int rotation) {
    return prepareVideo(width, height, fps, bitrate, hardwareRotation, 2, rotation);
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
  public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) {
    microphoneManager.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor);
    prepareAudioRtp(isStereo, sampleRate);
    return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo);
  }

  /**
   * Same to call:
   * rotation = 0;
   * if (Portrait) rotation = 90;
   * prepareVideo(640, 480, 30, 1200 * 1024, false, rotation);
   *
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   */
  public boolean prepareVideo() {
    if (onPreview) {
      stopPreview();
      onPreview = true;
    }
    if (openGlViewBase == null) {
      cameraManager.prepareCamera();
      return videoEncoder.prepareVideoEncoder();
    } else {
      int orientation = (context.getResources().getConfiguration().orientation == 1) ? 90 : 0;
      return videoEncoder.prepareVideoEncoder(640, 480, 30, 1200 * 1024, orientation, false, 2,
          FormatVideoEncoder.SURFACE);
    }
  }

  /**
   * Same to call:
   * prepareAudio(128 * 1024, 44100, true, false, false);
   *
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a AAC encoder).
   */
  public boolean prepareAudio() {
    microphoneManager.createMicrophone();
    return audioEncoder.prepareAudioEncoder();
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
   * Start record a MP4 video. Need be called while stream.
   *
   * @param path where file will be saved.
   * @throws IOException If you init it before start stream.
   */
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
      if (videoEncoder.isRunning()) {
        if (openGlViewBase != null) openGlViewBase.removeMediaCodecSurface();
        videoEncoder.reset();
        if (openGlViewBase != null) {
          openGlViewBase.addMediaCodecSurface(videoEncoder.getInputSurface());
        }
      }
    } else {
      throw new IOException("Need be called while stream");
    }
  }

  /**
   * Stop record MP4 video started with @startRecord. If you don't call it file will be unreadable.
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void stopRecord() {
    recording = false;
    if (mediaMuxer != null) {
      if (canRecord) {
        mediaMuxer.stop();
        mediaMuxer.release();
        canRecord = false;
      }
      mediaMuxer = null;
    }
    videoTrack = -1;
    audioTrack = -1;
  }

  /**
   * Start camera preview. Ignored, if stream or preview is started.
   *
   * @param cameraFacing front ot back camera. Like:
   * {@link android.hardware.Camera.CameraInfo#CAMERA_FACING_BACK}
   * {@link android.hardware.Camera.CameraInfo#CAMERA_FACING_FRONT}
   * @param width of preview in px.
   * @param height of preview in px.
   */
  public void startPreview(@Camera1Facing int cameraFacing, int width, int height) {
    if (!isStreaming() && !onPreview) {
      if (openGlViewBase != null && Build.VERSION.SDK_INT >= 18) {
        boolean isPortrait = context.getResources().getConfiguration().orientation == 1;
        if (isPortrait) {
          if (width == 0 || height == 0) {
            openGlViewBase.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
          } else {
            openGlViewBase.setEncoderSize(height, width);
          }
        } else {
          if (width == 0 || height == 0) {
            openGlViewBase.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
          } else {
            openGlViewBase.setEncoderSize(width, height);
          }
        }
        openGlViewBase.startGLThread(false);
        cameraManager.setSurfaceTexture(openGlViewBase.getSurfaceTexture());
      }
      cameraManager.prepareCamera();
      if (width == 0 || height == 0) {
        cameraManager.start(cameraFacing);
      } else {
        cameraManager.start(cameraFacing, width, height);
      }
      if (openGlViewBase != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        openGlViewBase.setCameraFace(cameraManager.isFrontCamera());
      }
      onPreview = true;
    } else {
      Log.e(TAG, "Streaming or preview started, ignored");
    }
  }

  /**
   * Start camera preview. Ignored, if stream or preview is started.
   * Width and height preview will be the last resolution used to start camera. 640x480 first time.
   *
   * @param cameraFacing front ot back camera. Like:
   * {@link android.hardware.Camera.CameraInfo#CAMERA_FACING_BACK}
   * {@link android.hardware.Camera.CameraInfo#CAMERA_FACING_FRONT}
   */
  public void startPreview(@Camera1Facing int cameraFacing) {
    startPreview(cameraFacing, 0, 0);
  }

  /**
   * Start camera preview. Ignored, if stream or preview is started.
   * CameraFacing will be always back.
   *
   * @param width preview in px.
   * @param height preview in px.
   */
  public void startPreview(int width, int height) {
    startPreview(Camera.CameraInfo.CAMERA_FACING_BACK, width, height);
  }

  /**
   * Start camera preview. Ignored, if stream or preview is started.
   * Width and height preview will be the last resolution used to start camera. 640x480 first time.
   * CameraFacing will be always back.
   */
  public void startPreview() {
    startPreview(Camera.CameraInfo.CAMERA_FACING_BACK);
  }

  /**
   * Stop camera preview. Ignored if streaming or already stopped.
   * You need call it after @stopStream to release camera properly if you will close activity.
   */
  public void stopPreview() {
    if (!isStreaming() && onPreview) {
      if (openGlViewBase != null && Build.VERSION.SDK_INT >= 18) {
        openGlViewBase.stopGlThread();
      }
      cameraManager.stop();
      onPreview = false;
    } else {
      Log.e(TAG, "Streaming or preview stopped, ignored");
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

  protected abstract void startStreamRtp(String url);

  /**
   * Need be called after @prepareVideo or/and @prepareAudio.
   * This method override resolution of @startPreview to resolution seated in @prepareVideo. If you
   * never startPreview this method startPreview for you to resolution seated in @prepareVideo.
   *
   * @param url of the stream like:
   * protocol://ip:port/application/streamName
   *
   * RTSP: rtsp://192.168.1.1:1935/live/pedroSG94
   * RTSPS: rtsps://192.168.1.1:1935/live/pedroSG94
   * RTMP: rtmp://192.168.1.1:1935/live/pedroSG94
   * RTMPS: rtmps://192.168.1.1:1935/live/pedroSG94
   */
  public void startStream(String url) {
    prepareGlView();
    startStreamRtp(url);
    videoEncoder.start();
    audioEncoder.start();
    cameraManager.start();
    if (openGlViewBase != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      openGlViewBase.setCameraFace(cameraManager.isFrontCamera());
    }
    microphoneManager.start();
    streaming = true;
    onPreview = true;
  }

  private void prepareGlView() {
    if (openGlViewBase != null && Build.VERSION.SDK_INT >= 18) {
      openGlViewBase.init();
      if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
        openGlViewBase.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
      } else {
        openGlViewBase.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
      }
      openGlViewBase.startGLThread(false);
      if (videoEncoder.getInputSurface() != null) {
        openGlViewBase.addMediaCodecSurface(videoEncoder.getInputSurface());
      }
      cameraManager.setSurfaceTexture(openGlViewBase.getSurfaceTexture());
      cameraManager.prepareCamera(videoEncoder.getWidth(), videoEncoder.getHeight(),
          videoEncoder.getFps(), ImageFormat.NV21);
    }
  }

  protected abstract void stopStreamRtp();

  /**
   * Stop stream started with @startStream.
   */
  public void stopStream() {
    microphoneManager.stop();
    stopStreamRtp();
    videoEncoder.stop();
    audioEncoder.stop();
    if (openGlViewBase != null && Build.VERSION.SDK_INT >= 18) {
      openGlViewBase.removeMediaCodecSurface();
    }
    streaming = false;
  }

  /**
   * Get supported preview resolutions of back camera in px.
   *
   * @return list of preview resolutions supported by back camera
   */
  public List<Camera.Size> getResolutionsBack() {
    return cameraManager.getPreviewSizeBack();
  }

  /**
   * Get supported preview resolutions of front camera in px.
   *
   * @return list of preview resolutions supported by front camera
   */
  public List<Camera.Size> getResolutionsFront() {
    return cameraManager.getPreviewSizeFront();
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
   * Get video camera state
   *
   * @return true if disabled, false if enabled
   */
  public boolean isVideoEnabled() {
    return videoEnabled;
  }

  /**
   * Disable send camera frames and send a black image with low bitrate(to reduce bandwith used)
   * instance it.
   */
  public void disableVideo() {
    videoEncoder.startSendBlackImage();
    videoEnabled = false;
  }

  /**
   * Enable send camera frames.
   */
  public void enableVideo() {
    videoEncoder.stopSendBlackImage();
    videoEnabled = true;
  }

  /**
   * Switch camera used. Can be called on preview or while stream, ignored with preview off.
   *
   * @throws CameraOpenException If the other camera doesn't support same resolution.
   */
  public void switchCamera() throws CameraOpenException {
    if (isStreaming() || onPreview) {
      cameraManager.switchCamera();
      if (openGlViewBase != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        openGlViewBase.setCameraFace(cameraManager.isFrontCamera());
      }
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void setFilter(BaseFilterRender baseFilterRender) {
    if (openGlView != null) {
      openGlView.setFilter(baseFilterRender);
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set a gif");
    }
  }

  /**
   * Set a gif to the stream.
   * By default with same resolution in px that the original file and in bottom-right position.
   *
   * @param gifStreamObject gif object that will be streamed.
   * @throws RuntimeException If you don't use OpenGlvIew
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void setGifStreamObject(GifStreamObject gifStreamObject) throws RuntimeException {
    if (openGlView != null) {
      openGlView.setGif(gifStreamObject);
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set a gif");
    }
  }

  /**
   * Set an image to the stream.
   * By default with same resolution in px that the original file and in bottom-right position.
   *
   * @param imageStreamObject image object that will be streamed.
   * @throws RuntimeException If you don't use OpenGlvIew
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void setImageStreamObject(ImageStreamObject imageStreamObject) throws RuntimeException {
    if (openGlView != null) {
      openGlView.setImage(imageStreamObject);
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set an image");
    }
  }

  /**
   * Set a text to the stream.
   * By default with same resolution in px that the original file and in bottom-right position.
   *
   * @param textStreamObject text object that will be streamed.
   * @throws RuntimeException If you don't use OpenGlvIew
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void setTextStreamObject(TextStreamObject textStreamObject) throws RuntimeException {
    if (openGlView != null) {
      openGlView.setText(textStreamObject);
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set a text");
    }
  }

  /**
   * Clear stream object of the stream.
   *
   * @throws RuntimeException If you don't use OpenGlvIew
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void clearStreamObject() throws RuntimeException {
    if (openGlView != null) {
      openGlView.clear();
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set a text");
    }
  }

  /**
   * Set alpha to the stream object.
   *
   * @param alpha of the stream object on fly, 1.0f totally opaque and 0.0f totally transparent
   * @throws RuntimeException If you don't use OpenGlvIew
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
   * Set resolution to the stream object in percent.
   *
   * @param sizeX of the stream object in percent: 100 full screen to 1
   * @param sizeY of the stream object in percent: 100 full screen to 1
   * @throws RuntimeException If you don't use OpenGlvIew
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
   * Set position to the stream object in percent.
   *
   * @param x of the stream object in percent: 100 full screen left to 0 full right
   * @param y of the stream object in percent: 100 full screen top to 0 full bottom
   * @throws RuntimeException If you don't use OpenGlvIew
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
   * Set position to the stream object with commons values developed.
   *
   * @param translateTo pre determinate positions
   * @throws RuntimeException If you don't use OpenGlvIew
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
   * Enable FXAA. Disabled by default.
   *
   * @param AAEnabled true to enable false to disable
   * @throws RuntimeException
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void enableAA(boolean AAEnabled) throws RuntimeException {
    if (openGlView != null) {
      openGlView.enableAA(AAEnabled);
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set a position");
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public boolean isAAEnabled() throws RuntimeException {
    if (openGlView != null) {
      return openGlView.isAAEnabled();
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set a position");
    }
  }

  /**
   * Get scale of the stream object in percent.
   *
   * @return scale in percent, 0 is stream not started
   * @throws RuntimeException If you don't use OpenGlvIew
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
   * Get position of the stream object in percent.
   *
   * @return position in percent, 0 is stream not started
   * @throws RuntimeException If you don't use OpenGlvIew
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public PointF getPositionStreamObject() throws RuntimeException {
    if (openGlView != null) {
      return openGlView.getPosition();
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to get scale");
    }
  }

  /**
   * Se video bitrate of H264 in kb while stream.
   *
   * @param bitrate H264 in kb.
   */
  @RequiresApi(api = Build.VERSION_CODES.KITKAT)
  public void setVideoBitrateOnFly(int bitrate) {
    videoEncoder.setVideoBitrateOnFly(bitrate);
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
    return recording;
  }

  /**
   * Set basic camera effect while preview or stream.
   *
   * @param effect seated.
   */
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
  public void inputYUVData(Frame frame) {
    videoEncoder.inputYUVData(frame);
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