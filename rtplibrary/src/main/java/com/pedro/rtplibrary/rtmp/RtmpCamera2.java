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

package com.pedro.rtplibrary.rtmp;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import android.view.SurfaceView;
import android.view.TextureView;

import com.pedro.rtmp.flv.video.ProfileIop;
import com.pedro.rtmp.rtmp.RtmpClient;
import com.pedro.rtmp.utils.ConnectCheckerRtmp;
import com.pedro.rtplibrary.base.Camera2Base;

import com.pedro.rtplibrary.view.LightOpenGlView;
import com.pedro.rtplibrary.view.OpenGlView;

import java.nio.ByteBuffer;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.Camera2Base}
 *
 * Created by pedro on 6/07/17.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RtmpCamera2 extends Camera2Base {

  private final RtmpClient rtmpClient;

  /**
   * @deprecated This view produce rotations problems and could be unsupported in future versions.
   * Use {@link Camera2Base#Camera2Base(OpenGlView)} or {@link Camera2Base#Camera2Base(LightOpenGlView)}
   * instead.
   */
  @Deprecated
  public RtmpCamera2(SurfaceView surfaceView, ConnectCheckerRtmp connectChecker) {
    super(surfaceView);
    rtmpClient = new RtmpClient(connectChecker);
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
  }

  public RtmpCamera2(OpenGlView openGlView, ConnectCheckerRtmp connectChecker) {
    super(openGlView);
    rtmpClient = new RtmpClient(connectChecker);
  }

  public RtmpCamera2(LightOpenGlView lightOpenGlView, ConnectCheckerRtmp connectChecker) {
    super(lightOpenGlView);
    rtmpClient = new RtmpClient(connectChecker);
  }

  public RtmpCamera2(Context context, boolean useOpengl, ConnectCheckerRtmp connectChecker) {
    super(context, useOpengl);
    rtmpClient = new RtmpClient(connectChecker);
  }

  /**
   * H264 profile.
   *
   * @param profileIop Could be ProfileIop.BASELINE or ProfileIop.CONSTRAINED
   */
  public void setProfileIop(ProfileIop profileIop) {
    rtmpClient.setProfileIop(profileIop);
  }

  @Override
  public void resizeCache(int newSize) throws RuntimeException {
    rtmpClient.resizeCache(newSize);
  }

  @Override
  public int getCacheSize() {
    return rtmpClient.getCacheSize();
  }

  @Override
  public long getSentAudioFrames() {
    return rtmpClient.getSentAudioFrames();
  }

  @Override
  public long getSentVideoFrames() {
    return rtmpClient.getSentVideoFrames();
  }

  @Override
  public long getDroppedAudioFrames() {
    return rtmpClient.getDroppedAudioFrames();
  }

  @Override
  public long getDroppedVideoFrames() {
    return rtmpClient.getDroppedVideoFrames();
  }

  @Override
  public void resetSentAudioFrames() {
    rtmpClient.resetSentAudioFrames();
  }

  @Override
  public void resetSentVideoFrames() {
    rtmpClient.resetSentVideoFrames();
  }

  @Override
  public void resetDroppedAudioFrames() {
    rtmpClient.resetDroppedAudioFrames();
  }

  @Override
  public void resetDroppedVideoFrames() {
    rtmpClient.resetDroppedVideoFrames();
  }

  @Override
  public void setAuthorization(String user, String password) {
    rtmpClient.setAuthorization(user, password);
  }

  /**
   * Some Livestream hosts use Akamai auth that requires RTMP packets to be sent with increasing
   * timestamp order regardless of packet type.
   * Necessary with Servers like Dacast.
   * More info here:
   * https://learn.akamai.com/en-us/webhelp/media-services-live/media-services-live-encoder-compatibility-testing-and-qualification-guide-v4.0/GUID-F941C88B-9128-4BF4-A81B-C2E5CFD35BBF.html
   */
  public void forceAkamaiTs(boolean enabled) {
    rtmpClient.forceAkamaiTs(enabled);
  }

  /**
   * Must be called before start stream.
   *
   * Default value 128
   * Range value: 1 to 16777215.
   *
   * The most common values example: 128, 4096, 65535
   *
   * @param chunkSize packet's chunk size send to server
   */
  public void setWriteChunkSize(int chunkSize) {
    if (!isStreaming()) {
      rtmpClient.setWriteChunkSize(chunkSize);
    }
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
  public void setReTries(int reTries) {
    rtmpClient.setReTries(reTries);
  }

  @Override
  protected boolean shouldRetry(String reason) {
    return rtmpClient.shouldRetry(reason);
  }

  @Override
  public void reConnect(long delay, @Nullable String backupUrl) {
    rtmpClient.reConnect(delay, backupUrl);
  }

  @Override
  public boolean hasCongestion() {
    return rtmpClient.hasCongestion();
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

  @Override
  public void setLogs(boolean enable) {
    rtmpClient.setLogs(enable);
  }

  @Override
  public void setCheckServerAlive(boolean enable) {
    rtmpClient.setCheckServerAlive(enable);
  }
}

