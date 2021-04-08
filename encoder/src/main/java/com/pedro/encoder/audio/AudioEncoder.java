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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by pedro on 19/01/17.
 *
 * Encode PCM audio data to ACC and return in a callback
 */

public class AudioEncoder extends BaseEncoder implements GetMicrophoneData {

  private GetAacData getAacData;
  private int bitRate = 64 * 1024;  //in kbps
  private int sampleRate = 32000; //in hz
  private int maxInputSize = 0;
  private boolean isStereo = true;
  private GetFrame getFrame;

  public AudioEncoder(GetAacData getAacData) {
    this.getAacData = getAacData;
    TAG = "AudioEncoder";
  }

  /**
   * Prepare encoder with custom parameters
   */
  public boolean prepareAudioEncoder(int bitRate, int sampleRate, boolean isStereo,
      int maxInputSize) {
    this.bitRate = bitRate;
    this.sampleRate = sampleRate;
    this.maxInputSize = maxInputSize;
    this.isStereo = isStereo;
    isBufferMode = true;
    try {
      MediaCodecInfo encoder = chooseEncoder(CodecUtil.AAC_MIME);
      if (encoder != null) {
        Log.i(TAG, "Encoder selected " + encoder.getName());
        codec = MediaCodec.createByCodecName(encoder.getName());
      } else {
        Log.e(TAG, "Valid encoder not found");
        return false;
      }

      int channelCount = (isStereo) ? 2 : 1;
      MediaFormat audioFormat =
          MediaFormat.createAudioFormat(CodecUtil.AAC_MIME, sampleRate, channelCount);
      audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
      audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
      audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
          MediaCodecInfo.CodecProfileLevel.AACObjectLC);
      codec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      running = false;
      Log.i(TAG, "prepared");
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
    Log.i(TAG, "stopped");
  }

  @Override
  public void reset() {
    stop(false);
    prepareAudioEncoder(bitRate, sampleRate, isStereo, maxInputSize);
    restart();
  }

  @Override
  protected Frame getInputFrame() throws InterruptedException {
    return getFrame != null ? getFrame.getInputFrame() : queue.take();
  }

  @Override
  protected void checkBuffer(@NonNull ByteBuffer byteBuffer,
      @NonNull MediaCodec.BufferInfo bufferInfo) {
    fixTimeStamp(bufferInfo);
  }

  @Override
  protected void sendBuffer(@NonNull ByteBuffer byteBuffer,
      @NonNull MediaCodec.BufferInfo bufferInfo) {
    getAacData.getAacData(byteBuffer, bufferInfo);
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
    if (force == CodecUtil.Force.HARDWARE) {
      mediaCodecInfoList = CodecUtil.getAllHardwareEncoders(CodecUtil.AAC_MIME);
    } else if (force == CodecUtil.Force.SOFTWARE) {
      mediaCodecInfoList = CodecUtil.getAllSoftwareEncoders(CodecUtil.AAC_MIME);
    } else {
      mediaCodecInfoList = CodecUtil.getAllEncoders(CodecUtil.AAC_MIME);
    }

    Log.i(TAG, mediaCodecInfoList.size() + " encoders found");
    for (MediaCodecInfo mci : mediaCodecInfoList) {
      String name = mci.getName().toLowerCase();
      Log.i(TAG, "Encoder " + mci.getName());
      if (name.contains("omx.google") && mediaCodecInfoList.size() > 1) {
        //skip omx.google.aac.encoder if possible
        continue;
      }
      return mci;
    }
    return null;
  }

  public void setSampleRate(int sampleRate) {
    this.sampleRate = sampleRate;
  }

  @Override
  public void formatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
    getAacData.onAudioFormat(mediaFormat);
  }
}
