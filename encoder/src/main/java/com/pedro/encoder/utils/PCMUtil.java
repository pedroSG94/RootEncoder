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

  private static final byte[] pcmBufferStereo = new byte[4096];

  /**
   * Experimental method to downgrade pcm with 3 channels or more to stereo.
   *
   * @return pcm buffer in stereo (2 channels)
   */
  public static byte[] pcmToStereo(byte[] pcm, int channels) {
    int cont = 0;
    for (int i = 0; i < pcm.length; i += channels) {
      byte channel1 = pcm[i];
      byte channel2 = pcm[i + 1];

      pcmBufferStereo[cont] = channel1;
      pcmBufferStereo[cont + 1] = channel2;
      cont += 2;
    }
    return pcmBufferStereo;
  }
}
