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

package com.pedro.encoder.input.decoder;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import com.pedro.encoder.Frame;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.utils.PCMUtil;

import java.nio.ByteBuffer;

/**
 * Created by pedro on 20/06/17.
 */
public class AudioDecoder extends BaseDecoder {

  private final AudioDecoderInterface audioDecoderInterface;
  private GetMicrophoneData getMicrophoneData;
  private int sampleRate;
  private boolean isStereo;
  private int channels = 1;
  private int size = 2048;
  private byte[] pcmBuffer = new byte[size];
  private byte[] pcmBufferMuted = new byte[11];
  private boolean muted = false;

  public AudioDecoder(GetMicrophoneData getMicrophoneData,
      AudioDecoderInterface audioDecoderInterface, DecoderInterface decoderInterface) {
    super(decoderInterface);
    TAG = "AudioDecoder";
    this.getMicrophoneData = getMicrophoneData;
    this.audioDecoderInterface = audioDecoderInterface;
  }

  @Override
  protected boolean extract(MediaExtractor audioExtractor) {
    size = 2048;
    for (int i = 0; i < audioExtractor.getTrackCount() && !mime.startsWith("audio/"); i++) {
      mediaFormat = audioExtractor.getTrackFormat(i);
      mime = mediaFormat.getString(MediaFormat.KEY_MIME);
      if (mime.startsWith("audio/")) {
        audioExtractor.selectTrack(i);
      } else {
        mediaFormat = null;
      }
    }
    if (mediaFormat != null) {
      channels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
      isStereo = channels >= 2;
      sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
      duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
      fixBuffer();
      return true;
      //audio decoder not supported
    } else {
      mime = "";
      return false;
    }
  }

  private void fixBuffer() {
    if (channels >= 2) {
      size *= channels;
    }
    pcmBuffer = new byte[size];
  }

  public boolean prepareAudio() {
    return prepare(null);
  }

  @Override
  protected boolean decodeOutput(ByteBuffer outputBuffer, long timeStamp) {
    //This buffer is PCM data
    GetMicrophoneData getMicrophoneData = this.getMicrophoneData;
    if (getMicrophoneData == null) return false;
    if (muted) {
      outputBuffer.get(pcmBufferMuted, 0,
              Math.min(outputBuffer.remaining(), pcmBufferMuted.length));
      getMicrophoneData.inputPCMData(new Frame(pcmBufferMuted, 0, pcmBufferMuted.length, timeStamp));
    } else {
      if (pcmBuffer.length < outputBuffer.remaining()) {
        pcmBuffer = new byte[outputBuffer.remaining()];
      }
      outputBuffer.get(pcmBuffer, 0, Math.min(outputBuffer.remaining(), pcmBuffer.length));
      if (channels > 2) { //downgrade to stereo
        byte[] bufferStereo = PCMUtil.pcmToStereo(pcmBuffer, channels);
        getMicrophoneData.inputPCMData(new Frame(bufferStereo, 0, bufferStereo.length, timeStamp));
      } else {
        getMicrophoneData.inputPCMData(new Frame(pcmBuffer, 0, pcmBuffer.length, timeStamp));
      }
    }
    return false;
  }

  @Override
  protected void finished() {
    audioDecoderInterface.onAudioDecoderFinished();
  }

  /**
   * This method should be called after prepare.
   * Get max output size to set max input size in encoder.
   */
  public int getOutsize() {
    if (!(mime.equals(CodecUtil.AAC_MIME) || mime.equals(CodecUtil.OPUS_MIME)
        || mime.equals(CodecUtil.VORBIS_MIME) || mime.equals(CodecUtil.G711_MIME))) {
      Log.i(TAG, "fixing input size");
      try {
        if (running) {
          return codec.getOutputBuffers()[0].remaining();
        } else {
          if (codec != null) {
            codec.start();
            int outSize = codec.getOutputBuffers()[0].remaining();
            stopDecoder();
            if (prepare(null)) return outSize;
          }
          return 0;
        }
      } catch (Exception e) {
        return 0;
      }
    } else {
      Log.i(TAG, "default input size");
      return 0;
    }
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

  public int getSampleRate() {
    return sampleRate;
  }

  public boolean isStereo() {
    return isStereo;
  }

  public int getSize() {
    return size;
  }

  public void setGetMicrophoneData(GetMicrophoneData getMicrophoneData) {
    if (running) return;
    this.getMicrophoneData = getMicrophoneData;
  }
}
