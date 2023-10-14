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

package com.pedro.library.rtmp;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.utils.CodecUtil;
import com.pedro.library.base.Camera2Base;
import com.pedro.library.util.streamclient.RtmpStreamClient;
import com.pedro.library.util.streamclient.StreamClientListener;
import com.pedro.library.view.LightOpenGlView;
import com.pedro.library.view.OpenGlView;
import com.pedro.rtmp.rtmp.RtmpClient;
import com.pedro.rtmp.rtmp.VideoCodec;
import com.pedro.rtmp.utils.ConnectCheckerRtmp;

import java.nio.ByteBuffer;

/**
 * More documentation see:
 * {@link com.pedro.library.base.Camera2Base}
 *
 * Created by pedro on 6/07/17.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RtmpCamera2 extends Camera2Base {

  private final RtmpClient rtmpClient;
  private final RtmpStreamClient streamClient;
  private final StreamClientListener streamClientListener = this::requestKeyFrame;

  /**
   * @deprecated This view produce rotations problems and could be unsupported in future versions.
   * Use {@link Camera2Base#Camera2Base(OpenGlView)} or {@link Camera2Base#Camera2Base(LightOpenGlView)}
   * instead.
   */
  @Deprecated
  public RtmpCamera2(SurfaceView surfaceView, ConnectCheckerRtmp connectChecker) {
    super(surfaceView);
    rtmpClient = new RtmpClient(connectChecker);
    streamClient = new RtmpStreamClient(rtmpClient, streamClientListener);
  }

  /**
   * @deprecated This view produce rotations problems and could be unsupported in future versions.
   * Use {@link Camera2Base#Camera2Base(OpenGlView)} or {@link Camera2Base#Camera2Base(LightOpenGlView)}
   * instead.
   */
  @Deprecated
  public RtmpCamera2(TextureView textureView, ConnectCheckerRtmp connectChecker) {
    super(textureView);
    rtmpClient = new RtmpClient(connectChecker);
    streamClient = new RtmpStreamClient(rtmpClient, streamClientListener);
  }

  public RtmpCamera2(OpenGlView openGlView, ConnectCheckerRtmp connectChecker) {
    super(openGlView);
    rtmpClient = new RtmpClient(connectChecker);
    streamClient = new RtmpStreamClient(rtmpClient, streamClientListener);
  }

  public RtmpCamera2(LightOpenGlView lightOpenGlView, ConnectCheckerRtmp connectChecker) {
    super(lightOpenGlView);
    rtmpClient = new RtmpClient(connectChecker);
    streamClient = new RtmpStreamClient(rtmpClient, streamClientListener);
  }

  public RtmpCamera2(Context context, boolean useOpengl, ConnectCheckerRtmp connectChecker) {
    super(context, useOpengl);
    rtmpClient = new RtmpClient(connectChecker);
    streamClient = new RtmpStreamClient(rtmpClient, streamClientListener);
  }

  public RtmpStreamClient getStreamClient() {
    return streamClient;
  }

  public void setVideoCodec(VideoCodec videoCodec) {
    recordController.setVideoMime(
            videoCodec == VideoCodec.H265 ? CodecUtil.H265_MIME : CodecUtil.H264_MIME);
    videoEncoder.setType(videoCodec == VideoCodec.H265 ? CodecUtil.H265_MIME : CodecUtil.H264_MIME);
    rtmpClient.setVideoCodec(videoCodec);
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
    rtmpClient.setOnlyVideo(!audioInitialized);
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

