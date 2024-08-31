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

package com.pedro.library.base;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.pedro.common.AudioCodec;
import com.pedro.encoder.EncoderErrorCallback;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAudioData;
import com.pedro.encoder.input.audio.CustomAudioEffect;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.audio.MicrophoneManagerManual;
import com.pedro.encoder.input.audio.MicrophoneMode;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.library.base.recording.BaseRecordController;
import com.pedro.library.base.recording.RecordController;
import com.pedro.library.util.AacMuxerRecordController;
import com.pedro.library.util.streamclient.StreamBaseClient;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Wrapper to stream only audio. It is under tests.
 *
 * Created by pedro on 10/07/18.
 */
public abstract class OnlyAudioBase {

  protected BaseRecordController recordController;
  private MicrophoneManager microphoneManager;
  private AudioEncoder audioEncoder;
  private boolean streaming = false;

  public OnlyAudioBase() {
    setMicrophoneMode(MicrophoneMode.ASYNC);
    recordController = new AacMuxerRecordController();
  }

  /**
   * Must be called before prepareAudio.
   *
   * @param microphoneMode mode to work accord to audioEncoder. By default ASYNC:
   * SYNC using same thread. This mode could solve choppy audio or audio frame discarded.
   * ASYNC using other thread.
   */
  public void setMicrophoneMode(MicrophoneMode microphoneMode) {
    switch (microphoneMode) {
      case SYNC:
        microphoneManager = new MicrophoneManagerManual();
        audioEncoder = new AudioEncoder(getAudioData);
        audioEncoder.setGetFrame(((MicrophoneManagerManual) microphoneManager).getGetFrame());
        audioEncoder.setTsModeBuffer(false);
        break;
      case ASYNC:
        microphoneManager = new MicrophoneManager(getMicrophoneData);
        audioEncoder = new AudioEncoder(getAudioData);
        audioEncoder.setTsModeBuffer(false);
        break;
      case BUFFER:
        microphoneManager = new MicrophoneManager(getMicrophoneData);
        audioEncoder = new AudioEncoder(getAudioData);
        audioEncoder.setTsModeBuffer(true);
        break;
    }
  }

  /**
   * Set a callback to know errors related with Video/Audio encoders
   * @param encoderErrorCallback callback to use, null to remove
   */
  public void setEncoderErrorCallback(EncoderErrorCallback encoderErrorCallback) {
    audioEncoder.setEncoderErrorCallback(encoderErrorCallback);
  }

  /**
   * Set an audio effect modifying microphone's PCM buffer.
   */
  public void setCustomAudioEffect(CustomAudioEffect customAudioEffect) {
    microphoneManager.setCustomAudioEffect(customAudioEffect);
  }

  /**
   * @param codecTypeAudio force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
   */
  public void forceCodecType(CodecUtil.CodecType codecTypeAudio) {
    audioEncoder.forceCodecType(codecTypeAudio);
  }

  protected abstract void onAudioInfoImp(boolean isStereo, int sampleRate);

  /**
   * Call this method before use @startStream. If not you will do a stream without audio.
   *
   * @param bitrate AAC in kb.
   * @param sampleRate of audio in hz. Can be 8000, 16000, 22500, 32000, 44100.
   * @param isStereo true if you want Stereo audio (2 audio channels), false if you want Mono audio
   * (1 audio channel).
   * @param echoCanceler true enable echo canceler, false disable.
   * @param noiseSuppressor true enable noise suppressor, false  disable.
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a AAC encoder).
   */
  public boolean prepareAudio(int audioSource, int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) {
    if (!microphoneManager.createMicrophone(audioSource, sampleRate, isStereo, echoCanceler, noiseSuppressor)) {
      return false;
    }
    onAudioInfoImp(isStereo, sampleRate);
    return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo,
        microphoneManager.getMaxInputSize());
  }

  public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) {
    return prepareAudio(MediaRecorder.AudioSource.DEFAULT, bitrate, sampleRate, isStereo, echoCanceler,
        noiseSuppressor);
  }

  public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo) {
    return prepareAudio(bitrate, sampleRate, isStereo, false, false);
  }

  /**
   * Same to call:
   * prepareAudio(64 * 1024, 32000, true, false, false);
   *
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a AAC encoder).
   */
  public boolean prepareAudio() {
    return prepareAudio(64 * 1024, 32000, true, false, false);
  }

  /**
   * Starts recording an AAC audio.
   *
   * @param path Where file will be saved.
   * @throws IOException If initialized before a stream.
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void startRecord(String path, RecordController.Listener listener) throws IOException {
    recordController.startRecord(path, listener);
    if (!streaming) {
      startEncoders();
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void startRecord(final String path) throws IOException {
    startRecord(path, null);
  }

  /**
   * Starts recording an AAC audio.
   *
   * @param fd Where the file will be saved.
   * @throws IOException If initialized before a stream.
   */
  @RequiresApi(api = Build.VERSION_CODES.O)
  public void startRecord(@NonNull final FileDescriptor fd,
      @Nullable RecordController.Listener listener) throws IOException {
    recordController.startRecord(fd, listener);
    if (!streaming) {
      startEncoders();
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  public void startRecord(@NonNull final FileDescriptor fd) throws IOException {
    startRecord(fd, null);
  }

  /**
   * Stop record AAC audio started with @startRecord. If you don't call it file will be unreadable.
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void stopRecord() {
    recordController.stopRecord();
    if (!streaming) stopStream();
  }

  protected abstract void startStreamImp(String url);

  /**
   * Need be called after @prepareVideo or/and @prepareAudio.
   *
   * @param url of the stream like:
   * protocol://ip:port/application/streamName
   *
   * RTSP: rtsp://192.168.1.1:1935/live/pedroSG94
   * RTSPS: rtsps://192.168.1.1:1935/live/pedroSG94
   * RTMP: rtmp://192.168.1.1:1935/live/pedroSG94
   * RTMPS: rtmps://192.168.1.1:1935/live/pedroSG94
   */
  public void startStream(String url) {
    streaming = true;
    if (!recordController.isRunning()) {
      startEncoders();
    }
    startStreamImp(url);
  }

  /**
   * Stop stream started with @startStream.
   */
  public void stopStream() {
    if (streaming) {
      streaming = false;
      stopStreamImp();
    }
    if (!recordController.isRecording()) {
      microphoneManager.stop();
      audioEncoder.stop();
      recordController.resetFormats();
    }
  }

  private void startEncoders() {
    long startTs = System.nanoTime() / 1000;
    audioEncoder.start(startTs);
    microphoneManager.start();
  }

  protected abstract void stopStreamImp();

  /**
   * Get record state.
   *
   * @return true if recording, false if not recoding.
   */
  public boolean isRecording() {
    return recordController.isRunning();
  }

  public void pauseRecord() {
    recordController.pauseRecord();
  }

  public void resumeRecord() {
    recordController.resumeRecord();
  }

  public RecordController.Status getRecordStatus() {
    return recordController.getStatus();
  }

  /**
   * Set a custom size of audio buffer input.
   * If you set 0 or less you can disable it to use library default value.
   * Must be called before of prepareAudio method.
   *
   * @param size in bytes. Recommended multiple of 1024 (2048, 4096, 8196, etc)
   */
  public void setAudioMaxInputSize(int size) {
    microphoneManager.setMaxInputSize(size);
  }

  /**
   * Mute microphone, can be called before, while and after stream.
   */
  public void disableAudio() {
    microphoneManager.mute();
  }

  /**
   * Enable a muted microphone, can be called before, while and after stream.
   */
  public void enableAudio() {
    microphoneManager.unMute();
  }

  /**
   * Get mute state of microphone.
   *
   * @return true if muted, false if enabled
   */
  public boolean isAudioMuted() {
    return microphoneManager.isMuted();
  }

  /**
   * Get stream state.
   *
   * @return true if streaming, false if not streaming.
   */
  public boolean isStreaming() {
    return streaming;
  }

  public boolean resetAudioEncoder() {
    return audioEncoder.reset();
  }

  protected abstract void getAudioDataImp(ByteBuffer audioBuffer, MediaCodec.BufferInfo info);

  public void setRecordController(BaseRecordController recordController) {
    if (!isRecording()) this.recordController = recordController;
  }

  private final GetMicrophoneData getMicrophoneData = frame -> {
    audioEncoder.inputPCMData(frame);
  };

  private final GetAudioData getAudioData = new GetAudioData() {
    @Override
    public void getAudioData(@NonNull ByteBuffer audioBuffer, @NonNull MediaCodec.BufferInfo info) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        recordController.recordAudio(audioBuffer, info);
      }
      if (streaming) getAudioDataImp(audioBuffer, info);
    }

    @Override
    public void onAudioFormat(@NonNull MediaFormat mediaFormat) {
      recordController.setAudioFormat(mediaFormat, true);
    }
  };

  public abstract StreamBaseClient getStreamClient();

  public void setAudioCodec(AudioCodec codec) {
    setAudioCodecImp(codec);
    recordController.setAudioCodec(codec);
    String type = switch (codec) {
      case G711 -> CodecUtil.G711_MIME;
      case AAC -> CodecUtil.AAC_MIME;
      case OPUS -> CodecUtil.OPUS_MIME;
    };
    audioEncoder.setType(type);
  }

  protected abstract void setAudioCodecImp(AudioCodec codec);
}
