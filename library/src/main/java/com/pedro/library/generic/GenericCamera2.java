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
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.RequiresApi;

import com.pedro.common.ConnectChecker;
import com.pedro.common.VideoCodec;
import com.pedro.library.base.Camera2Base;
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
public class GenericCamera2 extends Camera2Base {

  private final static String TAG = "GenericCamera2";
  private enum ClientType { NONE, RTMP, RTSP, SRT};

  private final RtmpClient rtmpClient;
  private final RtspClient rtspClient;
  private final SrtClient srtClient;
  private final GenericStreamClient streamClient;
  private ClientType connectedType = ClientType.NONE;
  private final StreamClientListener streamClientListener = this::requestKeyFrame;


  @Deprecated
  public GenericCamera2(SurfaceView surfaceView, ConnectChecker connectChecker) {
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
  public GenericCamera2(TextureView textureView, ConnectChecker connectChecker) {
    super(textureView);
    rtmpClient = new RtmpClient(connectChecker);
    rtspClient = new RtspClient(connectChecker);
    srtClient = new SrtClient(connectChecker);
    streamClient = new GenericStreamClient(
            new RtmpStreamClient(rtmpClient, streamClientListener),
            new RtspStreamClient(rtspClient, streamClientListener),
            new SrtStreamClient(srtClient, streamClientListener),
            streamClientListener);  }

  public GenericCamera2(OpenGlView openGlView, ConnectChecker connectChecker) {
    super(openGlView);
    rtmpClient = new RtmpClient(connectChecker);
    rtspClient = new RtspClient(connectChecker);
    srtClient = new SrtClient(connectChecker);
    streamClient = new GenericStreamClient(
            new RtmpStreamClient(rtmpClient, streamClientListener),
            new RtspStreamClient(rtspClient, streamClientListener),
            new SrtStreamClient(srtClient, streamClientListener),
            streamClientListener);
  }

  public GenericCamera2(LightOpenGlView lightOpenGlView, ConnectChecker connectChecker) {
    super(lightOpenGlView);
    rtmpClient = new RtmpClient(connectChecker);
    rtspClient = new RtspClient(connectChecker);
    srtClient = new SrtClient(connectChecker);
    streamClient = new GenericStreamClient(
            new RtmpStreamClient(rtmpClient, streamClientListener),
            new RtspStreamClient(rtspClient, streamClientListener),
            new SrtStreamClient(srtClient, streamClientListener),
            streamClientListener);  }

  public GenericCamera2(Context context, boolean useOpengl, ConnectChecker connectChecker) {
    super(context, useOpengl);
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
    if (RtmpClient.getUrlPattern().matcher(url).matches()) {
      connectedType = ClientType.RTMP;
      startStreamRtpRtmp(url);
    } else if (RtspClient.getUrlPattern().matcher(url).matches()) {
      connectedType = ClientType.RTSP;
      startStreamRtpRtsp(url);
    } else {
      // catch all here, to let it handle improper URLs
      connectedType = ClientType.SRT;
      startStreamRtpSrt(url);
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
      case RTMP: rtmpClient.disconnect(); break;
      case RTSP: rtspClient.disconnect(); break;
      case SRT: srtClient.disconnect(); break;
      default:
    }
  }

  @Override
  protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    switch (connectedType) {
      case RTMP: rtmpClient.sendAudio(aacBuffer, info); break;
      case RTSP: rtspClient.sendAudio(aacBuffer, info); break;
      case SRT: srtClient.sendAudio(aacBuffer, info); break;
      default:
    }
  }

  @Override
  protected void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
    switch (connectedType) {
      case RTMP: rtmpClient.setVideoInfo(sps, pps, vps); break;
      case RTSP: rtspClient.setVideoInfo(sps, pps, vps); break;
      case SRT: srtClient.setVideoInfo(sps, pps, vps); break;
      default:
    }
  }

  @Override
  protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    switch (connectedType) {
      case RTMP: rtmpClient.sendVideo(h264Buffer, info); break;
      case RTSP: rtspClient.sendVideo(h264Buffer, info); break;
      case SRT: srtClient.sendVideo(h264Buffer, info); break;
      default:
    }
  }
}
