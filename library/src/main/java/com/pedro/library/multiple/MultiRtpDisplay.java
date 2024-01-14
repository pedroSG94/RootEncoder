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

package com.pedro.library.multiple;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.pedro.common.AudioCodec;
import com.pedro.common.ConnectChecker;
import com.pedro.common.VideoCodec;
import com.pedro.library.base.DisplayBase;
import com.pedro.library.util.streamclient.StreamBaseClient;
import com.pedro.rtmp.rtmp.RtmpClient;
import com.pedro.rtsp.rtsp.RtspClient;

import java.nio.ByteBuffer;

/**
 * Created by pedro, HirogaKatageri on 2021-05-24.
 *
 * Experimental Class.
 *
 * It supports multiple streams of rtmp and rtsp at same time.
 * You must set the same number of ConnectChecker that you want use.
 *
 * For example. 2 RTMP and 1 RTSP:
 * stream1, stream2, stream3 (stream1 and stream2 are ConnectCheckerRtmp. stream3 is ConnectCheckerRtsp)
 *
 * MultiRtpDisplay multiRtpDisplay = new MultiRtpDisplay(context, true, new ConnectCheckerRtmp[]{ stream1, stream2 },
 * new ConnectCheckerRtsp[]{ stream3 });
 *
 * You can set an empty array or null if you don't want to use a specific protocol.
 * new MultiRtpDisplay(context, true, new ConnectCheckerRtmp[]{ stream1, stream2 },
 * null); // RTSP protocol is not used
 *
 * In order to use start, stop and other calls you must send type of stream and index to execute it.
 * Example (using previous example interfaces):
 *
 * multiRtpDisplay.startStream(RtpType.RTMP, 1, url); //stream2 is started
 * multiRtpDisplay.stopStream(RtpType.RTSP, 0); //stream3 is stopped
 * multiRtpDisplay.retry(RtpType.RTMP, 0, delay, reason, backupUrl) //retry stream1
 *
 * NOTE:
 * If you call these methods nothing is executed:
 *
 * multiRtpDisplay.startStream(endpoint);
 * multiRtpDisplay.stopStream();
 * multiRtpDisplay.retry(delay, reason, backUpUrl);
 *
 * The rest of methods without RtpType and index means that you will execute that command in all streams.
 * Read class code if you need info about any method.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MultiRtpDisplay extends DisplayBase {

  private final RtmpClient[] rtmpClients;
  private final RtspClient[] rtspClients;

  public MultiRtpDisplay(Context context, boolean useOpenGL, ConnectChecker[] connectCheckerRtmpList,
      ConnectChecker[] connectCheckerRtspList) {
    super(context, useOpenGL);
    int rtmpSize = connectCheckerRtmpList != null ? connectCheckerRtmpList.length : 0;
    rtmpClients = new RtmpClient[rtmpSize];
    for (int i = 0; i < rtmpClients.length; i++) {
      rtmpClients[i] = new RtmpClient(connectCheckerRtmpList[i]);
    }
    int rtspSize = connectCheckerRtspList != null ? connectCheckerRtspList.length : 0;
    rtspClients = new RtspClient[rtspSize];
    for (int i = 0; i < rtspClients.length; i++) {
      rtspClients[i] = new RtspClient(connectCheckerRtspList[i]);
    }
  }

  public boolean isStreaming(RtpType rtpType, int index) {
    if (rtpType == RtpType.RTMP) {
      return rtmpClients[index].isStreaming();
    } else {
      return rtspClients[index].isStreaming();
    }
  }

  /**
   * Some Livestream hosts use Akamai auth that requires RTMP packets to be sent with increasing
   * timestamp order regardless of packet type.
   * Necessary with Servers like Dacast.
   * More info here:
   * https://learn.akamai.com/en-us/webhelp/media-services-live/media-services-live-encoder-compatibility-testing-and-qualification-guide-v4.0/GUID-F941C88B-9128-4BF4-A81B-C2E5CFD35BBF.html
   */
  public void forceIncrementalTs(boolean enabled) {
    for (RtmpClient rtmpClient : rtmpClients) {
      rtmpClient.forceIncrementalTs(enabled);
    }
  }

  public void setAuthorization(RtpType rtpType, int index, String user, String password) {
    if (rtpType == RtpType.RTMP) {
      rtmpClients[index].setAuthorization(user, password);
    } else {
      rtspClients[index].setAuthorization(user, password);
    }
  }

  public void setAuthorization(String user, String password) {
    for (RtmpClient rtmpClient : rtmpClients) {
      rtmpClient.setAuthorization(user, password);
    }
    for (RtspClient rtspClient : rtspClients) {
      rtspClient.setAuthorization(user, password);
    }
  }

  @Override
  protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
    for (RtmpClient rtmpClient : rtmpClients) {
      rtmpClient.setAudioInfo(sampleRate, isStereo);
    }
    for (RtspClient rtspClient : rtspClients) {
      rtspClient.setAudioInfo(sampleRate, isStereo);
    }
  }

  public void startStream(RtpType rtpType, int index, String url) {
    boolean shouldStarEncoder = true;
    for (RtmpClient rtmpClient : rtmpClients) {
      if (rtmpClient.isStreaming()) {
        shouldStarEncoder = false;
        break;
      }
    }
    if (shouldStarEncoder) {
      for (RtspClient rtspClient : rtspClients) {
        if (rtspClient.isStreaming()) {
          shouldStarEncoder = false;
          break;
        }
      }
    }
    if (shouldStarEncoder) super.startStream("");
    if (rtpType == RtpType.RTMP) {
      if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
        rtmpClients[index].setVideoResolution(videoEncoder.getHeight(), videoEncoder.getWidth());
      } else {
        rtmpClients[index].setVideoResolution(videoEncoder.getWidth(), videoEncoder.getHeight());
      }
      rtmpClients[index].setFps(videoEncoder.getFps());
      rtmpClients[index].connect(url);
    } else {
      rtspClients[index].connect(url);
    }
  }

  public void stopStream(RtpType rtpType, int index) {
    boolean shouldStopEncoder = true;
    if (rtpType == RtpType.RTMP) {
      rtmpClients[index].disconnect();
    } else {
      rtspClients[index].disconnect();
    }
    for (RtmpClient rtmpClient : rtmpClients) {
      if (rtmpClient.isStreaming()) {
        shouldStopEncoder = false;
        break;
      }
    }
    if (shouldStopEncoder) {
      for (RtspClient rtspClient : rtspClients) {
        if (rtspClient.isStreaming()) {
          shouldStopEncoder = false;
          break;
        }
      }
    }
    if (shouldStopEncoder) super.stopStream();
  }

  @Override
  protected void startStreamRtp(String url) {
  }

  @Override
  protected void stopStreamRtp() {
  }

  public boolean reTry(RtpType rtpType, int index, long delay, String reason, @Nullable String backupUrl) {
    boolean result;
    if (rtpType == RtpType.RTMP) {
      result = rtmpClients[index].shouldRetry(reason);
      if (result) {
        requestKeyFrame();
        rtmpClients[index].reConnect(delay, backupUrl);
      }
    } else {
      result = rtspClients[index].shouldRetry(reason);
      if (result) {
        requestKeyFrame();
        rtmpClients[index].reConnect(delay, backupUrl);
      }
    }
    return result;
  }

  public void setReTries(int reTries) {
    for (RtmpClient rtmpClient : rtmpClients) {
      rtmpClient.setReTries(reTries);
    }
    for (RtspClient rtspClient : rtspClients) {
      rtspClient.setReTries(reTries);
    }
  }


  public boolean hasCongestion(RtpType rtpType, int index) {
    if (rtpType == RtpType.RTMP) {
      return rtmpClients[index].hasCongestion();
    } else {
      return rtspClients[index].hasCongestion();
    }
  }

  public void resizeCache(RtpType rtpType, int index, int newSize) {
    if (rtpType == RtpType.RTMP) {
      rtmpClients[index].resizeCache(newSize);
    } else {
      rtspClients[index].resizeCache(newSize);
    }
  }

  public int getCacheSize(RtpType rtpType, int index) {
    if (rtpType == RtpType.RTMP) {
      return rtmpClients[index].getCacheSize();
    } else {
      return rtspClients[index].getCacheSize();
    }
  }

  public long getSentAudioFrames() {
    long number = 0;
    for (RtmpClient rtmpClient : rtmpClients) {
      number += rtmpClient.getSentAudioFrames();
    }
    for (RtspClient rtspClient : rtspClients) {
      number += rtspClient.getSentAudioFrames();
    }
    return number;
  }

  public long getSentVideoFrames() {
    long number = 0;
    for (RtmpClient rtmpClient : rtmpClients) {
      number += rtmpClient.getSentVideoFrames();
    }
    for (RtspClient rtspClient : rtspClients) {
      number += rtspClient.getSentVideoFrames();
    }
    return number;
  }

  public long getDroppedAudioFrames() {
    long number = 0;
    for (RtmpClient rtmpClient : rtmpClients) {
      number += rtmpClient.getDroppedAudioFrames();
    }
    for (RtspClient rtspClient : rtspClients) {
      number += rtspClient.getDroppedAudioFrames();
    }
    return number;
  }

  public long getDroppedVideoFrames() {
    long number = 0;
    for (RtmpClient rtmpClient : rtmpClients) {
      number += rtmpClient.getDroppedVideoFrames();
    }
    for (RtspClient rtspClient : rtspClients) {
      number += rtspClient.getDroppedVideoFrames();
    }
    return number;
  }

  public void resetSentAudioFrames() {
    for (RtmpClient rtmpClient : rtmpClients) {
      rtmpClient.resetSentAudioFrames();
    }
    for (RtspClient rtspClient : rtspClients) {
      rtspClient.resetSentAudioFrames();
    }
  }

  public void resetSentVideoFrames() {
    for (RtmpClient rtmpClient : rtmpClients) {
      rtmpClient.resetSentVideoFrames();
    }
    for (RtspClient rtspClient : rtspClients) {
      rtspClient.resetSentVideoFrames();
    }
  }

  public void resetDroppedAudioFrames() {
    for (RtmpClient rtmpClient : rtmpClients) {
      rtmpClient.resetDroppedAudioFrames();
    }
    for (RtspClient rtspClient : rtspClients) {
      rtspClient.resetDroppedAudioFrames();
    }
  }

  public void resetDroppedVideoFrames() {
    for (RtmpClient rtmpClient : rtmpClients) {
      rtmpClient.resetDroppedVideoFrames();
    }
    for (RtspClient rtspClient : rtspClients) {
      rtspClient.resetDroppedVideoFrames();
    }
  }

  @Override
  protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    for (RtmpClient rtmpClient : rtmpClients) {
      rtmpClient.sendAudio(aacBuffer.duplicate(), info);
    }
    for (RtspClient rtspClient : rtspClients) {
      rtspClient.sendAudio(aacBuffer.duplicate(), info);
    }
  }

  @Override
  protected void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
    for (RtmpClient rtmpClient : rtmpClients) {
      rtmpClient.setVideoInfo(sps.duplicate(), pps.duplicate(), vps != null ? vps.duplicate() : null);
    }
    for (RtspClient rtspClient : rtspClients) {
      rtspClient.setVideoInfo(sps.duplicate(), pps.duplicate(), vps != null ? vps.duplicate() : null);
    }
  }

  @Override
  protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    for (RtmpClient rtmpClient : rtmpClients) {
      rtmpClient.sendVideo(h264Buffer.duplicate(), info);
    }
    for (RtspClient rtspClient : rtspClients) {
      rtspClient.sendVideo(h264Buffer.duplicate(), info);
    }
  }

  @Override
  public StreamBaseClient getStreamClient() {
    return null;
  }

  @Override
  protected void setVideoCodecImp(VideoCodec codec) {
    for (RtmpClient rtmpClient: rtmpClients) {
        rtmpClient.setVideoCodec(codec);
    }
    for (RtspClient rtspClient: rtspClients) {
        rtspClient.setVideoCodec(codec);
    }
  }

  @Override
  protected void setAudioCodecImp(AudioCodec codec) {
    for (RtspClient rtspClient: rtspClients) {
      rtspClient.setAudioCodec(codec);
    }
  }

  public void setLogs(boolean enable) {
    for (RtmpClient rtmpClient : rtmpClients) {
      rtmpClient.setLogs(enable);
    }
    for (RtspClient rtspClient : rtspClients) {
      rtspClient.setLogs(enable);
    }
  }

  public void setCheckServerAlive(boolean enable) {
    for (RtmpClient rtmpClient: rtmpClients) {
      rtmpClient.setCheckServerAlive(enable);
    }
    for (RtspClient rtspClient: rtspClients) {
      rtspClient.setCheckServerAlive(enable);
    }
  }
}
