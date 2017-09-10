package com.pedro.rtplibrary.rtmp;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.SurfaceView;
import android.view.TextureView;

import com.pedro.rtplibrary.base.Camera2Base;

import com.pedro.rtplibrary.view.OpenGlView;
import net.ossrs.rtmp.ConnectCheckerRtmp;
import net.ossrs.rtmp.SrsFlvMuxer;

import java.nio.ByteBuffer;

/**
 * Created by pedro on 6/07/17.
 * This builder is under test, rotation only work with hardware because use encoding surface mode.
 * This maybe don't work for synchronizations problems and you will lose audio or video channel in
 * the stream
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RtmpCamera2 extends Camera2Base {

  private SrsFlvMuxer srsFlvMuxer;

  public RtmpCamera2(SurfaceView surfaceView, ConnectCheckerRtmp connectChecker) {
    super(surfaceView, surfaceView.getContext());
    srsFlvMuxer = new SrsFlvMuxer(connectChecker);
  }

  public RtmpCamera2(TextureView textureView, ConnectCheckerRtmp connectChecker) {
    super(textureView, textureView.getContext());
    srsFlvMuxer = new SrsFlvMuxer(connectChecker);
  }

  public RtmpCamera2(OpenGlView openGlView, ConnectCheckerRtmp connectChecker) {
    super(openGlView, openGlView.getContext());
    srsFlvMuxer = new SrsFlvMuxer(connectChecker);
  }

  public RtmpCamera2(Context context, ConnectCheckerRtmp connectChecker) {
    super(context);
    srsFlvMuxer = new SrsFlvMuxer(connectChecker);
  }

  @Override
  public void setAuthorization(String user, String password) {
    srsFlvMuxer.setAuthorization(user, password);
  }

  @Override
  protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
    srsFlvMuxer.setIsStereo(isStereo);
    srsFlvMuxer.setSampleRate(sampleRate);
  }

  @Override
  public boolean prepareAudio() {
    microphoneManager.createMicrophone();
    return audioEncoder.prepareAudioEncoder();
  }

  @Override
  protected void startStreamRtp(String url) {
    if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
      srsFlvMuxer.setVideoResolution(videoEncoder.getHeight(), videoEncoder.getWidth());
    } else {
      srsFlvMuxer.setVideoResolution(videoEncoder.getWidth(), videoEncoder.getHeight());
    }
    srsFlvMuxer.start(url);
  }

  @Override
  protected void stopStreamRtp() {
    srsFlvMuxer.stop();
  }

  @Override
  protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    srsFlvMuxer.sendAudio(aacBuffer, info);
  }

  @Override
  protected void onSPSandPPSRtp(ByteBuffer sps, ByteBuffer pps) {
    srsFlvMuxer.setSpsPPs(sps, pps);
  }

  @Override
  protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    srsFlvMuxer.sendVideo(h264Buffer, info);
  }
}

