/*
 * Copyright (C) 2021 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.library.generic;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.RequiresApi;

import com.pedro.common.ConnectChecker;
import com.pedro.common.VideoCodec;
import com.pedro.library.base.Camera1Base;
import com.pedro.library.util.streamclient.GenericStreamClient;
import com.pedro.library.util.streamclient.RtmpStreamClient;
import com.pedro.library.util.streamclient.RtspStreamClient;
import com.pedro.library.util.streamclient.SrtStreamClient;
import com.pedro.library.util.streamclient.StreamClientListener;
import com.pedro.library.view.LightOpenGlView;
import com.pedro.library.view.OpenGlView;
import com.pedro.rtmp.rtmp.RtmpClient;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.srt.srt.SrtClient;

import java.nio.ByteBuffer;

/**
 * Created by Ernovation on 9/11/21.
 * <p>
 * Experiment class.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class GenericCamera1 extends Camera1Base {

  private final static String TAG = "GenericCamera1";
  private RtmpClient rtmpClient;
  private RtspClient rtspClient;
  private SrtClient srtClient;
  private GenericStreamClient streamClient;
  private ClientType connectedType = ClientType.NONE;
  private final StreamClientListener streamClientListener = this::requestKeyFrame;
  private ConnectChecker connectChecker;

  @Deprecated
  public GenericCamera1(SurfaceView surfaceView, ConnectChecker connectChecker) {
    super(surfaceView);
    rtmpClient = new RtmpClient(connectChecker);
    rtspClient = new RtspClient(connectChecker);
    srtClient = new SrtClient(connectChecker);
    streamClient = new GenericStreamClient(
            new RtmpStreamClient(rtmpClient, streamClientListener),
            new RtspStreamClient(rtspClient, streamClientListener),
            new SrtStreamClient(srtClient, streamClientListener),
            streamClientListener);
  }

  @Deprecated
  public GenericCamera1(TextureView textureView, ConnectChecker connectChecker) {
    super(textureView);
    init(connectChecker);
  }

  public GenericCamera1(OpenGlView openGlView, ConnectChecker connectChecker) {
    super(openGlView);
    init(connectChecker);
  }

  public GenericCamera1(LightOpenGlView lightOpenGlView, ConnectChecker connectChecker) {
    super(lightOpenGlView);
    init(connectChecker);
  }

  public GenericCamera1(Context context, ConnectChecker connectChecker) {
    super(context);
    init(connectChecker);
  }

  private void init(ConnectChecker connectChecker) {
    this.connectChecker = connectChecker;
    rtmpClient = new RtmpClient(connectChecker);
    rtspClient = new RtspClient(connectChecker);
    srtClient = new SrtClient(connectChecker);
    streamClient = new GenericStreamClient(
        new RtmpStreamClient(rtmpClient, streamClientListener),
        new RtspStreamClient(rtspClient, streamClientListener),
        new SrtStreamClient(srtClient, streamClientListener),
        streamClientListener);
  }

  @Override
  public GenericStreamClient getStreamClient() {
    return streamClient;
  }

  @Override
  protected void setVideoCodecImp(VideoCodec codec) {
    rtmpClient.setVideoCodec(codec);
    rtspClient.setVideoCodec(codec);
    srtClient.setVideoCodec(codec);
  }

  @Override
  protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
    rtmpClient.setAudioInfo(sampleRate, isStereo);
    rtspClient.setAudioInfo(sampleRate, isStereo);
    srtClient.setAudioInfo(sampleRate, isStereo);
  }

  @Override
  protected void startStreamRtp(String url) {
    streamClient.connecting(url);
    if (url.toLowerCase().startsWith("rtmp")) {
      connectedType = ClientType.RTMP;
      startStreamRtpRtmp(url);
    } else if (url.toLowerCase().startsWith("rtsp")) {
      connectedType = ClientType.RTSP;
      startStreamRtpRtsp(url);
    } else if (url.toLowerCase().startsWith("srt")){
      connectedType = ClientType.SRT;
      startStreamRtpSrt(url);
    } else {
      connectChecker.onConnectionFailed("unsupported protocol, only support rtmp, rtsp and srt");
    }
  }

  private void startStreamRtpRtmp(String url) {
    if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
      rtmpClient.setVideoResolution(videoEncoder.getHeight(), videoEncoder.getWidth());
    } else {
      rtmpClient.setVideoResolution(videoEncoder.getWidth(), videoEncoder.getHeight());
    }
    rtmpClient.setFps(videoEncoder.getFps());
    rtmpClient.setOnlyVideo(!audioInitialized);
    rtmpClient.connect(url);
  }

  private void startStreamRtpRtsp(String url) {
    rtspClient.setOnlyVideo(!audioInitialized);
    rtspClient.connect(url);
  }

  private void startStreamRtpSrt(String url) {
    srtClient.setOnlyVideo(!audioInitialized);
    srtClient.connect(url);
  }

  @Override
  protected void stopStreamRtp() {
    switch (connectedType) {
      case RTMP -> rtmpClient.disconnect();
      case RTSP -> rtspClient.disconnect();
      case SRT -> srtClient.disconnect();
      default -> {}
    }
    connectedType = ClientType.NONE;
  }

  @Override
  protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    switch (connectedType) {
      case RTMP -> rtmpClient.sendAudio(aacBuffer, info);
      case RTSP -> rtspClient.sendAudio(aacBuffer, info);
      case SRT -> srtClient.sendAudio(aacBuffer, info);
      default -> {}
    }
  }

  @Override
  protected void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
    switch (connectedType) {
      case RTMP -> rtmpClient.setVideoInfo(sps, pps, vps);
      case RTSP -> rtspClient.setVideoInfo(sps, pps, vps);
      case SRT -> srtClient.setVideoInfo(sps, pps, vps);
      default -> {}
    }
  }

  @Override
  protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    switch (connectedType) {
      case RTMP -> rtmpClient.sendVideo(h264Buffer, info);
      case RTSP -> rtspClient.sendVideo(h264Buffer, info);
      case SRT -> srtClient.sendVideo(h264Buffer, info);
      default -> {}
    }
  }
}
