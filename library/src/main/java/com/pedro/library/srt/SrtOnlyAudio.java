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

  public SrtOnlyAudio(ConnectCheckerSrt connectChecker) {
    super();
    srtClient = new SrtClient(connectChecker);
    srtClient.setOnlyAudio(true);
  }

  @Override
  public void resizeCache(int newSize) throws RuntimeException {
    srtClient.resizeCache(newSize);
  }

  @Override
  public int getCacheSize() {
    return srtClient.getCacheSize();
  }

  @Override
  public long getSentAudioFrames() {
    return srtClient.getSentAudioFrames();
  }

  @Override
  public long getSentVideoFrames() {
    return srtClient.getSentVideoFrames();
  }

  @Override
  public long getDroppedAudioFrames() {
    return srtClient.getDroppedAudioFrames();
  }

  @Override
  public long getDroppedVideoFrames() {
    return srtClient.getDroppedVideoFrames();
  }

  @Override
  public void resetSentAudioFrames() {
    srtClient.resetSentAudioFrames();
  }

  @Override
  public void resetSentVideoFrames() {
    srtClient.resetSentVideoFrames();
  }

  @Override
  public void resetDroppedAudioFrames() {
    srtClient.resetDroppedAudioFrames();
  }

  @Override
  public void resetDroppedVideoFrames() {
    srtClient.resetDroppedVideoFrames();
  }

  @Override
  public void setAuthorization(String user, String password) {
    srtClient.setAuthorization(user, password);
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
  public void setReTries(int reTries) {
    srtClient.setReTries(reTries);
  }

  @Override
  protected boolean shouldRetry(String reason) {
    return srtClient.shouldRetry(reason);
  }

  @Override
  public void reConnect(long delay, @Nullable String backupUrl) {
    srtClient.reConnect(delay, backupUrl);
  }

  @Override
  public boolean hasCongestion() {
    return srtClient.hasCongestion();
  }

  @Override
  public boolean hasCongestion(float percentUsed) {
    return srtClient.hasCongestion(percentUsed);
  }

  @Override
  protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    srtClient.sendAudio(aacBuffer, info);
  }

  @Override
  public void setLogs(boolean enable) {
    srtClient.setLogs(enable);
  }

  @Override
  public void setCheckServerAlive(boolean enable) {
    srtClient.setCheckServerAlive(enable);
  }
}
