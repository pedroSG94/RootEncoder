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

import android.media.MediaCodec;

import com.pedro.common.AudioCodec;
import com.pedro.common.ConnectChecker;
import com.pedro.library.base.OnlyAudioBase;
import com.pedro.library.util.streamclient.RtmpStreamClient;
import com.pedro.rtmp.rtmp.RtmpClient;

import java.nio.ByteBuffer;

/**
 * More documentation see:
 * {@link com.pedro.library.base.OnlyAudioBase}
 *
 * Created by pedro on 10/07/18.
 */
public class RtmpOnlyAudio extends OnlyAudioBase {

  private final RtmpClient rtmpClient;
  private final RtmpStreamClient streamClient;

  public RtmpOnlyAudio(ConnectChecker connectChecker) {
    super();
    rtmpClient = new RtmpClient(connectChecker);
    streamClient = new RtmpStreamClient(rtmpClient, null);
    streamClient.setOnlyAudio(true);
  }

  @Override
  public RtmpStreamClient getStreamClient() {
    return streamClient;
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
}
