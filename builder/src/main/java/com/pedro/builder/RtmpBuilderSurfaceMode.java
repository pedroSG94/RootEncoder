package com.pedro.builder;

import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.SurfaceView;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAccData;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.video.Camera2ApiManager;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetH264Data;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.nio.ByteBuffer;
import net.ossrs.rtmp.ConnectCheckerRtmp;
import net.ossrs.rtmp.SrsFlvMuxer;

/**
 * Created by pedro on 6/07/17.
 * This builder is under test, rotation only work with hardware because use encoding surface mode.
 * This maybe don't work for synchronizations problems and you will lose audio or video channel in the stream
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RtmpBuilderSurfaceMode
    implements GetAccData, GetCameraData, GetH264Data, GetMicrophoneData {

  private Camera2ApiManager cameraManager;
  private VideoEncoder videoEncoder;
  private MicrophoneManager microphoneManager;
  private AudioEncoder audioEncoder;
  private boolean streaming;
  private SurfaceView surfaceView;

  private SrsFlvMuxer srsFlvMuxer;
  private boolean videoEnabled = true;

  public RtmpBuilderSurfaceMode(SurfaceView surfaceView, ConnectCheckerRtmp connectCheckerRtmp) {
    this.surfaceView = surfaceView;
    srsFlvMuxer = new SrsFlvMuxer(connectCheckerRtmp);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
    streaming = false;
  }

  public void setAuthorization(String user, String password) {
    srsFlvMuxer.setAuthorization(user, password);
  }

  public boolean prepareVideo(int width, int height, int fps, int bitrate, int rotation) {
    int imageFormat = ImageFormat.NV21; //supported nv21 and yv12
    videoEncoder.setImageFormat(imageFormat);
    boolean result = videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation, true,
        FormatVideoEncoder.SURFACE);
    cameraManager = new Camera2ApiManager(surfaceView, videoEncoder.getInputSurface(),
        surfaceView.getContext());
    return result;
  }

  public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) {
    srsFlvMuxer.setSampleRate(sampleRate);
    srsFlvMuxer.setIsStereo(isStereo);
    microphoneManager.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor);
    return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo);
  }

  public boolean prepareVideo() {
    boolean result = videoEncoder.prepareVideoEncoder(640, 480, 30, 1200 * 1024, 90, true,
        FormatVideoEncoder.SURFACE);
    cameraManager = new Camera2ApiManager(surfaceView, videoEncoder.getInputSurface(),
        surfaceView.getContext());
    return result;
  }

  //set 16000hz sample rate because 44100 produce desynchronization audio in rtsp
  public boolean prepareAudio() {
    microphoneManager.setSampleRate(16000);
    audioEncoder.setSampleRate(16000);
    microphoneManager.createMicrophone();
    srsFlvMuxer.setSampleRate(microphoneManager.getSampleRate());
    return audioEncoder.prepareAudioEncoder();
  }

  public void startStream(String url) {
    srsFlvMuxer.start(url);
    srsFlvMuxer.setVideoResolution(videoEncoder.getWidth(), videoEncoder.getHeight());
    videoEncoder.start();
    audioEncoder.start();
    cameraManager.openCameraBack();
    microphoneManager.start();
    streaming = true;
  }

  public void stopStream() {
    srsFlvMuxer.stop();
    cameraManager.closeCamera();
    microphoneManager.stop();
    videoEncoder.stop();
    audioEncoder.stop();
    streaming = false;
  }

  public void disableAudio() {
    microphoneManager.mute();
  }

  public void enableAudio() {
    microphoneManager.unMute();
  }

  public void disableVideo() {
    videoEncoder.startSendBlackImage();
    videoEnabled = false;
  }

  public void enableVideo() {
    videoEncoder.stopSendBlackImage();
    videoEnabled = true;
  }

  public boolean isAudioMuted() {
    return microphoneManager.isMuted();
  }

  public boolean isVideoEnabled() {
    return videoEnabled;
  }

  public void switchCamera() throws CameraOpenException {
    if (isStreaming()) {
      cameraManager.switchCamera();
    }
  }

  /** need min API 19 */
  public void setVideoBitrateOnFly(int bitrate) {
    if (Build.VERSION.SDK_INT >= 19) {
      videoEncoder.setVideoBitrateOnFly(bitrate);
    }
  }

  public boolean isStreaming() {
    return streaming;
  }

  //public void setEffect(EffectManager effect) {
  //  if (isStreaming()) {
  //    cameraManager.setEffect(effect);
  //  }
  //}

  @Override
  public void getAccData(ByteBuffer accBuffer, MediaCodec.BufferInfo info) {
    srsFlvMuxer.sendAudio(accBuffer, info);
  }

  @Override
  public void onSPSandPPS(ByteBuffer sps, ByteBuffer pps) {
    srsFlvMuxer.setSpsPPs(sps, pps);
  }

  @Override
  public void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    srsFlvMuxer.sendVideo(h264Buffer, info);
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
}

