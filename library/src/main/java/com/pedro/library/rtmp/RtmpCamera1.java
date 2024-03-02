/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.library.rtmp;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.RequiresApi;

import com.pedro.common.AudioCodec;
import com.pedro.common.ConnectChecker;
import com.pedro.common.VideoCodec;
import com.pedro.library.base.Camera1Base;
import com.pedro.library.util.streamclient.RtmpStreamClient;
import com.pedro.library.util.streamclient.StreamClientListener;
import com.pedro.library.view.OpenGlView;
import com.pedro.rtmp.rtmp.RtmpClient;

import java.nio.ByteBuffer;

/**
 * More documentation see:
 * {@link com.pedro.library.base.Camera1Base}
 *
 * Created by pedro on 25/01/17.
 */

public class RtmpCamera1 extends Camera1Base {

  private final RtmpClient rtmpClient;
  private final RtmpStreamClient streamClient;
  private final StreamClientListener streamClientListener = this::requestKeyFrame;

  public RtmpCamera1(SurfaceView surfaceView, ConnectChecker connectChecker) {
    super(surfaceView);
    rtmpClient = new RtmpClient(connectChecker);
    streamClient = new RtmpStreamClient(rtmpClient, streamClientListener);
  }

  public RtmpCamera1(TextureView textureView, ConnectChecker connectChecker) {
    super(textureView);
    rtmpClient = new RtmpClient(connectChecker);
    streamClient = new RtmpStreamClient(rtmpClient, streamClientListener);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public RtmpCamera1(OpenGlView openGlView, ConnectChecker connectChecker) {
    super(openGlView);
    rtmpClient = new RtmpClient(connectChecker);
    streamClient = new RtmpStreamClient(rtmpClient, streamClientListener);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public RtmpCamera1(Context context, ConnectChecker connectChecker) {
    super(context);
    rtmpClient = new RtmpClient(connectChecker);
    streamClient = new RtmpStreamClient(rtmpClient, streamClientListener);
  }

  @Override
  public RtmpStreamClient getStreamClient() {
    return streamClient;
  }

  @Override
  protected void setVideoCodecImp(VideoCodec codec) {
      rtmpClient.setVideoCodec(codec);
  }

  @Override
  protected void setAudioCodecImp(AudioCodec codec) {
    rtmpClient.setAudioCodec(codec);
  }

  @Override
  protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
    rtmpClient.setAudioInfo(sampleRate, isStereo);
  }

  @Override
  protected void startStreamRtp(String url) {
    if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
      rtmpClient.setVideoResolution(videoEncoder.getHeight(), videoEncoder.getWidth());
    } else {
      rtmpClient.setVideoResolution(videoEncoder.getWidth(), videoEncoder.getHeight());
    }
    rtmpClient.setFps(videoEncoder.getFps());
    rtmpClient.connect(url);
  }

  @Override
  protected void stopStreamRtp() {
    rtmpClient.disconnect();
  }

  @Override
  protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    rtmpClient.sendAudio(aacBuffer, info);
  }

  @Override
  protected void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
    rtmpClient.setVideoInfo(sps, pps, vps);
  }

  @Override
  protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    rtmpClient.sendVideo(h264Buffer, info);
  }
}
