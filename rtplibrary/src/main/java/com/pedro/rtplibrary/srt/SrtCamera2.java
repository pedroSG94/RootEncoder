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

package com.pedro.rtplibrary.srt;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.pedro.encoder.utils.CodecUtil;
import com.pedro.rtplibrary.base.Camera2Base;
import com.pedro.rtplibrary.view.LightOpenGlView;
import com.pedro.rtplibrary.view.OpenGlView;
import com.pedro.srt.srt.SrtClient;
import com.pedro.srt.srt.VideoCodec;
import com.pedro.srt.utils.ConnectCheckerSrt;

import java.nio.ByteBuffer;

/**
 * More documentation see:
 * {@link Camera2Base}
 *
 * Created by pedro on 8/9/23.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class SrtCamera2 extends Camera2Base {

  private final SrtClient srtClient;

  /**
   * @deprecated This view produce rotations problems and could be unsupported in future versions.
   * Use {@link Camera2Base#Camera2Base(OpenGlView)} or {@link Camera2Base#Camera2Base(LightOpenGlView)}
   * instead.
   */
  @Deprecated
  public SrtCamera2(SurfaceView surfaceView, ConnectCheckerSrt connectChecker) {
    super(surfaceView);
    srtClient = new SrtClient(connectChecker);
  }

  /**
   * @deprecated This view produce rotations problems and could be unsupported in future versions.
   * Use {@link Camera2Base#Camera2Base(OpenGlView)} or {@link Camera2Base#Camera2Base(LightOpenGlView)}
   * instead.
   */
  @Deprecated
  public SrtCamera2(TextureView textureView, ConnectCheckerSrt connectChecker) {
    super(textureView);
    srtClient = new SrtClient(connectChecker);
  }

  public SrtCamera2(OpenGlView openGlView, ConnectCheckerSrt connectChecker) {
    super(openGlView);
    srtClient = new SrtClient(connectChecker);
  }

  public SrtCamera2(LightOpenGlView lightOpenGlView, ConnectCheckerSrt connectChecker) {
    super(lightOpenGlView);
    srtClient = new SrtClient(connectChecker);
  }

  public SrtCamera2(Context context, boolean useOpengl, ConnectCheckerSrt connectChecker) {
    super(context, useOpengl);
    srtClient = new SrtClient(connectChecker);
  }

  public void setVideoCodec(VideoCodec videoCodec) {
    recordController.setVideoMime(
            videoCodec == VideoCodec.H265 ? CodecUtil.H265_MIME : CodecUtil.H264_MIME);
    videoEncoder.setType(videoCodec == VideoCodec.H265 ? CodecUtil.H265_MIME : CodecUtil.H264_MIME);
    srtClient.setVideoCodec(videoCodec);
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
    srtClient.setOnlyVideo(!audioInitialized);
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

  @Override
  public void setLogs(boolean enable) {
    srtClient.setLogs(enable);
  }

  @Override
  public void setCheckServerAlive(boolean enable) {
    srtClient.setCheckServerAlive(enable);
  }
}

