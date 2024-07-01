/*
 * Copyright (C) 2024 pedroSG94.
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

package com.pedro.library.util;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.pedro.common.AudioCodec;
import com.pedro.common.BitrateManager;
import com.pedro.common.ExtensionsKt;
import com.pedro.library.base.recording.BaseRecordController;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 08/03/19.
 *
 * Class to control video recording with MediaMuxer.
 */
public class AndroidMuxerRecordController extends BaseRecordController {

  private MediaMuxer mediaMuxer;
  private MediaFormat videoFormat, audioFormat;

  @Override
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void startRecord(@NonNull String path, @Nullable Listener listener) throws IOException {
    if (audioCodec == AudioCodec.G711 || audioCodec == AudioCodec.OPUS) {
      throw new IOException("Unsupported AudioCodec: " + audioCodec.name());
    }
    mediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    this.listener = listener;
    status = Status.STARTED;
    if (listener != null) {
      bitrateManager = new BitrateManager(listener);
      listener.onStatusChange(status);
    } else {
      bitrateManager = null;
    }
    if (isOnlyAudio && audioFormat != null) init();
  }

  @Override
  @RequiresApi(api = Build.VERSION_CODES.O)
  public void startRecord(@NonNull FileDescriptor fd, @Nullable Listener listener) throws IOException {
    if (audioCodec == AudioCodec.G711 || audioCodec == AudioCodec.OPUS) {
      throw new IOException("Unsupported AudioCodec: " + audioCodec.name());
    }
    mediaMuxer = new MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    this.listener = listener;
    status = Status.STARTED;
    if (listener != null) {
      bitrateManager = new BitrateManager(listener);
      listener.onStatusChange(status);
    } else {
      bitrateManager = null;
    }
    if(isOnlyAudio && audioFormat != null) init();
  }

  @Override
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void stopRecord() {
    videoTrack = -1;
    audioTrack = -1;
    status = Status.STOPPED;
    if (mediaMuxer != null) {
      try {
        mediaMuxer.stop();
        mediaMuxer.release();
      } catch (Exception ignored) {
      }
    }
    mediaMuxer = null;
    pauseMoment = 0;
    pauseTime = 0;
    startTs = 0;
    if (listener != null) listener.onStatusChange(status);
  }

  @Override
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void recordVideo(ByteBuffer videoBuffer, MediaCodec.BufferInfo videoInfo) {
    if (status == Status.STARTED && videoFormat != null && (audioFormat != null || isOnlyVideo)) {
      if (videoInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME || isKeyFrame(videoBuffer)) {
        videoTrack = mediaMuxer.addTrack(videoFormat);
        init();
      }
    } else if (status == Status.RESUMED && (videoInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME
            || isKeyFrame(videoBuffer))) {
      status = Status.RECORDING;
      if (listener != null) listener.onStatusChange(status);
    }
    if (status == Status.RECORDING) {
      updateFormat(this.videoInfo, videoInfo);
      write(videoTrack, videoBuffer, this.videoInfo);
    }
  }

  @Override
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void recordAudio(ByteBuffer audioBuffer, MediaCodec.BufferInfo audioInfo) {
    if (status == Status.RECORDING) {
      updateFormat(this.audioInfo, audioInfo);
      write(audioTrack, audioBuffer, this.audioInfo);
    }
  }

  @Override
  public void setVideoFormat(MediaFormat videoFormat, boolean isOnlyVideo) {
    this.videoFormat = videoFormat;
    this.isOnlyVideo = isOnlyVideo;
  }

  @Override
  public void setAudioFormat(MediaFormat audioFormat, boolean isOnlyAudio) {
    this.audioFormat = audioFormat;
    this.isOnlyAudio = isOnlyAudio;
    if (isOnlyAudio && status == Status.STARTED
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      init();
    }
  }

  @Override
  public void resetFormats() {
    videoFormat = null;
    audioFormat = null;
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  private void init() {
    if (!isOnlyVideo) audioTrack = mediaMuxer.addTrack(audioFormat);
    mediaMuxer.start();
    status = Status.RECORDING;
    if (listener != null) listener.onStatusChange(status);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  private void write(int track, ByteBuffer byteBuffer, MediaCodec.BufferInfo info) {
    try {
      mediaMuxer.writeSampleData(track, byteBuffer, info);
      if (bitrateManager != null) bitrateManager.calculateBitrate(info.size * 8L, ExtensionsKt.getSuspendContext());
    } catch (Exception e) {
      if (listener != null) listener.onError(e);
    }
  }
}