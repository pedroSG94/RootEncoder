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

package com.pedro.encoder.audio;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import androidx.annotation.NonNull;

import com.pedro.encoder.BaseEncoder;
import com.pedro.encoder.Frame;
import com.pedro.encoder.GetFrame;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.utils.CodecUtil;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Created by pedro on 19/01/17.
 *
 * Encode PCM audio data to ACC and return in a callback
 */

public class AudioEncoder extends BaseEncoder implements GetMicrophoneData {

  private final GetAudioData getAudioData;
  private int bitRate = 64 * 1024;  //in kbps
  private int sampleRate = 32000; //in hz
  private int maxInputSize = 0;
  private boolean isStereo = true;
  private GetFrame getFrame;
  private long bytesRead = 0;
  private boolean tsModeBuffer = false;

  public AudioEncoder(GetAudioData getAudioData) {
    this.getAudioData = getAudioData;
    typeError = CodecUtil.CodecTypeError.AUDIO_CODEC;
    type = CodecUtil.AAC_MIME;
    TAG = "AudioEncoder";
  }

  /**
   * Prepare encoder with custom parameters
   */
  public boolean prepareAudioEncoder(int bitRate, int sampleRate, boolean isStereo,
      int maxInputSize) {
    if (prepared) stop();

    this.bitRate = bitRate;
    this.sampleRate = sampleRate;
    this.maxInputSize = maxInputSize;
    this.isStereo = isStereo;
    isBufferMode = true;

    try {
      if (type.equals(CodecUtil.G711_MIME)) {
        g711Codec.configure(sampleRate, isStereo ? 2 : 1);
        setCallback();
        running = false;
        Log.i(TAG, "prepared");
        prepared = true;
        return true;
      }
      MediaCodecInfo encoder = chooseEncoder(type);
      if (encoder != null) {
        Log.i(TAG, "Encoder selected " + encoder.getName());
        codec = MediaCodec.createByCodecName(encoder.getName());
      } else {
        Log.e(TAG, "Valid encoder not found");
        return false;
      }

      int channelCount = (isStereo) ? 2 : 1;
      MediaFormat audioFormat = MediaFormat.createAudioFormat(type, sampleRate, channelCount);
      audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
      audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
      audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
          MediaCodecInfo.CodecProfileLevel.AACObjectLC);
      setCallback();
      codec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      running = false;
      Log.i(TAG, "prepared");
      prepared = true;
      return true;
    } catch (Exception e) {
      Log.e(TAG, "Create AudioEncoder failed.", e);
      this.stop();
      return false;
    }
  }

  public void setGetFrame(GetFrame getFrame) {
    this.getFrame = getFrame;
  }

  /**
   * Prepare encoder with default parameters
   */
  public boolean prepareAudioEncoder() {
    return prepareAudioEncoder(bitRate, sampleRate, isStereo, maxInputSize);
  }

  @Override
  public void start(boolean resetTs) {
    shouldReset = resetTs;
    Log.i(TAG, "started");
  }

  @Override
  protected void stopImp() {
    bytesRead = 0;
    Log.i(TAG, "stopped");
  }

  @Override
  public boolean reset() {
    stop(false);
    boolean result = prepareAudioEncoder(bitRate, sampleRate, isStereo, maxInputSize);
    if (!result) return false;
    restart();
    return true;
  }

  @Override
  protected Frame getInputFrame() throws InterruptedException {
    return getFrame != null ? getFrame.getInputFrame() : queue.take();
  }

  @Override
  protected long calculatePts(Frame frame, long presentTimeUs) {
    long pts;
    if (tsModeBuffer) {
      int channels = isStereo ? 2 : 1;
      pts = 1000000 * bytesRead / 2 / channels / sampleRate;
      bytesRead += frame.getSize();
    } else {
      pts = Math.max(0, frame.getTimeStamp() - presentTimeUs);
    }
    return pts;
  }

  @Override
  protected void checkBuffer(@NonNull ByteBuffer byteBuffer,
      @NonNull MediaCodec.BufferInfo bufferInfo) {
    fixTimeStamp(bufferInfo);
  }

  @Override
  protected void sendBuffer(@NonNull ByteBuffer byteBuffer,
      @NonNull MediaCodec.BufferInfo bufferInfo) {
    getAudioData.getAudioData(byteBuffer, bufferInfo);
  }

  /**
   * Set custom PCM data.
   * Use it after prepareAudioEncoder(int sampleRate, int channel).
   * Used too with microphone.
   */
  @Override
  public void inputPCMData(Frame frame) {
    if (running && !queue.offer(frame)) {
      Log.i(TAG, "frame discarded");
    }
  }

  @Override
  protected MediaCodecInfo chooseEncoder(String mime) {
    List<MediaCodecInfo> mediaCodecInfoList;
    if (codecType == CodecUtil.CodecType.HARDWARE) {
      mediaCodecInfoList = CodecUtil.getAllHardwareEncoders(CodecUtil.AAC_MIME);
    } else if (codecType == CodecUtil.CodecType.SOFTWARE) {
      mediaCodecInfoList = CodecUtil.getAllSoftwareEncoders(CodecUtil.AAC_MIME);
    } else {
      //Priority: hardware > software
      mediaCodecInfoList = CodecUtil.getAllEncoders(mime, true);
    }

    Log.i(TAG, mediaCodecInfoList.size() + " encoders found");
    if (mediaCodecInfoList.isEmpty()) return null;
    else return mediaCodecInfoList.get(0);
  }

  public void setSampleRate(int sampleRate) {
    this.sampleRate = sampleRate;
  }

  public boolean isTsModeBuffer() {
    return tsModeBuffer;
  }

  public void setTsModeBuffer(boolean tsModeBuffer) {
    if (!isRunning()) {
      this.tsModeBuffer = tsModeBuffer;
    }
  }

  @Override
  public void formatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
    getAudioData.onAudioFormat(mediaFormat);
  }
}
