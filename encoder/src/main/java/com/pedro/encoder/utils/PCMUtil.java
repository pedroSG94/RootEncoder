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

package com.pedro.encoder.utils;

/**
 * Created by pedro on 3/07/17.
 */
public class PCMUtil {

  //no tested
  //see https://stackoverflow.com/questions/15652432/how-to-mix-overlay-two-mp3-audio-file-into-one-mp3-file-not-concatenate/33255658#33255658
  public static byte[] mixPCM(byte[] pcm1, byte[] pcm2) {
    int len1 = pcm1.length;
    int len2 = pcm2.length;
    byte[] pcmL;
    byte[] pcmS;
    int lenL; // length of the longest
    int lenS; // length of the shortest
    if (len2 > len1) {
      lenL = len2;
      pcmL = pcm2;
      lenS = len1;
      pcmS = pcm1;
    } else {
      lenL = len1;
      pcmL = pcm1;
      lenS = len2;
      pcmS = pcm2;
    }
    for (int idx = 0; idx < lenL; idx++) {
      int sample;
      if (idx >= lenS) {
        sample = pcmL[idx];
      } else {
        sample = pcmL[idx] + pcmS[idx];
      }
      sample = (int) (sample * .71);
      if (sample > 127) sample = 127;
      if (sample < -128) sample = -128;
      pcmL[idx] = (byte) sample;
    }
    return pcmL;
  }

  /**
   * Resample pcm 16 bits little endian (mono or stereo) to the given sample rate.
   * Upsampling interpolates between the 2 nearest frames. Downsampling averages
   * all the frames of each interval to reduce aliasing.
   *
   * @param pcm pcm buffer, 16 bits per sample little endian
   * @param channels 1 (mono) or 2 (stereo)
   * @param inputSampleRate sample rate of the pcm buffer in Hz
   * @param outputSampleRate desired sample rate in Hz
   * @return pcm buffer resampled to outputSampleRate
   */
  public static byte[] resample(byte[] pcm, int channels, int inputSampleRate, int outputSampleRate) {
    return resample(pcm, channels, channels, inputSampleRate, outputSampleRate);
  }

  /**
   * Resample pcm 16 bits little endian to the given sample rate and convert channels
   * in the same pass. Mono to stereo duplicates the channel, stereo to mono averages
   * both channels. Upsampling interpolates between the 2 nearest frames. Downsampling
   * averages all the frames of each interval to reduce aliasing.
   *
   * @param pcm pcm buffer, 16 bits per sample little endian
   * @param inputChannels 1 (mono) or 2 (stereo)
   * @param outputChannels 1 (mono) or 2 (stereo)
   * @param inputSampleRate sample rate of the pcm buffer in Hz
   * @param outputSampleRate desired sample rate in Hz
   * @return pcm buffer resampled to outputSampleRate with outputChannels channels
   */
  public static byte[] resample(byte[] pcm, int inputChannels, int outputChannels, int inputSampleRate, int outputSampleRate) {
    if (inputSampleRate == outputSampleRate && inputChannels == outputChannels) return pcm;
    int inputFrameSize = inputChannels * 2;
    int outputFrameSize = outputChannels * 2;
    int inputFrames = pcm.length / inputFrameSize;
    if (inputFrames == 0) return new byte[0];
    int outputFrames = (int) ((long) inputFrames * outputSampleRate / inputSampleRate);
    byte[] resampled = new byte[outputFrames * outputFrameSize];
    double step = (double) inputSampleRate / outputSampleRate;
    boolean downsampling = step > 1;
    for (int i = 0; i < outputFrames; i++) {
      double position = i * step;
      int frame = (int) position;
      double fraction = position - frame;
      int nextFrame = Math.min(frame + 1, inputFrames - 1);
      int endFrame = Math.min((int) (position + step), inputFrames);
      for (int channel = 0; channel < outputChannels; channel++) {
        short value;
        if (downsampling) { //average all frames of the interval to reduce aliasing
          long sum = 0;
          for (int f = frame; f < endFrame; f++) {
            sum += readSample(pcm, f, channel, inputChannels, outputChannels, inputFrameSize);
          }
          value = (short) Math.round((double) sum / (endFrame - frame));
        } else { //interpolate between the 2 nearest frames
          int sample = readSample(pcm, frame, channel, inputChannels, outputChannels, inputFrameSize);
          int nextSample = readSample(pcm, nextFrame, channel, inputChannels, outputChannels, inputFrameSize);
          value = (short) Math.round(sample + (nextSample - sample) * fraction);
        }
        int outIndex = i * outputFrameSize + channel * 2;
        resampled[outIndex] = (byte) (value & 0xFF);
        resampled[outIndex + 1] = (byte) ((value >> 8) & 0xFF);
      }
    }
    return resampled;
  }

  private static int readSample(byte[] pcm, int frame, int outputChannel, int inputChannels, int outputChannels, int inputFrameSize) {
    int base = frame * inputFrameSize;
    if (outputChannels < inputChannels) { //downmix, average all input channels
      int sum = 0;
      for (int channel = 0; channel < inputChannels; channel++) {
        int index = base + channel * 2;
        sum += (short) ((pcm[index] & 0xFF) | (pcm[index + 1] << 8));
      }
      return sum / inputChannels;
    } else { //same channels or mono to stereo duplicating the channel
      int index = base + Math.min(outputChannel, inputChannels - 1) * 2;
      return (short) ((pcm[index] & 0xFF) | (pcm[index + 1] << 8));
    }
  }

  /**
   * Experimental method to downgrade pcm with 3 channels or more to stereo.
   * Keeps the first two channels (16 bits little endian per sample) and drops the rest.
   *
   * @return pcm buffer in stereo (2 channels)
   */
  public static byte[] pcmToStereo(byte[] pcm, int channels) {
    int frameSize = channels * 2;
    int frames = pcm.length / frameSize;
    byte[] pcmBufferStereo = new byte[frames * 4];
    int cont = 0;
    for (int i = 0; i + frameSize <= pcm.length; i += frameSize) {
      pcmBufferStereo[cont] = pcm[i];
      pcmBufferStereo[cont + 1] = pcm[i + 1];
      pcmBufferStereo[cont + 2] = pcm[i + 2];
      pcmBufferStereo[cont + 3] = pcm[i + 3];
      cont += 4;
    }
    return pcmBufferStereo;
  }
}
