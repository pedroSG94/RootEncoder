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
 * Class to control audio recording with MediaMuxer.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class AndroidMuxerWebmRecordController extends BaseRecordController {

  private MediaMuxer mediaMuxer;
  private MediaFormat audioFormat;
  private final int outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM;

  @Override
  public void startRecord(@NonNull String path, @Nullable Listener listener) throws IOException {
    if (audioCodec != AudioCodec.OPUS) {
      throw new IOException("Unsupported AudioCodec: " + audioCodec.name());
    }
    mediaMuxer = new MediaMuxer(path, outputFormat);
    this.listener = listener;
    status = Status.STARTED;
    if (listener != null) {
      bitrateManager = new BitrateManager(listener);
      listener.onStatusChange(status);
    } else {
      bitrateManager = null;
    }
    if (audioFormat != null) init();
  }

  @Override
  @RequiresApi(api = Build.VERSION_CODES.O)
  public void startRecord(@NonNull FileDescriptor fd, @Nullable Listener listener) throws IOException {
    if (audioCodec != AudioCodec.OPUS) {
      throw new IOException("Unsupported AudioCodec: " + audioCodec.name());
    }
    mediaMuxer = new MediaMuxer(fd, outputFormat);
    this.listener = listener;
    status = Status.STARTED;
    if (listener != null) {
      bitrateManager = new BitrateManager(listener);
      listener.onStatusChange(status);
    } else {
      bitrateManager = null;
    }
    if(audioFormat != null) init();
  }

  @Override
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
  public void recordVideo(ByteBuffer videoBuffer, MediaCodec.BufferInfo videoInfo) {
  }

  @Override
  public void recordAudio(ByteBuffer audioBuffer, MediaCodec.BufferInfo audioInfo) {
    if (status == Status.RECORDING) {
      updateFormat(this.audioInfo, audioInfo);
      write(audioTrack, audioBuffer, this.audioInfo);
    }
  }

  @Override
  public void setVideoFormat(MediaFormat videoFormat, boolean isOnlyVideo) {
  }

  @Override
  public void setAudioFormat(MediaFormat audioFormat, boolean isOnlyAudio) {
    this.audioFormat = audioFormat;
    if (status == Status.STARTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      init();
    }
  }

  @Override
  public void resetFormats() {
    audioFormat = null;
  }

  private void init() {
    audioTrack = mediaMuxer.addTrack(audioFormat);
    mediaMuxer.start();
    status = Status.RECORDING;
    if (listener != null) listener.onStatusChange(status);
  }

  private void write(int track, ByteBuffer byteBuffer, MediaCodec.BufferInfo info) {
    if (track == -1) return;
    try {
      mediaMuxer.writeSampleData(track, byteBuffer, info);
      if (bitrateManager != null) bitrateManager.calculateBitrate(info.size * 8L, ExtensionsKt.getSuspendContext());
    } catch (Exception e) {
      if (listener != null) listener.onError(e);
    }
  }
}