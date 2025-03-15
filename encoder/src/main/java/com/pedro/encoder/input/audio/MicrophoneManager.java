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

package com.pedro.encoder.input.audio;

import android.annotation.SuppressLint;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.pedro.common.TimeUtils;
import com.pedro.encoder.Frame;
import com.pedro.encoder.audio.AudioEncoder;

/**
 * Created by pedro on 19/01/17.
 */

@SuppressLint("MissingPermission")
public class MicrophoneManager {

  private final String TAG = "MicrophoneManager";
  protected AudioRecord audioRecord;
  protected AudioRecord audioRecordDevice;
  private final GetMicrophoneData getMicrophoneData;
  protected byte[] pcmBuffer = new byte[AudioEncoder.inputSize];
  protected byte[] pcmBufferDevice = new byte[AudioEncoder.inputSize];
  protected byte[] pcmBufferMix = new byte[AudioEncoder.inputSize];
  protected byte[] pcmBufferMuted = new byte[AudioEncoder.inputSize];
  protected boolean running = false;
  private boolean created = false;
  //default parameters for microphone
  private int sampleRate = 32000; //hz
  private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
  private int channel = AudioFormat.CHANNEL_IN_STEREO;
  protected boolean muted = false;
  private AudioPostProcessEffect audioPostProcessEffect;
  protected HandlerThread handlerThread;
  protected CustomAudioEffect customAudioEffect = new NoAudioEffect();
  private Mode mode = Mode.MICROPHONE;
  private float microphoneVolume = 1f;
  private float internalVolume = 1f;
  private final AudioUtils audioUtils = new AudioUtils();

  enum Mode {
    MICROPHONE, INTERNAL, MIX
  }

  public MicrophoneManager(GetMicrophoneData getMicrophoneData) {
    this.getMicrophoneData = getMicrophoneData;
  }

  public void setCustomAudioEffect(CustomAudioEffect customAudioEffect) {
    this.customAudioEffect = customAudioEffect;
  }

  /**
   * Create audio record
   */
  public void createMicrophone() {
    createMicrophone(sampleRate, true, false, false);
    Log.i(TAG, "Microphone created, " + sampleRate + "hz, Stereo");
  }

  /**
   * Create audio record with params and default audio source
   */
  public boolean createMicrophone(int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) {
    return createMicrophone(MediaRecorder.AudioSource.DEFAULT, sampleRate, isStereo, echoCanceler,
        noiseSuppressor);
  }

  /**
   * Create audio record with params and selected audio source
   *
   * @param audioSource - the recording source. See {@link MediaRecorder.AudioSource} for the
   * recording source definitions.
   */
  public boolean createMicrophone(int audioSource, int sampleRate, boolean isStereo,
      boolean echoCanceler, boolean noiseSuppressor) {
    try {
      this.sampleRate = sampleRate;
      channel = isStereo ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
      getPcmBufferSize(sampleRate, channel);
      audioRecord = new AudioRecord(audioSource, sampleRate, channel, audioFormat, AudioEncoder.inputSize * 5);
      audioPostProcessEffect = new AudioPostProcessEffect(audioRecord.getAudioSessionId());
      if (echoCanceler) audioPostProcessEffect.enableEchoCanceler();
      if (noiseSuppressor) audioPostProcessEffect.enableNoiseSuppressor();
      String chl = (isStereo) ? "Stereo" : "Mono";
      if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
        throw new IllegalArgumentException("Some parameters specified are not valid");
      }
      Log.i(TAG, "Microphone created, " + sampleRate + "hz, " + chl);
      mode = Mode.MICROPHONE;
      created = true;
    } catch (IllegalArgumentException e) {
      Log.e(TAG, "create microphone error", e);
    }
    return created;
  }

  /**
   * Create audio record with params and AudioPlaybackCaptureConfig used for capturing internal
   * audio
   * Notice that you should granted {@link android.Manifest.permission#RECORD_AUDIO} before calling
   * this!
   *
   * @param config - AudioPlaybackCaptureConfiguration received from {@link
   * android.media.projection.MediaProjection}
   * @see AudioPlaybackCaptureConfiguration.Builder#Builder(MediaProjection)
   * @see "https://developer.android.com/guide/topics/media/playback-capture"
   * @see "https://medium.com/@debuggingisfun/android-10-audio-capture-77dd8e9070f9"
   */
  public boolean createInternalMicrophone(AudioPlaybackCaptureConfiguration config, int sampleRate,
      boolean isStereo, boolean echoCanceler, boolean noiseSuppressor) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        this.sampleRate = sampleRate;
        channel = isStereo ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        getPcmBufferSize(sampleRate, channel);
        audioRecordDevice = new AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(new AudioFormat.Builder().setEncoding(audioFormat)
                  .setSampleRate(sampleRate)
                  .setChannelMask(channel)
                  .build())
                .setBufferSizeInBytes(AudioEncoder.inputSize * 5)
                .build();
        audioPostProcessEffect = new AudioPostProcessEffect(audioRecordDevice.getAudioSessionId());
        if (echoCanceler) audioPostProcessEffect.enableEchoCanceler();
        if (noiseSuppressor) audioPostProcessEffect.enableNoiseSuppressor();
        String chl = (isStereo) ? "Stereo" : "Mono";
        if (audioRecordDevice.getState() != AudioRecord.STATE_INITIALIZED) {
          throw new IllegalArgumentException("Some parameters specified are not valid");
        }
        Log.i(TAG, "Internal microphone created, " + sampleRate + "hz, " + chl);
        mode = Mode.INTERNAL;
        created = true;
      } else {
        return createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor);
      }
    } catch (IllegalArgumentException e) {
      Log.e(TAG, "create microphone error", e);
    }
    return created;
  }

  public boolean createMixMicrophone(
          int audioSource, AudioPlaybackCaptureConfiguration config, int sampleRate,
          boolean isStereo, boolean echoCanceler, boolean noiseSuppressor
  ) {
    boolean micResult = createMicrophone(audioSource, sampleRate, isStereo, echoCanceler, noiseSuppressor);
    if (!micResult) return false;
    boolean internalResult = createInternalMicrophone(config, sampleRate, isStereo, echoCanceler, noiseSuppressor);
    mode = Mode.MIX;
    return internalResult;
  }

  public boolean createInternalMicrophone(AudioPlaybackCaptureConfiguration config, int sampleRate,
      boolean isStereo) {
    return createInternalMicrophone(config, sampleRate, isStereo, false, false);
  }

  /**
   * Start record and get data
   */
  public synchronized void start() {
    init();
    handlerThread = new HandlerThread(TAG);
    handlerThread.start();
    Handler handler = new Handler(handlerThread.getLooper());
    handler.post(() -> {
      while (running) {
        Frame frame = read();
        if (frame != null) {
          getMicrophoneData.inputPCMData(frame);
        }
      }
    });
  }

  private void init() {
    switch (mode) {
        case MICROPHONE -> {
          if (audioRecord != null) {
            audioRecord.startRecording();
          } else {
            throw new IllegalStateException("Error starting, microphone was stopped or not created, use createMicrophone() before start()");
          }
        }
        case INTERNAL -> {
          if (audioRecordDevice != null) {
            audioRecordDevice.startRecording();
          } else {
            throw new IllegalStateException("Error starting, microphone was stopped or not created, use createMicrophone() before start()");
          }
        }
        case MIX -> {
          if (audioRecord != null && audioRecordDevice != null) {
            audioRecord.startRecording();
            audioRecordDevice.startRecording();
          } else {
            throw new IllegalStateException("Error starting, microphone was stopped or not created, use createMicrophone() before start()");
          }
        }
    }
    running = true;
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  public boolean setPreferredDevice(AudioDeviceInfo deviceInfo){
    if(audioRecord == null) {
      Log.w(TAG, "audioRecord not created");
      return false;
    }
    return audioRecord.setPreferredDevice(deviceInfo);
  }

  public void mute() {
    muted = true;
  }

  public void unMute() {
    muted = false;
  }

  public boolean isMuted() {
    return muted;
  }

  /**
   * @return Object with size and PCM buffer data
   */
  protected Frame read() {
    long timeStamp = TimeUtils.getCurrentTimeMicro();
    switch (mode) {
        case MICROPHONE -> {
          int size = audioRecord.read(pcmBuffer, 0, pcmBuffer.length);
          if (size < 0) {
            Log.e(TAG, "read error: " + size);
            return null;
          }
          audioUtils.applyVolume(pcmBuffer, microphoneVolume);
          return new Frame(muted ? pcmBufferMuted : customAudioEffect.process(pcmBuffer), 0, size, timeStamp);
        }
        case INTERNAL -> {
          int size = audioRecordDevice.read(pcmBufferDevice, 0, pcmBufferDevice.length);
          if (size < 0) {
            Log.e(TAG, "read error: " + size);
            return null;
          }
          audioUtils.applyVolume(pcmBufferDevice, internalVolume);
          return new Frame(muted ? pcmBufferMuted : customAudioEffect.process(pcmBufferDevice), 0, size, timeStamp);
        }
        case MIX -> {
          int size = audioRecord.read(pcmBuffer, 0, pcmBuffer.length);
          if (size < 0) {
            Log.e(TAG, "read error: " + size);
            return null;
          }
          int sizeInternal = audioRecordDevice.read(pcmBufferDevice, 0, pcmBufferDevice.length);
          if (sizeInternal < 0) {
            Log.e(TAG, "read error: " + sizeInternal);
            return null;
          }
          audioUtils.applyVolumeAndMix(pcmBuffer, microphoneVolume, pcmBufferDevice, internalVolume, pcmBufferMix);
          return new Frame(muted ? pcmBufferMuted : customAudioEffect.process(pcmBufferMix), 0, size, timeStamp);
        }
        default -> { return null; }
    }
  }

  /**
   * Stop and release microphone
   */
  public synchronized void stop() {
    running = false;
    created = false;
    if (handlerThread != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        handlerThread.quitSafely();
      } else {
        handlerThread.quit();
      }
    }
    if (audioRecord != null) {
      audioRecord.setRecordPositionUpdateListener(null);
      audioRecord.stop();
      audioRecord.release();
      audioRecord = null;
    }
    if (audioRecordDevice != null) {
      audioRecordDevice.setRecordPositionUpdateListener(null);
      audioRecordDevice.stop();
      audioRecordDevice.release();
      audioRecordDevice = null;
    }
    if (audioPostProcessEffect != null) {
      audioPostProcessEffect.release();
    }
    Log.i(TAG, "Microphone stopped");
  }

  /**
   * Get PCM buffer size
   */
  private void getPcmBufferSize(int sampleRate, int channel) {
    int minSize = AudioRecord.getMinBufferSize(sampleRate, channel, audioFormat);
    int bufferSize = Math.max(minSize, AudioEncoder.inputSize);
    pcmBuffer = new byte[bufferSize];
    pcmBufferDevice = new byte[bufferSize];
    pcmBufferMix = new byte[bufferSize];
    pcmBufferMuted = new byte[bufferSize];
  }

  public int getSampleRate() {
    return sampleRate;
  }

  public void setSampleRate(int sampleRate) {
    this.sampleRate = sampleRate;
  }

  public int getAudioFormat() {
    return audioFormat;
  }

  public int getChannel() {
    return channel;
  }

  public boolean isRunning() {
    return running;
  }

  public boolean isCreated() {
    return created;
  }

  public void setVolume(float audioUtils) {
    setMicrophoneVolume(audioUtils);
    setInternalVolume(audioUtils);
  }

  public void setMicrophoneVolume(float microphoneVolume) {
    this.microphoneVolume = microphoneVolume;
  }

  public void setInternalVolume(float internalVolume) {
    this.internalVolume = internalVolume;
  }

  public float getMicrophoneVolume() {
    return microphoneVolume;
  }

  public float getInternalVolume() {
    return internalVolume;
  }
}
