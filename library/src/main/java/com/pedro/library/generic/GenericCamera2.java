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

package com.pedro.library.generic;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.RequiresApi;

import com.pedro.common.ConnectChecker;
import com.pedro.common.StreamClient;
import com.pedro.common.VideoCodec;
import com.pedro.library.base.Camera2Base;
import com.pedro.library.util.streamclient.StreamBaseClient;
import com.pedro.library.view.LightOpenGlView;
import com.pedro.library.view.OpenGlView;
import com.pedro.rtmp.flv.video.ProfileIop;
import com.pedro.rtmp.rtmp.RtmpClient;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.srt.srt.SrtClient;

import java.nio.ByteBuffer;

/**
 * Created by Ernovation on 9/11/21.
 * <p>
 * Experiment class.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class GenericCamera2 extends Camera2Base {

  private StreamClient client;
  private ConnectChecker connectChecker;
  private int reTries = -1; // default: not yet set
  private int audioSampleRate = -1; // default: not yet set
  private boolean isStereo = true;
  private boolean logsEnabled = false;
  private boolean checkServerAlive = false;
  private VideoCodec codec;
  private boolean isForceAkamaiTs = false;
  private ByteBuffer sps;
  private ByteBuffer pps;
  private ByteBuffer vps;

  @Deprecated
  public GenericCamera2(SurfaceView surfaceView, ConnectChecker checker) {
    super(surfaceView);
    connectChecker = checker;
  }

  @Deprecated
  public GenericCamera2(TextureView textureView, ConnectChecker checker) {
    super(textureView);
    connectChecker = checker;
  }

  public GenericCamera2(OpenGlView openGlView, ConnectChecker checker) {
    super(openGlView);
    connectChecker = checker;

  }

  public GenericCamera2(LightOpenGlView lightOpenGlView, ConnectChecker checker) {
    super(lightOpenGlView);
    connectChecker = checker;
  }

  public GenericCamera2(Context context, boolean useOpengl, ConnectChecker checker) {
    super(context, useOpengl);
    connectChecker = checker;
  }

  /**
   * H264 profile.
   *
   * @param profileIop Could be ProfileIop.BASELINE or ProfileIop.CONSTRAINED
   */
  public void setProfileIop(ProfileIop profileIop) {
    if (client instanceof RtmpClient) {
      ((RtmpClient) client).setProfileIop(profileIop);
    }
  }

  public void resizeCache(int newSize) throws RuntimeException {
    client.resizeCache(newSize);
  }

  public int getCacheSize() {
    return client.getCacheSize();
  }

  public long getSentAudioFrames() {
    return client.getSentAudioFrames();
  }

  public long getSentVideoFrames() {
    return client.getSentVideoFrames();
  }

  public long getDroppedAudioFrames() {
    return client.getDroppedAudioFrames();
  }

  public long getDroppedVideoFrames() {
    return client.getDroppedVideoFrames();
  }

  public void resetSentAudioFrames() {
    client.resetSentAudioFrames();
  }

  public void resetSentVideoFrames() {
    client.resetSentVideoFrames();
  }

  public void resetDroppedAudioFrames() {
    client.resetDroppedAudioFrames();
  }

  public void resetDroppedVideoFrames() {
    client.resetDroppedVideoFrames();
  }

  public void setAuthorization(String user, String password) {
    client.setAuthorization(user, password);
  }

  /**
   * Some Livestream hosts use Akamai auth that requires RTMP packets to be sent with increasing
   * timestamp order regardless of packet type.
   * Necessary with Servers like Dacast.
   * More info here:
   * https://learn.akamai.com/en-us/webhelp/media-services-live/media-services-live-encoder-compatibility-testing-and-qualification-guide-v4.0/GUID-F941C88B-9128-4BF4-A81B-C2E5CFD35BBF.html
   */
  public void forceAkamaiTs(boolean enabled) {
    this.isForceAkamaiTs = enabled;
    if (client != null) {
      if (client instanceof RtmpClient) {
        ((RtmpClient) client).forceAkamaiTs(enabled);
      }
    }
  }

  @Override
  protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
    this.audioSampleRate = sampleRate;
    this.isStereo = isStereo;
    if (client != null) {
      client.setAudioInfo(sampleRate, isStereo);
    }
  }

  @Override
  protected void startStreamRtp(String url) {
    // always create a new client, so we do not have to check if it is the same protocol
    client = createClient(url);
    client.setOnlyVideo(!audioInitialized);
    client.connect(url);
  }

  @Override
  protected void stopStreamRtp() {
    if (client != null) {
      client.disconnect();
    }
  }

  public void setReTries(int reTries) {
    this.reTries = reTries;
    if (client != null) {
      client.setReTries(reTries);
    }
  }

  public boolean hasCongestion() {
    if (client != null) {
      return client.hasCongestion();
    } else {
      return false;
    }
  }

  @Override
  protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    // should only be called after connect, so client should never be null
    client.sendAudio(aacBuffer.duplicate(), info);
  }

  @Override
  protected void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
    this.sps = sps;
    this.pps = pps;
    this.vps = vps;
    if (client != null) {
      client.setVideoInfo(sps, pps, vps);
    }
  }

  @Override
  protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    client.sendVideo(h264Buffer.duplicate(), info);
  }

  @Override
  public StreamBaseClient getStreamClient() {
    return null;
  }

  @Override
  protected void setVideoCodecImp(VideoCodec codec) {
    if (client != null) {
      client.setVideoCodec(codec);
    }
    this.codec = codec;
  }

  public void setLogs(boolean enable) {
    logsEnabled = enable;
    if (client != null) {
      client.setLogs(enable);
    }
  }

  public void setCheckServerAlive(boolean enable) {
    if (client != null) {
      client.setCheckServerAlive(enable);
    }
    checkServerAlive = enable;
  }

  private StreamClient createClient(String url) {
    StreamClient client;
    if (RtmpClient.getUrlPattern().matcher(url).matches()) {
      client = new RtmpClient(connectChecker);
    } else if (RtspClient.getUrlPattern().matcher(url).matches()) {
      client = new RtspClient(connectChecker);
    } else if (SrtClient.getUrlPattern().matcher(url).matches()) {
      client = new SrtClient(connectChecker);
    } else {
      client = new SrtClient(connectChecker); // this will fail later
    }
    // set all properties that have been requested earlier
    if (reTries >= 0) {
      client.setReTries(reTries);
    }
    if (audioSampleRate >= 0) {
      client.setAudioInfo(audioSampleRate, isStereo);
    }
    client.setCheckServerAlive(checkServerAlive);
    client.setLogs(logsEnabled);
    if (codec != null) {
      client.setVideoCodec(codec);
    }
    if (sps != null) {
      client.setVideoInfo(sps, pps, vps);
    }
    return client;
  }
}
