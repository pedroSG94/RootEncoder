package com.pedro.rtplibrary.rtsp;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;

import com.pedro.rtplibrary.base.Camera2Base;
import com.pedro.rtplibrary.view.OpenGlView;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;

import java.nio.ByteBuffer;

/**
 * Created by pedro on 4/06/17.
 * This builder is under test, rotation only work with hardware because use encoding surface mode.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RtspCamera2 extends Camera2Base {

  private RtspClient rtspClient;

  public RtspCamera2(Context context, ConnectCheckerRtsp connectCheckerRtsp) {
    super(context);
    initialize(connectCheckerRtsp);
  }

  public RtspCamera2(Context context, Surface surfacePreview, ConnectCheckerRtsp connectCheckerRtsp) {
    super(surfacePreview, context);
    initialize(connectCheckerRtsp);
  }

  public RtspCamera2(SurfaceView surfaceView, ConnectCheckerRtsp connectCheckerRtsp) {
    super(getSurfaceFactory(surfaceView), surfaceView.getContext());
    initialize(connectCheckerRtsp);
  }

  public RtspCamera2(TextureView textureView, ConnectCheckerRtsp connectCheckerRtsp) {
    super(getSurfaceFactory(textureView), textureView.getContext());
    initialize(connectCheckerRtsp);
  }

  public RtspCamera2(OpenGlView openGlView, ConnectCheckerRtsp connectCheckerRtsp) {
    super(openGlView, openGlView.getContext());
    initialize(connectCheckerRtsp);
  }

  private void initialize(ConnectCheckerRtsp connectCheckerRtsp) {
    rtspClient = new RtspClient(connectCheckerRtsp);
  }

  public void setProtocol(Protocol protocol) {
    rtspClient.setProtocol(protocol);
  }

  @Override
  public void setAuthorization(String user, String password) {
    rtspClient.setAuthorization(user, password);
  }

  @Override
  protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
    rtspClient.setIsStereo(isStereo);
    rtspClient.setSampleRate(sampleRate);
  }

  @Override
  public boolean prepareAudio() {
    microphoneManager.setSampleRate(16000);
    audioEncoder.setSampleRate(16000);
    microphoneManager.createMicrophone();
    rtspClient.setSampleRate(microphoneManager.getSampleRate());
    return audioEncoder.prepareAudioEncoder();
  }

  @Override
  protected void startStreamRtp(String url) {
    rtspClient.setUrl(url);
    if (!cameraManager.isPrepared()) {
      rtspClient.connect();
    }
  }

  @Override
  protected void stopStreamRtp() {
    rtspClient.disconnect();
  }

  @Override
  protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    rtspClient.sendAudio(aacBuffer, info);
  }

  @Override
  protected void onSPSandPPSRtp(ByteBuffer sps, ByteBuffer pps) {
    ByteBuffer newSps = sps.duplicate();
    ByteBuffer newPps = pps.duplicate();
    rtspClient.setSPSandPPS(newSps, newPps);
    rtspClient.connect();
  }

  @Override
  protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    rtspClient.sendVideo(h264Buffer, info);
  }
}

