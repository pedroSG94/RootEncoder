package com.pedro.rtmpstreamer;

import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.util.Base64;
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

import com.pedro.rtsp.rtp.packets.H264Packet;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.rtp.packets.AccPacket;

import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 10/02/17.
 */

public class RtspBuilder implements GetAccData, GetCameraData, GetH264Data, GetMicrophoneData {

  private Camera1ApiManager cameraManager;
  private VideoEncoder videoEncoder;
  private MicrophoneManager microphoneManager;
  private AudioEncoder audioEncoder;
  private boolean streaming;

  private RtspClient rtspClient;
  private AccPacket accPacket;
  private H264Packet h264Packet;

  public RtspBuilder(SurfaceView surfaceView, Protocol protocol, ConnectCheckerRtsp connectCheckerRtsp) {
    rtspClient = new RtspClient(connectCheckerRtsp, protocol);
    accPacket = new AccPacket(rtspClient, protocol);
    h264Packet = new H264Packet(rtspClient, protocol);

    cameraManager = new Camera1ApiManager(surfaceView, this);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
    streaming = false;
  }

  public void setAuthorization(String user, String password){
    rtspClient.setAuthorization(user, password);
  }

  public void prepareVideo(int width, int height, int fps, int bitrate, int rotation) {
    cameraManager.prepareCamera(width, height, fps, rotation, ImageFormat.NV21);
    videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation,
        FormatVideoEncoder.YUV420PLANAR);
  }

  public void prepareAudio(int bitrate, int sampleRate, boolean isStereo) {
    microphoneManager.createMicrophone(sampleRate, isStereo);
    audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo);
    rtspClient.setSampleRate(sampleRate);
    accPacket.setSampleRate(sampleRate);
  }

  public void prepareVideo() {
    cameraManager.prepareCamera();
    videoEncoder.prepareVideoEncoder();
  }

  public void prepareAudio() {
    microphoneManager.createMicrophone();
    audioEncoder.prepareAudioEncoder();
    rtspClient.setSampleRate(microphoneManager.getSampleRate());
    accPacket.setSampleRate(microphoneManager.getSampleRate());
  }

  public void startStream(String url) {
    videoEncoder.start();
    audioEncoder.start();
    cameraManager.start();
    microphoneManager.start();
    streaming = true;
    rtspClient.setUrl(url);
  }

  public void stopStream() {
    rtspClient.disconnect();
    cameraManager.stop();
    microphoneManager.stop();
    videoEncoder.stop();
    audioEncoder.stop();
    streaming = false;
    accPacket.close();
    h264Packet.close();
  }

  public void enableDisableLantern() {
    if (isStreaming()) {
      if (cameraManager.isLanternEnable()) {
        cameraManager.disableLantern();
      } else {
        cameraManager.enableLantern();
      }
    }
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
    accPacket.createAndSendPacket(accBuffer, info);
  }

  @Override
  public void onSPSandPPS(ByteBuffer sps, ByteBuffer pps) {
    byte[] mSPS = new byte[sps.capacity() - 4];
    sps.position(4);
    sps.get(mSPS, 0, mSPS.length);
    byte[] mPPS = new byte[pps.capacity() - 4];
    pps.position(4);
    pps.get(mPPS, 0, mPPS.length);

    String sSPS = Base64.encodeToString(mSPS, 0, mSPS.length, Base64.NO_WRAP);
    String sPPS = Base64.encodeToString(mPPS, 0, mPPS.length, Base64.NO_WRAP);
    rtspClient.setSPSandPPS(sSPS, sPPS);
    rtspClient.connect();
  }

  @Override
  public void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    h264Packet.createAndSendPacket(h264Buffer, info);
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

  public void updateDestination() {
    accPacket.updateDestinationAudio();
    h264Packet.updateDestinationVideo();
  }
}
