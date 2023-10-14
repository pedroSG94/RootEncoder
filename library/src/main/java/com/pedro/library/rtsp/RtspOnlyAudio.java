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

package com.pedro.library.rtsp;

import android.media.MediaCodec;

import androidx.annotation.Nullable;

import com.pedro.library.base.OnlyAudioBase;
import com.pedro.library.util.streamclient.RtspStreamClient;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;

import java.nio.ByteBuffer;

/**
 * More documentation see:
 * {@link com.pedro.library.base.OnlyAudioBase}
 *
 * Created by pedro on 10/07/18.
 */
public class RtspOnlyAudio extends OnlyAudioBase {

  private final RtspClient rtspClient;
  private final RtspStreamClient streamClient;

  public RtspOnlyAudio(ConnectCheckerRtsp connectCheckerRtsp) {
    super();
    rtspClient = new RtspClient(connectCheckerRtsp);
    rtspClient.setOnlyAudio(true);
    streamClient = new RtspStreamClient(rtspClient, null);
  }

  public RtspStreamClient getStreamClient() {
    return streamClient;
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
}