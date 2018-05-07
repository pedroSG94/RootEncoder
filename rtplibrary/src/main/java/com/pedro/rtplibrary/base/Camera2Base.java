package com.pedro.rtplibrary.base;

import android.content.Context;
import android.graphics.PointF;
import android.hardware.camera2.CameraCharacteristics;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAacData;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.input.video.Camera2ApiManager;
import com.pedro.encoder.input.video.Camera2Facing;
import com.pedro.encoder.input.video.CameraOpenException;
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
import java.util.Arrays;
import java.util.List;

/**
 * Wrapper to stream with camera2 api and microphone.
 * Support stream with SurfaceView, TextureView, OpenGlView(Custom SurfaceView that use OpenGl) and
 * Context(background mode).
 * All views use Surface to buffer encoding mode for H264.
 *
 * API requirements:
 * API 21+.
 *
 * Created by pedro on 7/07/17.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public abstract class Camera2Base implements GetAacData, GetH264Data, GetMicrophoneData {

  protected Context context;
  protected Camera2ApiManager cameraManager;
  protected VideoEncoder videoEncoder;
  protected MicrophoneManager microphoneManager;
  protected AudioEncoder audioEncoder;
  private boolean streaming = false;
  private SurfaceView surfaceView;
  private TextureView textureView;
  private OpenGlView openGlView;
  private OpenGlViewBase openGlViewBase;
  private boolean videoEnabled = false;
  //record
  private MediaMuxer mediaMuxer;
  private int videoTrack = -1;
  private int audioTrack = -1;
  private boolean recording = false;
  private boolean canRecord = false;
  private boolean onPreview = false;
  private MediaFormat videoFormat;
  private MediaFormat audioFormat;
  private boolean onChangeOrientation = false;
  private boolean isBackground = false;

  public Camera2Base(SurfaceView surfaceView) {
    this.surfaceView = surfaceView;
    this.context = surfaceView.getContext();
    cameraManager = new Camera2ApiManager(context);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
  }

  public Camera2Base(TextureView textureView) {
    this.textureView = textureView;
    this.context = textureView.getContext();
    cameraManager = new Camera2ApiManager(context);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
  }

  public Camera2Base(OpenGlView openGlView) {
    this.openGlView = openGlView;
    this.openGlViewBase = openGlView;
    this.context = openGlView.getContext();
    openGlView.init();
    cameraManager = new Camera2ApiManager(context);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
  }

  public Camera2Base(LightOpenGlView lightOpenGlView) {
    this.openGlViewBase = lightOpenGlView;
    this.context = lightOpenGlView.getContext();
    lightOpenGlView.init();
    cameraManager = new Camera2ApiManager(context);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
  }

  public Camera2Base(Context context) {
    this.context = context;
    isBackground = true;
    cameraManager = new Camera2ApiManager(context);
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
   *
   * @param width resolution in px.
   * @param height resolution in px.
   * @param fps frames per second of the stream.
   * @param bitrate H264 in kb.
   * @param hardwareRotation true if you want rotate using encoder, false if you with OpenGl if you
   * are using OpenGlView.
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
    boolean result =
        videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation, hardwareRotation,
            iFrameInterval, FormatVideoEncoder.SURFACE);
    prepareCameraManager();
    return result;
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
   * isHardwareRotation = true;
   * if (openGlVIew) isHardwareRotation = false;
   * prepareVideo(640, 480, 30, 1200 * 1024, isHardwareRotation, 90);
   *
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   */
  public boolean prepareVideo() {
    if (onPreview) {
      stopPreview();
      onPreview = true;
    }
    boolean isHardwareRotation = openGlViewBase == null;
    int orientation = (context.getResources().getConfiguration().orientation == 1) ? 90 : 0;
    boolean result =
        videoEncoder.prepareVideoEncoder(640, 480, 30, 1200 * 1024, orientation, isHardwareRotation,
            2, FormatVideoEncoder.SURFACE);
    prepareCameraManager();
    return result;
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
   * Width and height preview will be the last resolution used to prepareVideo. 640x480 first time.
   *
   * @param cameraFacing front or back camera. Like:
   * {@link android.hardware.camera2.CameraMetadata#LENS_FACING_BACK}
   * {@link android.hardware.camera2.CameraMetadata#LENS_FACING_FRONT}
   */
  public void startPreview(@Camera2Facing int cameraFacing) {
    if (!isStreaming() && !onPreview) {
      if (surfaceView != null) {
        cameraManager.prepareCamera(surfaceView.getHolder().getSurface());
      } else if (textureView != null) {
        cameraManager.prepareCamera(new Surface(textureView.getSurfaceTexture()));
      } else if (openGlViewBase != null) {
        boolean isCamera2Lanscape = context.getResources().getConfiguration().orientation != 1;
        if (isCamera2Lanscape) openGlViewBase.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
        else openGlViewBase.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
        openGlViewBase.startGLThread(isCamera2Lanscape);
        cameraManager.prepareCamera(openGlViewBase.getSurfaceTexture(), videoEncoder.getWidth(),
            videoEncoder.getHeight());
      }
      cameraManager.openCameraFacing(cameraFacing);
      if (openGlViewBase != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        openGlViewBase.setCameraFace(cameraManager.isFrontCamera());
      }
      onPreview = true;
    }
  }

  /**
   * Start camera preview. Ignored, if stream or preview is started.
   * Width and height preview will be the last resolution used to start camera. 640x480 first time.
   * CameraFacing will be always back.
   */
  public void startPreview() {
    startPreview(CameraCharacteristics.LENS_FACING_BACK);
  }

  /**
   * Stop camera preview. Ignored if streaming or already stopped.
   * You need call it after @stopStream to release camera properly if you will close activity.
   */
  public void stopPreview() {
    if (!isStreaming() && onPreview) {
      if (openGlViewBase != null) {
        openGlViewBase.stopGlThread();
      }
      cameraManager.closeCamera(false);
      onPreview = false;
    }
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
    prepareGlView(false);
    videoEncoder.start();
    audioEncoder.start();
    if (onPreview) {
      cameraManager.openLastCamera();
    } else {
      cameraManager.openCameraBack();
    }
    if (openGlViewBase != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      openGlViewBase.setCameraFace(cameraManager.isFrontCamera());
    }
    microphoneManager.start();
    streaming = true;
    onPreview = true;
    startStreamRtp(url);
  }

  private void prepareGlView(boolean onChangeOrientation) {
    if (openGlViewBase != null && videoEnabled) {
      openGlViewBase.init();
      boolean rotate;
      if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
        openGlViewBase.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
        rotate = false;
      } else {
        openGlViewBase.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
        rotate = true;
      }
      if (onChangeOrientation) rotate = context.getResources().getConfiguration().orientation != 1;
      openGlViewBase.startGLThread(rotate);
      if (videoEncoder.getInputSurface() != null) {
        openGlViewBase.addMediaCodecSurface(videoEncoder.getInputSurface());
      }
      cameraManager.prepareCamera(openGlViewBase.getSurfaceTexture(), videoEncoder.getWidth(),
          videoEncoder.getHeight());
    }
  }

  protected abstract void stopStreamRtp();

  /**
   * Stop stream started with @startStream.
   */
  public void stopStream() {
    cameraManager.closeCamera(!isBackground);
    onPreview = !isBackground;
    microphoneManager.stop();
    stopStreamRtp();
    videoEncoder.stop();
    audioEncoder.stop();
    if (openGlViewBase != null) {
      openGlViewBase.removeMediaCodecSurface();
    }
    streaming = false;
  }

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
  public void setImageStreamObject(ImageStreamObject imageStreamObject) throws RuntimeException {
    if (openGlView != null) {
      openGlView.setImage(imageStreamObject);
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set a image");
    }
  }

  /**
   * Set a text to the stream.
   * By default with same resolution in px that the original file and in bottom-right position.
   *
   * @param textStreamObject text object that will be streamed.
   * @throws RuntimeException If you don't use OpenGlvIew
   */
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
  public void enableAA(boolean AAEnabled) throws RuntimeException {
    if (openGlView != null) {
      openGlView.enableAA(AAEnabled);
    } else {
      throw new RuntimeException("You must use OpenGlView in the constructor to set a position");
    }
  }

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
  public void setVideoBitrateOnFly(int bitrate) {
    if (Build.VERSION.SDK_INT >= 19) {
      videoEncoder.setVideoBitrateOnFly(bitrate);
    }
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
    return recording;
  }

  /**
   * Get preview state.
   *
   * @return true if enabled, false if disabled.
   */
  public boolean isOnPreview() {
    return onPreview;
  }

  private void prepareCameraManager() {
    if (textureView != null) {
      cameraManager.prepareCamera(textureView, videoEncoder.getInputSurface());
    } else if (surfaceView != null) {
      cameraManager.prepareCamera(surfaceView, videoEncoder.getInputSurface());
    } else if (openGlView != null || openGlViewBase != null) {
    } else {
      cameraManager.prepareCamera(videoEncoder.getInputSurface());
    }
    videoEnabled = true;
  }

  protected abstract void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

  @Override
  public void getAacData(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    if (recording && audioTrack != -1 && canRecord) {
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
    if (recording && videoTrack != -1) {
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
  public void onVideoFormat(MediaFormat mediaFormat) {
    videoFormat = mediaFormat;
  }

  @Override
  public void onAudioFormat(MediaFormat mediaFormat) {
    audioFormat = mediaFormat;
  }
}
