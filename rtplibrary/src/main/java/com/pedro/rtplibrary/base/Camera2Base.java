package com.pedro.rtplibrary.base;

import android.content.Context;
import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAacData;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.video.Camera2ApiManager;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetH264Data;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtplibrary.view.OpenGlView;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 7/07/17.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public abstract class Camera2Base
    implements GetAacData, GetCameraData, GetH264Data, GetMicrophoneData {

  protected Context context;
  protected Camera2ApiManager cameraManager;
  protected VideoEncoder videoEncoder;
  protected MicrophoneManager microphoneManager;
  protected AudioEncoder audioEncoder;
  private boolean streaming;
  private SurfaceView surfaceView;
  private TextureView textureView;
  private OpenGlView openGlView;
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

  public Camera2Base(SurfaceView surfaceView, Context context) {
    this.surfaceView = surfaceView;
    this.context = context;
    cameraManager = new Camera2ApiManager(context);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
    streaming = false;
  }

  public Camera2Base(TextureView textureView, Context context) {
    this.textureView = textureView;
    this.context = context;
    cameraManager = new Camera2ApiManager(context);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
    streaming = false;
  }

  public Camera2Base(OpenGlView openGlView, Context context) {
    this.openGlView = openGlView;
    this.context = context;
    cameraManager = new Camera2ApiManager(context);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
    streaming = false;
  }

  public Camera2Base(Context context) {
    this.context = context;
    this.textureView = null;
    cameraManager = new Camera2ApiManager(context);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
    streaming = false;
  }

  public abstract void setAuthorization(String user, String password);

  public boolean prepareVideo(int width, int height, int fps, int bitrate, boolean hardwareRotation,
      int rotation) {
    if (onPreview) stopPreview();
    int imageFormat = ImageFormat.NV21; //supported nv21 and yv12
    videoEncoder.setImageFormat(imageFormat);
    boolean result =
        videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation, hardwareRotation,
            FormatVideoEncoder.SURFACE);
    prepareCameraManager();
    return result;
  }

  protected abstract void prepareAudioRtp(boolean isStereo, int sampleRate);

  public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) {
    microphoneManager.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor);
    prepareAudioRtp(isStereo, sampleRate);
    return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo);
  }

  public boolean prepareVideo() {
    if (onPreview) stopPreview();
    boolean result = videoEncoder.prepareVideoEncoder(640, 480, 30, 1200 * 1024, 0, true,
        FormatVideoEncoder.SURFACE);
    prepareCameraManager();
    return result;
  }

  public abstract boolean prepareAudio();

  /*Need be called while stream*/
  public void startRecord(String path) throws IOException {
    if (streaming) {
      mediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      if (videoFormat != null) {
        videoTrack = mediaMuxer.addTrack(videoFormat);
        Log.e("Pedro", "video");
      }
      if (audioFormat != null) {
        audioTrack = mediaMuxer.addTrack(audioFormat);
        Log.e("Pedro", "audio");
      }
      mediaMuxer.start();
      recording = true;
      Log.e("Pedro", "started");
    } else {
      throw new IOException("Need be called while stream");
    }
  }

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

  public void startPreview() {
    if (!onPreview) {
      if (surfaceView != null) {
        cameraManager.prepareCamera(surfaceView.getHolder().getSurface(), false);
      } else if (textureView != null) {
        cameraManager.prepareCamera(new Surface(textureView.getSurfaceTexture()), false);
      } else if (openGlView != null) {
        openGlView.startGLThread();
        cameraManager.prepareCamera(openGlView.getSurface(), true);
      }
      cameraManager.openCameraBack();
      onPreview = true;
    }
  }

  public void stopPreview() {
    if (!isStreaming() && onPreview) {
      if (openGlView != null) {
        openGlView.stopGlThread();
      }
      cameraManager.closeCamera();
    }
  }

  protected abstract void startStreamRtp(String url);

  public void startStream(String url) {
    if (openGlView != null && videoEnabled) {
      openGlView.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
      openGlView.startGLThread();
      openGlView.addMediaCodecSurface(videoEncoder.getInputSurface());
      cameraManager.prepareCamera(openGlView.getSurface(), true);
    }
    videoEncoder.start();
    audioEncoder.start();
    if (onPreview) {
      cameraManager.openLastCamera();
    } else {
      cameraManager.openCameraBack();
    }
    microphoneManager.start();
    streaming = true;
    onPreview = true;
    startStreamRtp(url);
  }

  protected abstract void stopStreamRtp();

  public void stopStream() {
    cameraManager.closeCamera();
    microphoneManager.stop();
    stopStreamRtp();
    videoEncoder.stop();
    audioEncoder.stop();
    if (openGlView != null) {
      openGlView.stopGlThread();
      openGlView.removeMediaCodecSurface();
    }
    streaming = false;
    onPreview = false;
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

  /**
   * need min API 19
   */
  public void setVideoBitrateOnFly(int bitrate) {
    if (Build.VERSION.SDK_INT >= 19) {
      videoEncoder.setVideoBitrateOnFly(bitrate);
    }
  }

  public boolean isStreaming() {
    return streaming;
  }

  private void prepareCameraManager() {
    if (textureView != null) {
      cameraManager.prepareCamera(textureView, videoEncoder.getInputSurface());
    } else if (surfaceView != null) {
      cameraManager.prepareCamera(surfaceView, videoEncoder.getInputSurface());
    } else if (openGlView != null) {
    } else {
      cameraManager.prepareCamera(videoEncoder.getInputSurface(), false);
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
  public void inputPcmData(byte[] buffer, int size) {
    audioEncoder.inputPcmData(buffer, size);
  }

  @Override
  public void inputYv12Data(byte[] buffer) {
    videoEncoder.inputYv12Data(buffer);
  }

  @Override
  public void inputNv21Data(byte[] buffer) {
    videoEncoder.inputNv21Data(buffer);
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
