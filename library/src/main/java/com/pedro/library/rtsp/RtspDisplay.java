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

package com.pedro.library.rtsp;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.pedro.common.AudioCodec;
import com.pedro.common.ConnectChecker;
import com.pedro.common.VideoCodec;
import com.pedro.library.base.DisplayBase;
import com.pedro.library.util.streamclient.RtspStreamClient;
import com.pedro.library.util.streamclient.StreamClientListener;
import com.pedro.rtsp.rtsp.RtspClient;

import java.nio.ByteBuffer;

/**
 * More documentation see:
 * {@link com.pedro.library.base.DisplayBase}
 *
 * Created by pedro on 9/08/17.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RtspDisplay extends DisplayBase {

  private final RtspClient rtspClient;
  private final RtspStreamClient streamClient;
  private final StreamClientListener streamClientListener = this::requestKeyFrame;

  public RtspDisplay(Context context, boolean useOpengl, ConnectChecker connectChecker) {
    super(context, useOpengl);
    rtspClient = new RtspClient(connectChecker);
    streamClient = new RtspStreamClient(rtspClient, streamClientListener);
  }

  @Override
  public RtspStreamClient getStreamClient() {
    return streamClient;
  }

  @Override
  protected void setVideoCodecImp(VideoCodec codec) {
      rtspClient.setVideoCodec(codec);
  }

  @Override
  protected void setAudioCodecImp(AudioCodec codec) {
    rtspClient.setAudioCodec(codec);
  }

  @Override
  protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
    rtspClient.setAudioInfo(sampleRate, isStereo);
  }

  @Override
  protected void startStreamRtp(String url) {
    rtspClient.connect(url);
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
  protected void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
    rtspClient.setVideoInfo(sps, pps, vps);
  }

  @Override
  protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    rtspClient.sendVideo(h264Buffer, info);
  }
}
