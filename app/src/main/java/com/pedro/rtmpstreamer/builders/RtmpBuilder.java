package com.pedro.rtmpstreamer.builders;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.view.SurfaceView;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAccData;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.video.Camera1ApiManager;
import com.pedro.encoder.input.video.EffectManager;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetH264Data;
import com.pedro.encoder.video.VideoEncoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.ossrs.rtmp.ConnectCheckerRtmp;
import net.ossrs.rtmp.SrsFlvMuxer;

/**
 * Created by pedro on 25/01/17.
 */

public class RtmpBuilder implements GetAccData, GetCameraData, GetH264Data, GetMicrophoneData {

  private int width;
  private int height;
  private Camera1ApiManager cameraManager;
  private VideoEncoder videoEncoder;
  private MicrophoneManager microphoneManager;
  private AudioEncoder audioEncoder;
  private SrsFlvMuxer srsFlvMuxer;
  private boolean streaming;
  private ConnectCheckerRtmp connectChecker;

  public RtmpBuilder(SurfaceView surfaceView, ConnectCheckerRtmp connectChecker) {
    this.connectChecker = connectChecker;
    cameraManager = new Camera1ApiManager(surfaceView, this);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
    srsFlvMuxer = new SrsFlvMuxer();
    streaming = false;
  }

  public void prepareVideo(int width, int height, int fps, int bitrate, int rotation) {
    this.width = width;
    this.height = height;
    cameraManager.prepareCamera(width, height, fps, rotation, ImageFormat.NV21);
    videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation,
        FormatVideoEncoder.YUV420PLANAR);
  }

  public void prepareAudio(int bitrate, int sampleRate, boolean isStereo) {
    microphoneManager.createMicrophone(sampleRate, isStereo);
    audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo);
  }

  public void prepareVideo() {
    cameraManager.prepareCamera();
    videoEncoder.prepareVideoEncoder();
    width = videoEncoder.getWidth();
    height = videoEncoder.getHeight();
  }

  public void prepareAudio() {
    microphoneManager.createMicrophone();
    audioEncoder.prepareAudioEncoder();
  }

  public void startStream(String url) {
    srsFlvMuxer.start(url, connectChecker);
    srsFlvMuxer.setVideoResolution(width, height);
    videoEncoder.start();
    audioEncoder.start();
    cameraManager.start();
    microphoneManager.start();
    streaming = true;
  }

  public void stopStream() {
    srsFlvMuxer.stop(connectChecker);
    cameraManager.stop();
    microphoneManager.stop();
    videoEncoder.stop();
    audioEncoder.stop();
    streaming = false;
  }

  public List<String> getResolutions(){
    List<Camera.Size> list = cameraManager.getPreviewSize();
    List<String> resolutions = new ArrayList<>();
    for(Camera.Size size : list){
      resolutions.add(size.width + "X" + size.height);
    }
    return resolutions;
  }

  public void switchCamera() {
    if (isStreaming()) {
      cameraManager.switchCamera();
    }
  }

  public boolean isStreaming() {
    return streaming;
  }

  public void setEffect(EffectManager effect) {
    if (isStreaming()) {
      cameraManager.setEffect(effect);
    }
  }

  @Override
  public void getAccData(ByteBuffer accBuffer, MediaCodec.BufferInfo info) {
    srsFlvMuxer.sendAudio(accBuffer, info);
  }

  @Override
  public void onSPSandPPS(ByteBuffer sps, ByteBuffer pps) {
    //used on rtsp
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
  public void inputYv12Data(byte[] buffer, int width, int height) {
    videoEncoder.inputYv12Data(buffer, width, height);
  }

  @Override
  public void inputNv21Data(byte[] buffer, int width, int height) {
    videoEncoder.inputNv21Data(buffer, width, height);
  }
}
