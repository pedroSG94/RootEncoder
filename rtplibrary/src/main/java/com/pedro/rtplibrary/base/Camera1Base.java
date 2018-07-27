package com.pedro.rtplibrary.base;

import android.content.Context;
import android.graphics.ImageFormat;
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
import com.pedro.encoder.input.video.Frame;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetH264Data;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtplibrary.view.GlInterface;
import com.pedro.rtplibrary.view.LightOpenGlView;
import com.pedro.rtplibrary.view.OffScreenGlThread;
import com.pedro.rtplibrary.view.OpenGlView;
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
  private GlInterface glInterface;
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
    context = surfaceView.getContext();
    cameraManager = new Camera1ApiManager(surfaceView, this);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
  }

  public Camera1Base(TextureView textureView) {
    context = textureView.getContext();
    cameraManager = new Camera1ApiManager(textureView, this);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public Camera1Base(OpenGlView openGlView) {
    context = openGlView.getContext();
    this.glInterface = openGlView;
    this.glInterface.init();
    cameraManager = new Camera1ApiManager(glInterface.getSurfaceTexture(), openGlView.getContext());
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public Camera1Base(LightOpenGlView lightOpenGlView) {
    context = lightOpenGlView.getContext();
    this.glInterface = lightOpenGlView;
    this.glInterface.init();
    cameraManager =
        new Camera1ApiManager(glInterface.getSurfaceTexture(), lightOpenGlView.getContext());
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public Camera1Base(Context context) {
    this.context = context;
    glInterface = new OffScreenGlThread(context);
    glInterface.init();
    cameraManager = new Camera1ApiManager(glInterface.getSurfaceTexture(), context);
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
    if (glInterface == null) {
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
    if (glInterface == null) {
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
  public void startRecord(final String path) throws IOException {
    mediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    recording = true;
    if (!streaming) {
      startEncoders();
    } else if (videoEncoder.isRunning()) {
      resetVideoEncoder();
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
    videoFormat = null;
    audioFormat = null;
    videoTrack = -1;
    audioTrack = -1;
    if (!streaming) stopStream();
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
    if (!isStreaming() && !onPreview && !(glInterface instanceof OffScreenGlThread)) {
      if (glInterface != null && Build.VERSION.SDK_INT >= 18) {
        boolean isPortrait = context.getResources().getConfiguration().orientation == 1;
        if (isPortrait) {
          if (width == 0 || height == 0) {
            glInterface.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
          } else {
            glInterface.setEncoderSize(height, width);
          }
        } else {
          if (width == 0 || height == 0) {
            glInterface.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
          } else {
            glInterface.setEncoderSize(width, height);
          }
        }
        glInterface.start(false);
        cameraManager.setSurfaceTexture(glInterface.getSurfaceTexture());
      }
      cameraManager.prepareCamera();
      if (width == 0 || height == 0) {
        cameraManager.start(cameraFacing);
      } else {
        cameraManager.start(cameraFacing, width, height);
      }
      if (glInterface != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        glInterface.setCameraFace(cameraManager.isFrontCamera());
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
    if (!isStreaming() && onPreview && !(glInterface instanceof OffScreenGlThread)) {
      if (glInterface != null && Build.VERSION.SDK_INT >= 18) {
        glInterface.stop();
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
    startStreamRtp(url);
    if (!recording) {
      startEncoders();
    } else {
      resetVideoEncoder();
    }
    streaming = true;
    onPreview = true;
  }

  private void startEncoders() {
    prepareGlView();
    videoEncoder.start();
    audioEncoder.start();
    microphoneManager.start();
    cameraManager.start();
    if (glInterface != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      glInterface.setCameraFace(cameraManager.isFrontCamera());
    }
  }

  private void resetVideoEncoder() {
    if (glInterface != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      glInterface.removeMediaCodecSurface();
    }
    videoEncoder.reset();
    if (glInterface != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
    }
  }

  private void prepareGlView() {
    if (glInterface != null && Build.VERSION.SDK_INT >= 18) {
      if (glInterface instanceof OffScreenGlThread) {
        glInterface = new OffScreenGlThread(context);
      }
      glInterface.init();
      if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
        glInterface.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
      } else {
        glInterface.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
      }
      glInterface.start(false);
      if (videoEncoder.getInputSurface() != null) {
        glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
      }
      cameraManager.setSurfaceTexture(glInterface.getSurfaceTexture());
      cameraManager.prepareCamera(videoEncoder.getWidth(), videoEncoder.getHeight(),
          videoEncoder.getFps(), ImageFormat.NV21);
    }
  }

  protected abstract void stopStreamRtp();

  /**
   * Stop stream started with @startStream.
   */
  public void stopStream() {
    if (streaming) stopStreamRtp();
    if (!recording) {
      microphoneManager.stop();
      videoEncoder.stop();
      audioEncoder.stop();
      if (glInterface != null && Build.VERSION.SDK_INT >= 18) {
        glInterface.removeMediaCodecSurface();
        if (glInterface instanceof OffScreenGlThread) {
          glInterface.stop();
          cameraManager.stop();
        }
      }
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
   * Switch camera used. Can be called on preview or while stream, ignored with preview off.
   *
   * @throws CameraOpenException If the other camera doesn't support same resolution.
   */
  public void switchCamera() throws CameraOpenException {
    if (isStreaming() || onPreview) {
      cameraManager.switchCamera();
      if (glInterface != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        glInterface.setCameraFace(cameraManager.isFrontCamera());
      }
    }
  }

  public GlInterface getGlInterface() {
    if (glInterface != null) return glInterface;
    else throw new RuntimeException("You can't do it. You are not using Opengl");
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

  protected abstract void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

  @Override
  public void getAacData(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && recording && canRecord) {
      mediaMuxer.writeSampleData(audioTrack, aacBuffer, info);
    }
    if (streaming) getAacDataRtp(aacBuffer, info);
  }

  protected abstract void onSPSandPPSRtp(ByteBuffer sps, ByteBuffer pps);

  @Override
  public void onSPSandPPS(ByteBuffer sps, ByteBuffer pps) {
    if (streaming) onSPSandPPSRtp(sps, pps);
  }

  protected abstract void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

  @Override
  public void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && recording) {
      if (info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME
          && !canRecord
          && videoFormat != null
          && audioFormat != null) {
        videoTrack = mediaMuxer.addTrack(videoFormat);
        audioTrack = mediaMuxer.addTrack(audioFormat);
        mediaMuxer.start();
        canRecord = true;
      }
      if (canRecord) mediaMuxer.writeSampleData(videoTrack, h264Buffer, info);
    }
    if (streaming) getH264DataRtp(h264Buffer, info);
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