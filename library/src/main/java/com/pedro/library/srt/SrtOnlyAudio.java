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

import android.media.MediaCodec;

import androidx.annotation.Nullable;

import com.pedro.library.base.OnlyAudioBase;
import com.pedro.library.util.streamclient.SrtStreamClient;
import com.pedro.srt.srt.SrtClient;
import com.pedro.srt.utils.ConnectCheckerSrt;

import java.nio.ByteBuffer;

/**
 * More documentation see:
 * {@link OnlyAudioBase}
 *
 * Created by pedro on 8/9/23.
 */
public class SrtOnlyAudio extends OnlyAudioBase {

  private final SrtClient srtClient;
  private final SrtStreamClient streamClient;

  public SrtOnlyAudio(ConnectCheckerSrt connectChecker) {
    super();
    srtClient = new SrtClient(connectChecker);
    srtClient.setOnlyAudio(true);
    streamClient = new SrtStreamClient(srtClient, null);
  }

  public SrtStreamClient getStreamClient() {
    return streamClient;
  }

  @Override
  protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
    srtClient.setAudioInfo(sampleRate, isStereo);
  }

  @Override
  protected void startStreamRtp(String url) {
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
}
