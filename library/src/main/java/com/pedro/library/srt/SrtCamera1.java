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

package com.pedro.library.srt;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.utils.CodecUtil;
import com.pedro.library.base.Camera1Base;
import com.pedro.library.util.streamclient.SrtStreamClient;
import com.pedro.library.util.streamclient.StreamClientListener;
import com.pedro.library.view.LightOpenGlView;
import com.pedro.library.view.OpenGlView;
import com.pedro.srt.srt.SrtClient;
import com.pedro.srt.srt.VideoCodec;
import com.pedro.srt.utils.ConnectCheckerSrt;

import java.nio.ByteBuffer;

/**
 * More documentation see:
 * {@link Camera1Base}
 *
 * Created by pedro on 8/9/23.
 */

public class SrtCamera1 extends Camera1Base {

  private final SrtClient srtClient;
  private final SrtStreamClient streamClient;
  private final StreamClientListener streamClientListener = this::requestKeyFrame;

  public SrtCamera1(SurfaceView surfaceView, ConnectCheckerSrt connectChecker) {
    super(surfaceView);
    srtClient = new SrtClient(connectChecker);
    streamClient = new SrtStreamClient(srtClient, streamClientListener);
  }

  public SrtCamera1(TextureView textureView, ConnectCheckerSrt connectChecker) {
    super(textureView);
    srtClient = new SrtClient(connectChecker);
    streamClient = new SrtStreamClient(srtClient, streamClientListener);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public SrtCamera1(OpenGlView openGlView, ConnectCheckerSrt connectChecker) {
    super(openGlView);
    srtClient = new SrtClient(connectChecker);
    streamClient = new SrtStreamClient(srtClient, streamClientListener);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public SrtCamera1(LightOpenGlView lightOpenGlView, ConnectCheckerSrt connectChecker) {
    super(lightOpenGlView);
    srtClient = new SrtClient(connectChecker);
    streamClient = new SrtStreamClient(srtClient, streamClientListener);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public SrtCamera1(Context context, ConnectCheckerSrt connectChecker) {
    super(context);
    srtClient = new SrtClient(connectChecker);
    streamClient = new SrtStreamClient(srtClient, streamClientListener);
  }

  public SrtStreamClient getStreamClient() {
    return streamClient;
  }

  public void setVideoCodec(VideoCodec videoCodec) {
    recordController.setVideoMime(
            videoCodec == VideoCodec.H265 ? CodecUtil.H265_MIME : CodecUtil.H264_MIME);
    videoEncoder.setType(videoCodec == VideoCodec.H265 ? CodecUtil.H265_MIME : CodecUtil.H264_MIME);
    srtClient.setVideoCodec(videoCodec);
  }

  @Override
  protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
    srtClient.setAudioInfo(sampleRate, isStereo);
  }

  @Override
  protected void startStreamRtp(String url) {
    srtClient.setOnlyVideo(!audioInitialized);
    srtClient.connect(url);
  }

  @Override
  protected void stopStreamRtp() {
    srtClient.disconnect();
  }

  @Override
  protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    srtClient.sendAudio(aacBuffer, info);
  }

  @Override
  protected void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
    srtClient.setVideoInfo(sps, pps, vps);
  }

  @Override
  protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    srtClient.sendVideo(h264Buffer, info);
  }
}
